import java.util.List;
import java.util.Set;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;

/** Chỉ CI container tạm: gọi đúng hàm production bằng Authentication cố tình giữ quyền cũ, không qua JWT filter. */
public final class DatabaseGuardInspector {

    private DatabaseGuardInspector() {}

    public static void main(String[] args) {
        var source = new PGSimpleDataSource();
        source.setURL(System.getenv("CI_GUARD_DB_URL"));
        source.setUser(System.getenv("CI_GUARD_DB_USER"));
        source.setPassword(System.getenv("CI_GUARD_DB_PASSWORD"));
        var guard = new DatabaseAccessGuard(
            new AuthorizationRepository(
                new JdbcConnectionFactory(source, new JdbcSchema(System.getenv("CI_GUARD_DB_SCHEMA")))
            )
        );
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(Long.valueOf(args[0]), "guardci", Set.of(Role.ADMIN), Long.parseLong(args[1])),
            null,
            List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("BOOKING_LIST"),
                new SimpleGrantedAuthority("BOOKING_DETAIL")
            )
        );
        boolean expected = Boolean.parseBoolean(args[2]);
        if (
            guard.has(auth, "BOOKING_LIST") != expected ||
            guard.any(auth, "BOOKING_LIST", "BOOKING_DETAIL") != expected ||
            guard.all(auth, "BOOKING_LIST", "BOOKING_DETAIL") ||
            guard.admin(auth) ||
            guard.adminAll(auth, "BOOKING_LIST")
        ) throw new IllegalStateException("DB guard không khớp mapping/state hiện hành");
        System.out.println("DB guard matched current database state without trusting stale authorities.");
    }
}
