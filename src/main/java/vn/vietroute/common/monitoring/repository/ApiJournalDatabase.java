package vn.vietroute.common.monitoring.repository;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import vn.vietroute.common.config.DatabaseSettings;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Pool nhật ký tối đa một connection/node; không giành slot login/nghiệp vụ hoặc xếp queue lịch sử. */
public final class ApiJournalDatabase implements AutoCloseable {

    private final HikariDataSource pool;

    private final JdbcConnectionFactory connections;

    public ApiJournalDatabase(DatabaseSettings settings, JdbcSchema schema) {
        pool = new HikariDataSource();
        pool.setPoolName("vietroute-api-journal");
        pool.setDriverClassName("org.postgresql.Driver");
        pool.setJdbcUrl(settings.getUrl());
        pool.setUsername(settings.getUsername());
        pool.setPassword(settings.getPassword());
        pool.setMaximumPoolSize(1);
        pool.setMinimumIdle(0);
        pool.setConnectionTimeout(250);
        pool.setValidationTimeout(250);
        pool.setIdleTimeout(60000);
        pool.setMaxLifetime(600000);
        pool.setKeepaliveTime(0);
        pool.setInitializationFailTimeout(-1);
        pool.addDataSourceProperty("connectTimeout", 2);
        pool.addDataSourceProperty("socketTimeout", 2);
        pool.addDataSourceProperty("ApplicationName", "vietroute-api-journal");
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
