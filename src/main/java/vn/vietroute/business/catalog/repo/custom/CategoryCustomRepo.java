package vn.vietroute.business.catalog.repo.custom;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ.
 */
@Repository
@RequiredArgsConstructor
public class CategoryCustomRepo {

    private final ObjectMapper json;

    /**
     * Seed giá trị nền bằng batch; không phục hồi mục đã tắt hoặc ghi đè props đã sửa.
     */
    public void seedCatalogs(Connection connection) throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO category(type, ref_type, code, name, props, sort_order)
                SELECT 'VALUE', seed.ref_type, seed.code, seed.name, JSONB_BUILD_OBJECT('nameEn', seed.name_en) ||
                    CASE WHEN seed.seats IS NULL THEN '{}' ::JSONB ELSE JSONB_BUILD_OBJECT('seatCount', seed.seats) END,
                    seed.sort_order
                FROM (VALUES
                    ('VEHICLE_TYPE', 'SEAT', 'Ghế ngồi', 'Seated coach', 45, 0),
                    ('VEHICLE_TYPE', 'SLEEPER', 'Giường nằm', 'Sleeper coach', 40, 1),
                    ('VEHICLE_TYPE', 'LIMOUSINE', 'Limousine', 'Limousine', 9, 2),
                    ('BOOKING_SOURCE', 'WEB', 'Đặt trên website', 'Website', NULL, 3),
                    ('BOOKING_SOURCE', 'PHONE', 'Đặt qua điện thoại', 'Phone', NULL, 4)
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
        try (
            InputStream input = CategoryCustomRepo.class.getResourceAsStream("/bootstrap/administrative-divisions.json")
        ) {
            if (input == null) throw new IOException("Administrative dataset missing");
            source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new SQLException("Cannot load administrative dataset", exception);
        }
        // Giữ mã nghiệp vụ/ID cũ của 12 tỉnh đã có trong bộ tuyến; mã NSO lưu riêng dạng chuỗi.
        String provinces = """
        INSERT INTO category(type, ref_type, code, name, props, sort_order)
        SELECT 'VALUE', 'PROVINCE', p. "localCode", p.name,
            JSONB_BUILD_OBJECT('nameEn', p. "nameEn", 'administrativeCode', p.code, 'divisionType', p.
                "divisionType"), p.code::INTEGER
        FROM JSONB_TO_RECORDSET((?::JSONB)-> 'provinces') p(
            code TEXT, name TEXT, "nameEn" TEXT, "divisionType" TEXT, "localCode" TEXT
        )
        JOIN category g ON g.group_code = 'PROVINCE' AND g.active
        WHERE NOT EXISTS (
            SELECT 1 FROM category c WHERE c.ref_type = 'PROVINCE' AND c.code = p. "localCode"
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
            JSONB_BUILD_OBJECT('nameEn', w.name, 'administrativeCode', w.code, 'divisionType', w. "divisionType"), 0
        FROM JSONB_TO_RECORDSET((?::JSONB)-> 'provinces') p("localCode" TEXT, wards JSONB)
        JOIN category c ON c.type = 'VALUE' AND c.ref_type = 'PROVINCE' AND c.code = p. "localCode" AND c.active
        JOIN category g ON g.group_code = 'WARD' AND g.active
        CROSS JOIN LATERAL JSONB_TO_RECORDSET(p.wards) w(code TEXT, name TEXT, "divisionType" TEXT)
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
