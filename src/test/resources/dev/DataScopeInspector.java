import java.util.List;
import java.util.Set;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Chỉ DB Docker CI: cố tình giữ authority ADMIN giả để chứng minh scope guard vẫn đọc mapping/account DB thật. */
public final class DataScopeInspector {

    private DataScopeInspector() {}

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_GUARD_DB_URL");
        if (!url.startsWith("jdbc:postgresql://vietroute-db-ci:5432/")) throw new IllegalArgumentException(
            "Chỉ chạy DB Docker CI"
        );
        var source = new PGSimpleDataSource();
        source.setURL(url);
        source.setUser(System.getenv("CI_GUARD_DB_USER"));
        source.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        long user = Long.parseLong(args[0]),
            version = Long.parseLong(args[1]),
            scope = Long.parseLong(args[2]);
        var action = PermissionAction.valueOf(args[3]);
        boolean expected = Boolean.parseBoolean(args[4]);
        var actor = new UserPrincipal(user, "scopeci", Set.of(Role.ADMIN), version);
        SecurityContextHolder.getContext().setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                actor,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("BOOKING_DETAIL"))
            )
        );
        try (
            var connection = new JdbcConnectionFactory(
                source,
                new JdbcSchema(System.getenv("CI_GUARD_DB_SCHEMA"))
            ).open()
        ) {
            connection.setAutoCommit(false);
            var guard = new DataScopeGuard();
            boolean actual;
            try {
                guard.require(connection, scope, "BOOKING", action);
                actual = true;
            } catch (BusinessException error) {
                if (error.getErrorCode() != ErrorCode.ACCESS_DENIED) throw error;
                actual = false;
            }
            if (actual != expected) throw new IllegalStateException("Scope guard không khớp DB hiện hành");
            if (args.length > 5) {
                long other = Long.parseLong(args[5]);
                // DAO mẫu chỉ trong bảng TEMP: có quyền A vẫn không đọc/sửa row B bằng đổi ID khi SQL có scope predicate.
                try (var query = connection.createStatement()) {
                    query.execute(
                        "CREATE TEMP TABLE ci_scope_rows(id BIGINT PRIMARY KEY,scope_id BIGINT NOT NULL REFERENCES auth_data_scopes(id)) ON COMMIT DROP"
                    );
                }
                try (var query = connection.prepareStatement("INSERT INTO ci_scope_rows VALUES (1,?),(2,?)")) {
                    query.setLong(1, scope);
                    query.setLong(2, other);
                    query.executeUpdate();
                }
                try (
                    var query = connection.prepareStatement("SELECT id FROM ci_scope_rows WHERE id=2 AND scope_id=?")
                ) {
                    query.setLong(1, scope);
                    try (var row = query.executeQuery()) {
                        if (row.next()) throw new IllegalStateException("Đọc chéo scope");
                    }
                }
                try (
                    var query = connection.prepareStatement("UPDATE ci_scope_rows SET id=id WHERE id=2 AND scope_id=?")
                ) {
                    query.setLong(1, scope);
                    if (query.executeUpdate() != 0) throw new IllegalStateException("Sửa chéo scope");
                }
                boolean denied = false;
                try {
                    DataScopeGuard.requireSameScope(scope, other);
                } catch (BusinessException error) {
                    denied = true;
                }
                if (!denied) throw new IllegalStateException("Quan hệ cha/con chéo scope phải bị chặn");
            }
            connection.rollback();
        } finally {
            SecurityContextHolder.clearContext();
        }
        System.out.println("Scope guard matched current DB; scoped role isolation and row predicate hold.");
    }
}
