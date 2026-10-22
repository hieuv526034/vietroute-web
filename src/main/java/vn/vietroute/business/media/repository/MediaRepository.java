package vn.vietroute.business.media.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.media.entity.MediaAsset;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.media.MediaStorage.StoredImage;

/** Mọi ghi dữ liệu dùng connection của transaction; không gọi Cloudinary trong repository. */
@Repository
public class MediaRepository extends BaseRepository {

    public MediaRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    public MediaAsset find(long id) {
        return read(connection -> find(connection, id));
    }

    public MediaAsset find(Connection connection, long id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM media_assets WHERE id=?")) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                return map(row);
            }
        }
    }

    /** Scope owner được bind tại SQL cho cả COUNT lẫn SELECT; chỉ ADMIN được xem toàn bộ thư viện. */
    public PageResponse<MediaAsset> page(PageRequest page, long actor, boolean admin) {
        return read(connection -> {
            String where = " WHERE status<>'DELETED' AND (? OR created_by=?)";
            long total;
            try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM media_assets" + where)) {
                count.setBoolean(1, admin);
                count.setLong(2, actor);
                try (ResultSet row = count.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var content = new ArrayList<MediaAsset>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM media_assets" + where + " ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?"
                )
            ) {
                query.setBoolean(1, admin);
                query.setLong(2, actor);
                query.setInt(3, page.getSize());
                query.setLong(4, (long) page.getPage() * page.getSize());
                try (ResultSet row = query.executeQuery()) {
                    while (row.next()) {
                        content.add(map(row));
                    }
                }
            }
            int pages = (int) Math.ceil((double) total / page.getSize());
            return new PageResponse<>(
                content,
                page.getPage(),
                page.getSize(),
                total,
                pages,
                page.getPage() + 1 < pages,
                page.getPage() > 0
            );
        });
    }

    /** Giữ chỗ trước khi gọi mạng để mọi upload đều có dấu vết phục hồi và owner từ JWT. */
    public MediaAsset reserve(Connection connection, String publicId, long actor) throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO media_assets(public_id,created_by,updated_by) VALUES (?,?,?) RETURNING *"
            )
        ) {
            insert.setString(1, publicId);
            insert.setLong(2, actor);
            insert.setLong(3, actor);
            try (ResultSet row = insert.executeQuery()) {
                row.next();
                return map(row);
            }
        }
    }

    /** Chỉ chuyển PENDING sang READY một lần; job dọn đã nhận xử lý thì không cho hồi sinh bản ghi. */
    public MediaAsset ready(Connection connection, long id, StoredImage image) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE media_assets SET status='READY',url=?,thumbnail_url=?,format=?,bytes=?,width=?,height=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND status='PENDING' RETURNING *"
            )
        ) {
            update.setString(1, image.url());
            update.setString(2, image.thumbnailUrl());
            update.setString(3, image.format());
            update.setLong(4, image.bytes());
            update.setInt(5, image.width());
            update.setInt(6, image.height());
            update.setLong(7, id);
            try (ResultSet row = update.executeQuery()) {
                if (row.next()) {
                    return map(row);
                }
            }
            throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    public MediaAsset deleting(Connection connection, long id, long version, long actor) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE media_assets SET status='DELETING',updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=? AND status='READY' RETURNING *"
            )
        ) {
            update.setLong(1, actor);
            update.setLong(2, id);
            update.setLong(3, version);
            try (ResultSet row = update.executeQuery()) {
                if (row.next()) {
                    return map(row);
                }
            }
            throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    /** Nhận việc dọn bằng UPDATE nguyên tử để không đua với bước hoàn tất upload. */
    public MediaAsset claimCleanup(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE media_assets SET status='DELETING',updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND (status='DELETING' OR (status='PENDING' AND created_at<=CURRENT_TIMESTAMP-INTERVAL '1 hour')) RETURNING *"
            )
        ) {
            update.setLong(1, id);
            try (ResultSet row = update.executeQuery()) {
                return row.next() ? map(row) : null;
            }
        }
    }

    /** Cleanup chỉ xóa ảnh PENDING quá hạn hoặc DELETING; READY không bị xóa theo tuổi. */
    public void deleted(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                "UPDATE media_assets SET status='DELETED',url=NULL,thumbnail_url=NULL,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND status IN ('PENDING','DELETING')"
            )
        ) {
            update.setLong(1, id);
            update.executeUpdate();
        }
    }

    private static MediaAsset map(ResultSet row) throws SQLException {
        return MediaAsset.builder()
            .id(row.getLong("id"))
            .publicId(row.getString("public_id"))
            .status(row.getString("status"))
            .url(row.getString("url"))
            .thumbnailUrl(row.getString("thumbnail_url"))
            .format(row.getString("format"))
            .bytes(row.getObject("bytes", Long.class))
            .width(row.getObject("width", Integer.class))
            .height(row.getObject("height", Integer.class))
            .createdAt(row.getTimestamp("created_at").toInstant())
            .updatedAt(row.getTimestamp("updated_at").toInstant())
            .createdBy(row.getLong("created_by"))
            .updatedBy(row.getLong("updated_by"))
            .version(row.getLong("version"))
            .build();
    }
}
