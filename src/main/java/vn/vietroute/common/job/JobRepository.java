package vn.vietroute.common.job;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Hàng đợi PostgreSQL: transaction enqueue có thể dùng chung với thay đổi nghiệp vụ (transactional outbox). */
@Repository
public class JobRepository extends BaseRepository {

    public JobRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Dedup key duy nhất không cho HTTP retry tạo mail/job thứ hai; không ghi đè payload của job đã có. */
    public UUID enqueue(Connection connection, String type, String dedup, String payload, Long actor)
        throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO vietroute.system_jobs(id,type,dedup_key,payload,created_by) VALUES (?,?,?,?::jsonb,?) ON CONFLICT (dedup_key) DO NOTHING RETURNING id"
            )
        ) {
            UUID id = UUID.randomUUID();
            insert.setObject(1, id);
            insert.setString(2, type);
            insert.setString(3, dedup);
            insert.setString(4, payload);
            insert.setObject(5, actor);
            try (ResultSet row = insert.executeQuery()) {
                if (row.next()) {
                    return row.getObject(1, UUID.class);
                }
            }
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id FROM vietroute.system_jobs WHERE dedup_key=?"
            )
        ) {
            query.setString(1, dedup);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new BusinessException(ErrorCode.CONFLICT);
                }
                return row.getObject(1, UUID.class);
            }
        }
    }

    /** SKIP LOCKED chia việc giữa mọi instance; commit nhận lease trước khi chạy handler, không giữ row lock khi gửi mail. */
    public Job claim(Connection connection, int leaseSeconds) throws SQLException {
        try (
            PreparedStatement expire = connection.prepareStatement(
                "UPDATE vietroute.system_jobs SET status='DEAD', lease_token=NULL, locked_until=NULL, last_error_code='LEASE_EXHAUSTED', finished_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE status='RUNNING' AND locked_until<CURRENT_TIMESTAMP AND attempts>=max_attempts"
            )
        ) {
            expire.executeUpdate();
        }
        try (
            PreparedStatement claim = connection.prepareStatement(
                """
                WITH next AS (
                    SELECT id FROM vietroute.system_jobs
                    WHERE attempts<max_attempts AND ((status='QUEUED' AND available_at<=CURRENT_TIMESTAMP)
                        OR (status='RUNNING' AND locked_until<CURRENT_TIMESTAMP))
                    ORDER BY available_at, created_at, id FOR UPDATE SKIP LOCKED LIMIT 1
                )
                UPDATE vietroute.system_jobs j SET status='RUNNING', attempts=attempts+1, lease_token=?,
                    locked_until=CURRENT_TIMESTAMP + (? * INTERVAL '1 second'), updated_at=CURRENT_TIMESTAMP, version=version+1
                FROM next WHERE j.id=next.id
                RETURNING j.id,j.type,j.payload::text,j.attempts,j.max_attempts,j.lease_token,j.locked_until
                """
            )
        ) {
            claim.setObject(1, UUID.randomUUID());
            claim.setInt(2, leaseSeconds);
            try (ResultSet row = claim.executeQuery()) {
                return row.next()
                    ? new Job(
                          row.getObject(1, UUID.class),
                          row.getString(2),
                          row.getString(3),
                          row.getInt(4),
                          row.getInt(5),
                          row.getObject(6, UUID.class),
                          row.getTimestamp(7).toInstant()
                      )
                    : null;
            }
        }
    }

    /** Chỉ worker đang giữ lease còn hạn mới được ACK/NACK; worker cũ không thể ghi đè lần chạy mới. */
    public boolean finish(Connection connection, Job job, String errorCode, boolean retryable, int delaySeconds)
        throws SQLException {
        boolean retry = errorCode != null && retryable && job.attempts() < job.maxAttempts();
        String status = errorCode == null ? "SUCCEEDED" : retry ? "QUEUED" : "DEAD";
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE vietroute.system_jobs SET status=?,last_error_code=?,available_at=CURRENT_TIMESTAMP+(? * INTERVAL '1 second'),
                    lease_token=NULL,locked_until=NULL,finished_at=CASE WHEN ?='QUEUED' THEN NULL ELSE CURRENT_TIMESTAMP END,
                    payload=CASE WHEN ?='SUCCEEDED' THEN '{}'::jsonb ELSE payload END,
                    updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=? AND status='RUNNING' AND lease_token=? AND locked_until>CURRENT_TIMESTAMP
                """
            )
        ) {
            update.setString(1, status);
            update.setString(2, errorCode);
            update.setInt(3, delaySeconds);
            update.setString(4, status);
            update.setString(5, status);
            update.setObject(6, job.id());
            update.setObject(7, job.leaseToken());
            return update.executeUpdate() == 1;
        }
    }

    public JobResponse find(UUID id) {
        return read(connection -> {
            try (
                PreparedStatement query = connection.prepareStatement("SELECT * FROM vietroute.system_jobs WHERE id=?")
            ) {
                query.setObject(1, id);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) {
                        throw new BusinessException(ErrorCode.NOT_FOUND);
                    }
                    return response(row);
                }
            }
        });
    }

    public PageResponse<JobResponse> page(PageRequest page) {
        return read(connection -> {
            long total;
            try (
                PreparedStatement count = connection.prepareStatement("SELECT count(*) FROM vietroute.system_jobs");
                ResultSet row = count.executeQuery()
            ) {
                row.next();
                total = row.getLong(1);
            }
            List<JobResponse> content = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM vietroute.system_jobs ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?"
                )
            ) {
                query.setInt(1, page.getSize());
                query.setLong(2, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        content.add(response(rows));
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    public void retry(Connection connection, UUID id, long version) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE vietroute.system_jobs SET status='QUEUED',attempts=0,available_at=CURRENT_TIMESTAMP,
                    finished_at=NULL,last_error_code=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=? AND version=? AND status='DEAD'
                """
            )
        ) {
            update.setObject(1, id);
            update.setLong(2, version);
            if (update.executeUpdate() != 1) {
                throw new BusinessException(ErrorCode.JOB_NOT_RETRYABLE);
            }
        }
    }

    /** Một instance giữ khóa dọn dẹp, xóa theo lô nhỏ để không khóa bảng hoặc chiếm pool lâu. */
    public void cleanup(Connection connection, int retentionDays) throws SQLException {
        try (
            PreparedStatement lock = connection.prepareStatement("SELECT pg_try_advisory_xact_lock(781205612)");
            ResultSet row = lock.executeQuery()
        ) {
            row.next();
            if (!row.getBoolean(1)) {
                return;
            }
        }
        try (
            PreparedStatement delete = connection.prepareStatement(
                """
                DELETE FROM vietroute.system_jobs WHERE id IN (
                    SELECT id FROM vietroute.system_jobs WHERE status='SUCCEEDED'
                        AND finished_at<CURRENT_TIMESTAMP-(? * INTERVAL '1 day') ORDER BY finished_at LIMIT 500
                )
                """
            )
        ) {
            delete.setInt(1, retentionDays);
            delete.executeUpdate();
        }
    }

    private static JobResponse response(ResultSet row) throws SQLException {
        return new JobResponse(
            row.getObject("id", UUID.class),
            row.getString("type"),
            row.getString("status"),
            row.getInt("attempts"),
            row.getInt("max_attempts"),
            instant(row, "available_at"),
            instant(row, "locked_until"),
            row.getString("last_error_code"),
            instant(row, "finished_at"),
            instant(row, "created_at"),
            instant(row, "updated_at"),
            row.getObject("created_by", Long.class),
            row.getLong("version")
        );
    }

    private static Instant instant(ResultSet row, String column) throws SQLException {
        Timestamp value = row.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
