package vn.vietroute.business.catalog.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogUpdateRequest;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Repository mẫu cho module nghiệp vụ; mọi giá trị SQL đều được bind bằng PreparedStatement. */
@Repository
public class CatalogRepository extends BaseRepository {

    public CatalogRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Quản trị được đọc cả bản đã xóa mềm; API công khai vẫn chỉ đọc active=true. */
    public CatalogItem find(long id) {
        return read(connection -> find(connection, id));
    }

    private CatalogItem find(Connection connection, long id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT * FROM catalog_items WHERE id=?")) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) {
                    throw new BusinessException(ErrorCode.NOT_FOUND);
                }
                return map(row);
            }
        }
    }

    /** Compare-and-set trên version ở SQL là nơi chốt cạnh tranh, không chỉ so version trong Java. */
    public CatalogItem update(Connection connection, long id, CatalogUpdateRequest request, long actor)
        throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE catalog_items SET name_vi=?,name_en=?,sort_order=?,updated_by=?,
                    updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND version=? AND active=true RETURNING *
                """
            )
        ) {
            update.setString(1, request.getNameVi().trim());
            update.setString(2, request.getNameEn().trim());
            update.setInt(3, request.getSortOrder());
            update.setLong(4, actor);
            update.setLong(5, id);
            update.setLong(6, request.getVersion());
            try (ResultSet row = update.executeQuery()) {
                if (row.next()) {
                    return map(row);
                }
            }
        }
        find(connection, id); // Phân biệt không tồn tại (404) với version cũ/xóa mềm (409).
        throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    /** Xóa mềm tránh phá FK của nghiệp vụ tương lai; quyền DELETE tách biệt với quyền UPDATE/WRITE. */
    public CatalogItem delete(Connection connection, long id, long version, long actor) throws SQLException {
        try (
            PreparedStatement update = connection.prepareStatement(
                """
                UPDATE catalog_items SET active=false,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1
                WHERE id=? AND version=? AND active=true RETURNING *
                """
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
        }
        find(connection, id);
        throw new BusinessException(ErrorCode.STALE_VERSION);
    }

    /** Đếm tổng và lấy trang theo ORDER BY cố định, bind LIMIT/OFFSET, rồi map entity trước khi đóng connection. */
    public PageResponse<CatalogItem> page(CatalogType type, PageRequest page) {
        return read(connection -> {
            long total;
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT COUNT(*) FROM catalog_items WHERE type=? AND active=true"
                )
            ) {
                query.setString(1, type.name());
                try (ResultSet count = query.executeQuery()) {
                    count.next();
                    total = count.getLong(1);
                }
            }
            var content = new ArrayList<CatalogItem>();
            // ORDER BY cố định: không nối trực tiếp sort từ request vào SQL.
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT * FROM catalog_items WHERE type=? AND active=true ORDER BY sort_order,id LIMIT ? OFFSET ?"
                )
            ) {
                query.setString(1, type.name());
                query.setInt(2, page.getSize());
                query.setLong(3, page.offset());
                try (ResultSet result = query.executeQuery()) {
                    while (result.next()) {
                        content.add(map(result));
                    }
                }
            }
            return PageResponse.of(content, page, total);
        });
    }

    /** Insert nhận actor từ server và trả lại audit do SQL tạo; không tự commit connection của service. */
    public CatalogItem insert(Connection connection, CatalogCreateRequest request, String code, long actor)
        throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO catalog_items(type,code,name_vi,name_en,sort_order,created_by,updated_by) VALUES (?,?,?,?,?,?,?) RETURNING *"
            )
        ) {
            insert.setString(1, request.getType().name());
            insert.setString(2, code);
            insert.setString(3, request.getNameVi().trim());
            insert.setString(4, request.getNameEn().trim());
            insert.setInt(5, request.getSortOrder());
            insert.setLong(6, actor);
            insert.setLong(7, actor);
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                return map(result);
            }
        }
    }

    private CatalogItem map(ResultSet result) throws SQLException {
        return CatalogItem.builder()
            .id(result.getLong("id"))
            .type(CatalogType.valueOf(result.getString("type")))
            .code(result.getString("code"))
            .nameVi(result.getString("name_vi"))
            .nameEn(result.getString("name_en"))
            .sortOrder(result.getInt("sort_order"))
            .active(result.getBoolean("active"))
            .version(result.getLong("version"))
            .createdAt(result.getTimestamp("created_at").toInstant())
            .updatedAt(result.getTimestamp("updated_at").toInstant())
            .createdBy(result.getObject("created_by", Long.class))
            .updatedBy(result.getObject("updated_by", Long.class))
            .build();
    }
}
