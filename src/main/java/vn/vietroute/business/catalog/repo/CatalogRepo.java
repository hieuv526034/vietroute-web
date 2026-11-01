package vn.vietroute.business.catalog.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.request.CatalogRequest;
import vn.vietroute.business.catalog.dto.search.CatalogSearch;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Repository mẫu cho module nghiệp vụ; mọi giá trị SQL đều được bind bằng PreparedStatement. */
@Repository
public class CatalogRepo extends JpaRepositorySupport<CatalogItem, Long> {

    /** Count và trang theo loại cố định; có thể xem/bật lại mục ngừng dùng, không làm đổi lookup public. */
    public PageResponse<CatalogItem> search(CatalogType type, CatalogSearch page) {
        return read(connection -> search(connection, type, page));
    }

    /** Biến thể dùng connection transaction caller; cùng SQL/bind với luồng đọc độc lập. */
    public PageResponse<CatalogItem> search(Connection connection, CatalogType type, CatalogSearch page)
        throws SQLException {
        String filter = """
        WHERE type = ?
            AND (?::boolean IS NULL OR active = ?)
            AND lower(code || ' ' || name_vi || ' ' || name_en) LIKE ? ESCAPE '\\'
        """;
        long total;
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT count(*)
                FROM catalog_items
                %s
                """.formatted(filter)
            )
        ) {
            bindSearch(query, type, page);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                total = row.getLong(1);
            }
        }
        ArrayList<CatalogItem> content = new ArrayList<CatalogItem>();
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                SELECT *
                FROM catalog_items
                %s
                ORDER BY sort_order, id
                LIMIT ? OFFSET ?
                """.formatted(filter)
            )
        ) {
            bindSearch(query, type, page);
            query.setInt(5, page.getSize());
            query.setLong(6, page.offset());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) content.add(map(rows));
            }
        }
        return PageResponse.of(content, page, total);
    }

    private void bindSearch(PreparedStatement query, CatalogType type, CatalogSearch page) throws SQLException {
        query.setString(1, type.name());
        query.setObject(2, page.getActive(), Types.BOOLEAN);
        query.setObject(3, page.getActive(), Types.BOOLEAN);
        String term = page.getQuery() == null ? "" : page.getQuery().strip().toLowerCase(Locale.ROOT);
        query.setString(4, "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%");
    }

    /** ID + type + version trong UPDATE, giữ bất biến mã và cho phép khôi phục trạng thái. */
    public CatalogItem updateManaged(
        Connection connection,
        long id,
        CatalogType type,
        CatalogRequest body,
        long actor
    ) {
        CatalogItem value = locked(id, body.getVersion());
        requireIdentity(value, body);
        BusinessRules.require(value.getType() == type, ErrorCode.STALE_VERSION);
        value.setNameVi(body.getNameVi().strip());
        value.setNameEn(body.getNameEn().strip());
        value.setSortOrder(body.getSortOrder());
        value.setActive(body.isActive());
        return changed(value, actor);
    }

    public CatalogRepo(EntityManager manager, JdbcTransactions transactions) {
        super(CatalogItem.class, manager, transactions);
    }

    /** Quản trị được đọc cả bản đã xóa mềm; API công khai vẫn chỉ đọc active=true. */
    public CatalogItem find(long id) {
        return read(connection -> find(connection, id));
    }

    /** Đọc JPA trên transaction caller; service typed kiểm loại trước khi cập nhật hoặc trả DTO. */
    public CatalogItem find(Connection connection, long id) throws SQLException {
        return BusinessRules.requireFound(entityManager.find(CatalogItem.class, id));
    }

    /** Khóa JPA và @Version chốt cạnh tranh khi flush, không chỉ so version trong Java. */
    public CatalogItem update(Connection connection, long id, CatalogRequest request, long actor) {
        CatalogItem value = locked(id, request.getVersion());
        requireIdentity(value, request);
        BusinessRules.require(value.isActive(), ErrorCode.STALE_VERSION);
        value.setNameVi(request.getNameVi().strip());
        value.setNameEn(request.getNameEn().strip());
        value.setSortOrder(request.getSortOrder());
        return changed(value, actor);
    }

    /** Xóa mềm tránh phá FK của nghiệp vụ tương lai; quyền DELETE tách biệt với quyền UPDATE/WRITE. */
    public CatalogItem delete(Connection connection, long id, long version, long actor) {
        CatalogItem value = locked(id, version);
        BusinessRules.require(value.isActive(), ErrorCode.STALE_VERSION);
        value.setActive(false);
        return changed(value, actor);
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
            ArrayList<CatalogItem> content = new ArrayList<CatalogItem>();
            // ORDER BY cố định: không nối trực tiếp sort từ request vào SQL.
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT *
                    FROM catalog_items
                    WHERE type = ? AND active = true
                    ORDER BY sort_order, id
                    LIMIT ? OFFSET ?
                    """
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
    public CatalogItem insert(Connection connection, CatalogRequest request, String code, long actor) {
        CatalogItem value = CatalogItem.builder()
            .type(request.getType())
            .code(code)
            .nameVi(request.getNameVi().strip())
            .nameEn(request.getNameEn().strip())
            .sortOrder(request.getSortOrder())
            .active(true)
            .build();
        Instant now = Instant.now();
        value.setCreatedAt(now);
        value.setUpdatedAt(now);
        value.setCreatedBy(actor);
        value.setUpdatedBy(actor);
        return saveAndFlush(value);
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

    /** Seed danh mục nền bằng UNIQUE(type, code); không thêm dữ liệu chuyến/đơn giả. */
    public void seedCatalogs(Connection connection) throws SQLException {
        String[][] values = {
            { "VEHICLE_TYPE", "SEAT", "Ghế ngồi", "Seated coach" },
            { "VEHICLE_TYPE", "SLEEPER", "Giường nằm", "Sleeper coach" },
            { "VEHICLE_TYPE", "LIMOUSINE", "Limousine", "Limousine" },
            { "BOOKING_SOURCE", "WEB", "Đặt trên website", "Website" },
            { "BOOKING_SOURCE", "PHONE", "Đặt qua điện thoại", "Phone" },
        };
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO catalog_items(type, code, name_vi, name_en, sort_order)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (type, code) DO NOTHING
                """
            )
        ) {
            for (int i = 0; i < values.length; i++) {
                for (int j = 0; j < values[i].length; j++) {
                    insert.setString(j + 1, values[i][j]);
                }
                insert.setInt(5, i);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Khóa + @Version bảo vệ cả JPA và caller JDBC, không ghi đè version cũ. */
    private CatalogItem locked(long id, long version) {
        CatalogItem value = BusinessRules.requireFound(
            entityManager.find(CatalogItem.class, id, LockModeType.PESSIMISTIC_WRITE)
        );
        BusinessRules.requireVersion(version, value.getVersion());
        return value;
    }

    /** DTO dùng chung cho tạo/sửa không cho đổi định danh code/type đã được DB chốt. */
    private static void requireIdentity(CatalogItem value, CatalogRequest body) {
        BusinessRules.require(
            (body.getCode() == null || value.getCode().equals(body.getCode())) &&
                (body.getType() == null || value.getType() == body.getType()),
            ErrorCode.INVALID_REQUEST
        );
    }

    private CatalogItem changed(CatalogItem value, long actor) {
        value.setUpdatedAt(Instant.now());
        value.setUpdatedBy(actor);
        return saveAndFlush(value);
    }
}
