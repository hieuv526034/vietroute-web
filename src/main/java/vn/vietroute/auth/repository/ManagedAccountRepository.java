package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.request.ManagedAccountSearchRequest;
import vn.vietroute.auth.dto.response.ManagedAccountResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** DAO quản trị: projection tường minh và paging ổn định, không đọc hash cho danh sách. */
@Repository
public class ManagedAccountRepository extends BaseRepository {

    public ManagedAccountRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** COUNT và trang dữ liệu cùng bộ lọc bind; không dùng N+1 để lấy role. */
    public PageResponse<ManagedAccountResponse> page(ManagedAccountSearchRequest page) {
        return read(connection -> {
            String where = " FROM auth_users u WHERE u.id<>? AND lower(u.username) LIKE ?";
            if (page.getRole() != null && !page.getRole().isBlank()) where +=
                " AND EXISTS(SELECT 1 FROM auth_user_roles ur WHERE ur.user_id=u.id AND ur.role_code=?)";
            if (page.getActive() != null) where += " AND u.active=?";
            long total;
            try (var count = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(count, page);
                try (var row = count.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var content = new ArrayList<ManagedAccountResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT u.id,u.username,u.display_name,u.active,u.activation_pending,u.must_change_password,(u.email_verified_at IS NULL) AS email_verification_required,u.locked_until,u.version,u.created_at,u.updated_at,COALESCE((SELECT string_agg(role_code,',' ORDER BY role_code) FROM auth_user_roles WHERE user_id=u.id),'') AS roles" +
                        where +
                        " ORDER BY u.username,u.id LIMIT ? OFFSET ?"
                )
            ) {
                int index = bind(query, page);
                query.setInt(index++, page.getSize());
                query.setLong(index, page.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) {
                        String roles = rows.getString("roles");
                        Set<Role> assigned = roles.isEmpty()
                            ? Set.of()
                            : Arrays.stream(roles.split(","))
                                  .map(Role::valueOf)
                                  .collect(Collectors.toUnmodifiableSet());
                        content.add(
                            new ManagedAccountResponse(
                                rows.getLong("id"),
                                rows.getString("username"),
                                rows.getString("display_name"),
                                rows.getBoolean("active"),
                                rows.getBoolean("activation_pending"),
                                rows.getBoolean("must_change_password"),
                                rows.getBoolean("email_verification_required"),
                                instant(rows, "locked_until"),
                                assigned,
                                rows.getLong("version"),
                                instant(rows, "created_at"),
                                instant(rows, "updated_at")
                            )
                        );
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    private int bind(PreparedStatement query, ManagedAccountSearchRequest page) throws SQLException {
        query.setLong(1, SystemActor.ID);
        query.setString(2, page.getUsername().toLowerCase(Locale.ROOT) + "%");
        int index = 3;
        if (page.getRole() != null && !page.getRole().isBlank()) query.setString(index++, page.getRole());
        if (page.getActive() != null) query.setBoolean(index++, page.getActive());
        return index;
    }

    /** Mã đã gán vẫn hiện khi role đang tắt; không dùng tập này để xây authorities. */
    public Set<Role> assignedRoles(long id) {
        return read(connection -> assignedRoles(connection, id));
    }

    public Set<Role> assignedRoles(Connection connection, long id) throws SQLException {
        var result = new LinkedHashSet<Role>();
        try (
            var query = connection.prepareStatement(
                "SELECT role_code FROM auth_user_roles WHERE user_id=? ORDER BY role_code"
            )
        ) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(Role.valueOf(rows.getString(1)));
            }
        }
        return Set.copyOf(result);
    }

    /** ON CONFLICT tránh làm hỏng transaction; chỉ trả lỗi trùng cho admin đã được xác thực. */
    public long create(Connection connection, String username, String displayName, String hash, long actor)
        throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_users(username,display_name,password_hash,active,must_change_password,created_by,updated_by) VALUES (?,?,?,true,true,?,?) ON CONFLICT DO NOTHING RETURNING id"
            )
        ) {
            query.setString(1, username);
            query.setString(2, displayName);
            query.setString(3, hash);
            query.setLong(4, actor);
            query.setLong(5, actor);
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.CONFLICT);
                return row.getLong(1);
            }
        }
    }

    /** Trạng thái và tokenVersion đổi nguyên tử; mở khóa không tự kích hoạt email chưa xác minh. */
    public void state(Connection connection, long id, boolean active, long version, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET active=?,token_version=token_version+1,version=version+1,failed_login_attempts=0,locked_until=NULL,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?"
            )
        ) {
            query.setBoolean(1, active);
            query.setLong(2, actor);
            query.setLong(3, id);
            query.setLong(4, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws SQLException {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }
}
