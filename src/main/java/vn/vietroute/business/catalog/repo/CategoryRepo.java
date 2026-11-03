package vn.vietroute.business.catalog.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/**
 * CRUD JPA và query lookup/cha theo tập; JDBC và JPA tham gia cùng transaction.
 */
@Repository
public class CategoryRepo extends JpaRepositorySupport<Category, Long> {

    private final ObjectMapper json;

    private static final String COLUMNS = """
    c.*, p.name AS parent_name, p.props->>'nameEn' AS parent_name_en,
    COALESCE(p.active,false) AS parent_active
    """;

    private static final String FROM = """
    FROM category c
    LEFT JOIN category g ON g.group_code=c.ref_type
    LEFT JOIN category p ON p.id=c.parent_id
    """;

    public CategoryRepo(EntityManager manager, JdbcTransactions transactions, ObjectMapper json) {
        super(Category.class, manager, transactions);
        this.json = json;
    }

    /**
     * Danh sách có filter cố định, count/page cùng predicate và thứ tự; không query từng dòng.
     */
    public PageResponse<Category> search(CategorySearch page, String refType, String type, boolean publicOnly) {
        return read(connection -> search(connection, page, refType, type, publicOnly));
    }

    public PageResponse<Category> search(
        Connection connection,
        CategorySearch page,
        String refType,
        String type,
        boolean publicOnly
    ) throws SQLException {
        String filter = """
        WHERE c.type=? AND (?::text IS NULL OR c.ref_type=?)
            AND ((?::bigint IS NULL AND c.parent_id IS NULL) OR c.parent_id=?)
            AND (?::boolean IS NULL OR c.active=?)
                AND (NOT ? OR (c.active AND g.active AND NOT EXISTS (
                    WITH RECURSIVE ancestors AS (
                        SELECT a.id,a.parent_id,a.active,a.ref_type FROM category a WHERE a.id=c.parent_id
                        UNION ALL
                        SELECT a.id,a.parent_id,a.active,a.ref_type FROM category a
                        JOIN ancestors previous ON a.id=previous.parent_id
                    )
                    SELECT 1 FROM ancestors a JOIN category ag ON ag.group_code=a.ref_type
                    WHERE NOT a.active OR NOT ag.active
                )))
            AND lower(c.code || ' ' || c.name || ' ' || COALESCE(c.props->>'nameEn','') ||
                ' ' || COALESCE(c.props->>'address','')) LIKE lower(?) ESCAPE '!'
        """;
        long count;
        try (PreparedStatement query = connection.prepareStatement("SELECT count(*) " + FROM + filter)) {
            bind(query, page, refType, type, publicOnly);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                count = row.getLong(1);
            }
        }
        List<Category> content = new ArrayList<>();
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT " + COLUMNS + FROM + filter + " ORDER BY c.sort_order,c.id LIMIT ? OFFSET ?"
            )
        ) {
            bind(query, page, refType, type, publicOnly);
            query.setInt(10, page.getSize());
            query.setLong(11, page.offset());
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) content.add(map(rows));
            }
        }
        return PageResponse.of(content, page, count);
    }

    private void bind(PreparedStatement query, CategorySearch page, String refType, String type, boolean publicOnly)
        throws SQLException {
        query.setString(1, type);
        query.setString(2, refType);
        query.setString(3, refType);
        query.setObject(4, page.parent());
        query.setObject(5, page.parent());
        Boolean active = publicOnly ? Boolean.TRUE : page.getActive();
        query.setObject(6, active);
        query.setObject(7, active);
        query.setBoolean(8, publicOnly);
        String value = page.getQuery() == null ? "" : page.getQuery();
        query.setString(9, "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    public Category item(long id) {
        return read(connection -> item(connection, id));
    }

    /**
     * Một SELECT đã JOIN cha; entity projection chỉ phục vụ DTO, không nhận quyền từ client.
     */
    public Category item(Connection connection, long id) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement("SELECT " + COLUMNS + FROM + " WHERE c.id=?")) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                return BusinessRules.requireFound(rows.next() ? map(rows) : null);
            }
        }
    }

    /**
     * Khóa GROUP/cha trước mutation VALUE để không lọt hợp đồng/trạng thái khi cập nhật đồng thời.
     */
    public Category group(String code) {
        return BusinessRules.requireFound(
            entityManager
                .createQuery("select c from Category c where c.type='GROUP' and c.code=:code", Category.class)
                .setParameter("code", code)
                .setLockMode(LockModeType.PESSIMISTIC_READ)
                .getResultStream()
                .findFirst()
                .orElse(null)
        );
    }

    public Category locked(long id) {
        return BusinessRules.requireFound(entityManager.find(Category.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public Category parent(long id) {
        return BusinessRules.requireFound(entityManager.find(Category.class, id, LockModeType.PESSIMISTIC_READ));
    }

    /**
     * Dùng cùng hợp đồng DB với trigger; dữ liệu bind JSON, không sinh SQL từ props.
     */
    public void requireProps(Map<String, Object> props, Map<String, Object> group) {
        read(connection -> {
            try (
                PreparedStatement query = connection.prepareStatement("SELECT category_props_valid(?::jsonb,?::jsonb)")
            ) {
                query.setString(1, json.writeValueAsString(props));
                query.setString(2, json.writeValueAsString(group.getOrDefault("fields", List.of())));
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    BusinessRules.require(row.getBoolean(1), ErrorCode.INVALID_REQUEST);
                }
            } catch (IOException exception) {
                throw new SQLException("Invalid category props", exception);
            }
            return null;
        });
    }

    private Category map(ResultSet row) throws SQLException {
        try {
            Map<String, Object> props = json.readValue(
                row.getString("props"),
                new TypeReference<LinkedHashMap<String, Object>>() {}
            );
            return Category.builder()
                .id(row.getLong("id"))
                .code(row.getString("code"))
                .type(row.getString("type"))
                .name(row.getString("name"))
                .description(row.getString("description"))
                .refType(row.getString("ref_type"))
                .parentId(row.getObject("parent_id", Long.class))
                .props(props)
                .groupCode(row.getString("group_code"))
                .valueRef(row.getString("value_ref"))
                .active(row.getBoolean("active"))
                .sortOrder(row.getInt("sort_order"))
                .createdAt(row.getTimestamp("created_at").toInstant())
                .updatedAt(row.getTimestamp("updated_at").toInstant())
                .createdBy(row.getLong("created_by"))
                .updatedBy(row.getLong("updated_by"))
                .version(row.getLong("version"))
                .parentName(row.getString("parent_name"))
                .parentNameEn(row.getString("parent_name_en"))
                .parentActive(row.getBoolean("parent_active"))
                .build();
        } catch (IOException exception) {
            throw new SQLException("Invalid stored category JSON", exception);
        }
    }

    /**
     * Seed giá trị nền bằng batch; không phục hồi mục đã tắt hoặc ghi đè props đã sửa.
     */
    public void seedCatalogs(Connection connection) throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO category(type, ref_type, code, name, props, sort_order)
                SELECT 'VALUE', seed.ref_type, seed.code, seed.name,
                    jsonb_build_object('nameEn', seed.name_en) ||
                        CASE WHEN seed.seats IS NULL THEN '{}'::jsonb ELSE jsonb_build_object('seatCount', seed.seats) END,
                    seed.sort_order
                FROM (VALUES
                    ('VEHICLE_TYPE','SEAT','Ghế ngồi','Seated coach',45,0),
                    ('VEHICLE_TYPE','SLEEPER','Giường nằm','Sleeper coach',40,1),
                    ('VEHICLE_TYPE','LIMOUSINE','Limousine','Limousine',9,2),
                    ('BOOKING_SOURCE','WEB','Đặt trên website','Website',NULL,3),
                    ('BOOKING_SOURCE','PHONE','Đặt qua điện thoại','Phone',NULL,4)
                ) seed(ref_type, code, name, name_en, seats, sort_order)
                JOIN category g ON g.group_code = seed.ref_type AND g.active
                WHERE NOT EXISTS (
                    SELECT 1 FROM category c WHERE c.ref_type = seed.ref_type AND c.code = seed.code
                )
                ON CONFLICT(ref_type, code) DO NOTHING
                """
            )
        ) {
            insert.executeUpdate();
        }
        seedAdministrative(connection);
    }

    /**
     * Bộ nguồn đã đối chiếu nằm trong artifact; hai INSERT theo tập, không gọi HTTP lúc startup.
     */
    private void seedAdministrative(Connection connection) throws SQLException {
        String source;
        try (InputStream input = CategoryRepo.class.getResourceAsStream("/bootstrap/administrative-divisions.json")) {
            if (input == null) throw new IOException("Administrative dataset missing");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Cannot load administrative dataset", exception);
        }
        // Giữ mã nghiệp vụ/ID cũ của 12 tỉnh đã có trong bộ tuyến; mã NSO lưu riêng dạng chuỗi.
        String provinces = """
        INSERT INTO category(type, ref_type, code, name, props, sort_order)
        SELECT 'VALUE', 'PROVINCE', p."localCode", p.name,
            jsonb_build_object('nameEn', p."nameEn", 'administrativeCode', p.code, 'divisionType', p."divisionType"),
            p.code::integer
        FROM jsonb_to_recordset((?::jsonb)->'provinces') p(
            code text, name text, "nameEn" text, "divisionType" text, "localCode" text
        )
        JOIN category g ON g.group_code = 'PROVINCE' AND g.active
        WHERE NOT EXISTS (
            SELECT 1 FROM category c WHERE c.ref_type = 'PROVINCE' AND c.code = p."localCode"
        )
        ON CONFLICT(ref_type, code) DO NOTHING
        """;
        try (PreparedStatement insert = connection.prepareStatement(provinces)) {
            insert.setString(1, source);
            insert.executeUpdate();
        }
        String wards = """
        INSERT INTO category(type, ref_type, code, parent_id, name, props, sort_order)
        SELECT 'VALUE', 'WARD', w.code, c.id, w.name,
            jsonb_build_object('nameEn', w.name, 'administrativeCode', w.code, 'divisionType', w."divisionType"), 0
        FROM jsonb_to_recordset((?::jsonb)->'provinces') p("localCode" text, wards jsonb)
        JOIN category c ON c.type = 'VALUE' AND c.ref_type = 'PROVINCE' AND c.code = p."localCode" AND c.active
        JOIN category g ON g.group_code = 'WARD' AND g.active
        CROSS JOIN LATERAL jsonb_to_recordset(p.wards) w(code text, name text, "divisionType" text)
        WHERE NOT EXISTS (
            SELECT 1 FROM category item WHERE item.ref_type = 'WARD' AND item.code = w.code
        )
        ON CONFLICT(ref_type, code) DO NOTHING
        """;
        try (PreparedStatement insert = connection.prepareStatement(wards)) {
            insert.setString(1, source);
            insert.executeUpdate();
        }
    }
}
