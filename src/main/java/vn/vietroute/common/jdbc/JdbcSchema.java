package vn.vietroute.common.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import vn.vietroute.common.jdbc.dialect.JdbcDialect;

/** Schema ứng dụng từ cấu hình; adapter giữ các quy tắc riêng của hệ DB. */
public final class JdbcSchema {

    private final String name;

    private final JdbcDialect dialect;

    /** Tương thích các caller PostgreSQL hiện hành, không suy đoán hệ DB từ connection đang mượn. */
    public JdbcSchema(String name) {
        this(name, JdbcDialect.postgresql());
    }

    public JdbcSchema(String name, JdbcDialect dialect) {
        dialect.validateSchema(name);
        this.name = name;
        this.dialect = dialect;
    }

    public String name() {
        return name;
    }

    /** Identifier được adapter validate trước khi quote; dùng cho metadata/migration hiện hành. */
    public String quoted() {
        return "\"" + name + "\"";
    }

    public static void validate(String name) {
        JdbcDialect.postgresql().validateSchema(name);
    }

    /** Chọn schema trong cùng transaction với SQL nghiệp vụ, không đổi autocommit/commit của caller. */
    public void select(Connection connection) throws SQLException {
        dialect.selectSchema(connection, name);
    }
}
