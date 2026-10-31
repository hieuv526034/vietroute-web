import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.vietroute.auth.dto.request.session.LoginRequest;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Đo năm login tuần tự trên DB giả cố định; không nhận URL production hoặc đọc .env, không in credential. */
public final class LoginCostInspector {

    private LoginCostInspector() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_GUARD_DB_URL");
        if (
            url == null ||
            !(
                url.equals("jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci") ||
                url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/vietroute_perf01")
            )
        ) {
            throw new IllegalArgumentException("Chỉ cho phép DB fixture Docker CI hoặc loopback perf01");
        }
        var config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(System.getenv("CI_GUARD_DB_USER"));
        config.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(0);
        try (var pool = new HikariDataSource(config)) {
            var measured = new AccessCostInspector.MeasuredSource(pool);
            var connections = new JdbcConnectionFactory(
                measured.source(),
                new JdbcSchema(System.getenv("CI_GUARD_DB_SCHEMA"))
            );
            var settings = new AppSettings(
                Map.of("VIETROUTE_JWT_SECRET", "login_cost_test_only_never_use_in_runtime_0123456789")::get
            );
            var auth = new AuthService(
                new UserRepository(connections),
                new JdbcTransactions(connections),
                new BCryptPasswordEncoder(12),
                new JwtService(settings, Clock.systemUTC()),
                Clock.systemUTC()
            );
            var request = new LoginRequest();
            request.setUsername(args.length == 0 ? "admin" : args[0]);
            request.setPassword(System.getenv("CI_LOGIN_PASSWORD"));
            auth.login(request);
            measured.statements.set(0);
            var samples = new ArrayList<Double>();
            for (int index = 0; index < 5; index++) {
                long start = System.nanoTime();
                auth.login(request);
                samples.add((System.nanoTime() - start) / 1_000_000d);
            }
            if (measured.statements.get() != 15) throw new IllegalStateException(
                "Mỗi login phải đúng 3 statement kể cả chọn schema"
            );
            samples.sort(Double::compareTo);
            System.out.println(
                new ObjectMapper().writeValueAsString(
                    Map.of(
                        "workload",
                        "5 serial successful fixture logins; BCrypt cost 12",
                        "requests",
                        5,
                        "businessStatementsPerLogin",
                        2,
                        "includingSearchPath",
                        3,
                        "meanMs",
                        samples.stream().mapToDouble(Double::doubleValue).average().orElseThrow(),
                        "maxMs",
                        samples.get(4),
                        "errors",
                        0,
                        "scope",
                        "isolated fixture service; excludes HTTP/CSRF/render/journal/network"
                    )
                )
            );
        }
    }
}
