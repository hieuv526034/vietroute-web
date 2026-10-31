package vn.vietroute.common.monitoring.repository;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import vn.vietroute.common.config.database.DatabaseSettings;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Pool nhật ký tối đa một connection/node; không giành slot login/nghiệp vụ hoặc xếp queue lịch sử. */
public final class ApiJournalDatabase implements AutoCloseable {

    private final HikariDataSource pool;

    private final JdbcConnectionFactory connections;

    public ApiJournalDatabase(DatabaseSettings settings, JdbcSchema schema) {
        pool = new HikariDataSource();
        pool.setPoolName("vietroute-api-journal");
        pool.setDriverClassName(settings.getDialect().driverClassName());
        pool.setJdbcUrl(settings.getUrl());
        pool.setUsername(settings.getUsername());
        pool.setPassword(settings.getPassword());
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(250);
        // 250ms chỉ giới hạn chờ slot của request; xác minh connection tới DB khác vùng cần ngân sách riêng.
        // Dùng 1s như pool nghiệp vụ để tránh loại mọi connection khi RTT mạng vượt 250ms.
        pool.setValidationTimeout(1000);
        pool.setIdleTimeout(60000);
        pool.setMaxLifetime(600000);
        pool.setKeepaliveTime(0);
        pool.setInitializationFailTimeout(-1);
        pool.setDataSourceProperties(settings.getDialect().connectionProperties(2, 2, "vietroute-api-journal", false));
        connections = new JdbcConnectionFactory(pool, schema);
    }

    /** Mỗi connection được đặt lại search_path cấu hình, không public/$user fallback. */
    public Connection open() throws SQLException {
        return connections.open();
    }

    @Override
    public void close() {
        pool.close();
    }
}
