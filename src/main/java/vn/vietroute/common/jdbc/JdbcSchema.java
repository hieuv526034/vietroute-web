package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Schema ứng dụng từ cấu hình; không có biến static toàn cục hoặc tên schema trong repository/entity. */
public final class JdbcSchema {

    private final String name;

    public JdbcSchema(String name) {
        validate(name);
        this.name = name;
    }

    public String name() {
        return name;
    }

    /** Identifier được whitelist rồi quote, kể cả tên trùng keyword PostgreSQL. */
    public String quoted() {
        return "\"" + name + "\"";
    }

    public static void validate(String name) {
        if (
            name == null ||
            !name.matches("[a-z][a-z0-9_]{0,62}") ||
            name.startsWith("pg_") ||
            name.equals("public") ||
            name.equals("information_schema")
        ) {
            throw new IllegalStateException(
                "VIETROUTE_DB_SCHEMA must be a private lowercase PostgreSQL identifier (1-63 characters)"
            );
        }
    }

    /**
     * Mỗi lần mượn pool đều đặt lại path, không tin currentSchema trong URL hoặc trạng thái borrower trước.
     * Không có public/$user fallback; pg_temp ở cuối để bảng tạm không che bảng ứng dụng.
     * pg_catalog được PostgreSQL ưu tiên ngầm trước path để bảo vệ hàm/kiểu hệ thống.
     */
    public void select(Connection connection) throws SQLException {
        try (
            PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.set_config('search_path', ?, false)"
            )
        ) {
            statement.setString(1, quoted() + ", pg_temp");
            statement.execute();
        }
    }
}
