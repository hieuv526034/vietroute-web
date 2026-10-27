import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Đo production DAO trên DB Docker CI: workload nhỏ có trần, không đọc .env hoặc in credential/SQL payload. */
public final class AccessCostInspector {

    private AccessCostInspector() {}

    public static void main(String[] args) throws Exception {
        long id = Long.parseLong(args[0]);
        long version = Long.parseLong(args[1]);
        var config = new HikariConfig();
        config.setJdbcUrl(System.getenv("CI_GUARD_DB_URL"));
        config.setUsername(System.getenv("CI_GUARD_DB_USER"));
        config.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(0);
        config.setConnectionTimeout(2000);
        try (var pool = new HikariDataSource(config)) {
            var measured = new MeasuredSource(pool);
            var connections = new JdbcConnectionFactory(
                measured.source(),
                new JdbcSchema(System.getenv("CI_GUARD_DB_SCHEMA"))
            );
            var users = new UserRepository(connections);
            var guard = new DatabaseAccessGuard(new AuthorizationRepository(connections));
            var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(id, "costci", Set.of(), version),
                null,
                List.of()
            );
            // Cùng dữ liệu DB: cả hai đường dùng một SELECT; chỉ đường credential có password hash.
            measured.statements.set(0);
            var snapshot = users.findById(id);
            int optimized = measured.statements.get();
            measured.statements.set(0);
            try (var connection = connections.open()) {
                var old = users.findById(connection, id, false);
                if (
                    snapshot.getPasswordHash() != null ||
                    old.getPasswordHash() == null ||
                    !old.getRoles().equals(snapshot.getRoles()) ||
                    !old.getPermissions().equals(snapshot.getPermissions())
                ) throw new IllegalStateException("Projection phải giữ quyền hiện hành và bỏ credential");
            }
            int credentialPath = measured.statements.get();
            if (optimized != 2 || credentialPath != 2) throw new IllegalStateException(
                "Sai số statement, gồm cả set search_path"
            );
            for (int index = 0; index < 10; index++) {
                users.findById(id);
                if (!guard.has(authentication, "BOOKING_LIST")) throw new IllegalStateException("Thiếu quyền fixture");
            }
            measured.statements.set(0);
            measured.borrowNanos.set(0);
            measured.maxBorrowNanos.set(0);
            var executor = Executors.newFixedThreadPool(4);
            List<Long> durations = new ArrayList<>();
            try {
                var futures = new ArrayList<Future<List<Long>>>();
                for (int worker = 0; worker < 4; worker++) futures.add(
                    executor.submit(() -> {
                        List<Long> samples = new ArrayList<>();
                        for (int operation = 0; operation < 25; operation++) {
                            long start = System.nanoTime();
                            if (
                                users.findById(id) == null || !guard.has(authentication, "BOOKING_LIST")
                            ) throw new IllegalStateException("Guard phải giữ quyền DB");
                            samples.add(System.nanoTime() - start);
                        }
                        return samples;
                    })
                );
                for (var future : futures) durations.addAll(future.get());
            } finally {
                executor.shutdownNow();
            }
            durations.sort(Long::compareTo);
            double p95 = durations.get(94) / 1_000_000d;
            if (
                durations.size() != 100 || measured.statements.get() != 400 || p95 > 2000
            ) throw new IllegalStateException("Chi phí/query hoặc ngưỡng CI không đạt");
            System.out.println(
                new ObjectMapper().writeValueAsString(
                    Map.of(
                        "workload",
                        "4 workers x 25 operations; user projection + current DB guard",
                        "operations",
                        durations.size(),
                        "errors",
                        0,
                        "poolMaximum",
                        2,
                        "statementsPerOperationIncludingSearchPath",
                        4,
                        "credentialPathStatements",
                        credentialPath,
                        "optimizedUserStatements",
                        optimized,
                        "p95Ms",
                        p95,
                        "meanBorrowMs",
                        measured.borrowNanos.get() / 200_000_000d,
                        "maxBorrowMs",
                        measured.maxBorrowNanos.get() / 1_000_000d
                    )
                )
            );
        }
    }

    /** Chỉ instrumentation test: đếm prepareStatement thực sự của DAO và thời gian mượn pool, không sửa câu SQL/giá trị. */
    static final class MeasuredSource {

        private final HikariDataSource pool;

        final AtomicInteger statements = new AtomicInteger();

        final AtomicLong borrowNanos = new AtomicLong();

        final AtomicLong maxBorrowNanos = new AtomicLong();

        MeasuredSource(HikariDataSource pool) {
            this.pool = pool;
        }

        DataSource source() {
            return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    if (
                        method.getName().equals("getConnection") && (args == null || args.length == 0)
                    ) return getConnection();
                    try {
                        return method.invoke(pool, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                }
            );
        }

        public Connection getConnection() throws SQLException {
            long start = System.nanoTime();
            Connection connection = pool.getConnection();
            long elapsed = System.nanoTime() - start;
            borrowNanos.addAndGet(elapsed);
            maxBorrowNanos.accumulateAndGet(elapsed, Math::max);
            return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[] { Connection.class },
                (proxy, method, args) -> {
                    if (method.getName().equals("prepareStatement")) statements.incrementAndGet();
                    try {
                        return method.invoke(connection, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                }
            );
        }
    }
}
