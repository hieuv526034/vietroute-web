import java.util.List;
import java.util.Set;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.auth.security.AccessMethodExpressionHandler;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
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
        // Tài nguyên/hành động ngoài hằng bootstrap: dùng đúng meta-annotation và proxy method security production.
        try (var context = new AnnotationConfigApplicationContext()) {
            context.register(GuardConfig.class);
            context.registerBean("accessGuard", DatabaseAccessGuard.class, () -> guard);
            context.registerBean(BookingModule.class);
            context.refresh();
            SecurityContextHolder.getContext().setAuthentication(auth);
            boolean allowed;
            try {
                allowed = context.getBean(BookingModule.class).list();
            } catch (AccessDeniedException exception) {
                allowed = false;
            }
            if (allowed != expected) throw new IllegalStateException("Generic method guard không khớp DB");
        } finally {
            SecurityContextHolder.clearContext();
        }
        System.out.println("DB guard matched current database state without trusting stale authorities.");
    }

    @Configuration
    @EnableMethodSecurity
    static class GuardConfig {

        @Bean
        static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
            return new AccessMethodExpressionHandler();
        }
    }

    @AccessResource("BOOKING")
    public static class BookingModule {

        @AccessOperation(action = PermissionAction.LIST)
        public boolean list() {
            // Thân service phải nhận snapshot DB mới, không chỉ guard trả true rồi giữ authority/ADMIN giả cũ.
            var current = SecurityContextHolder.getContext().getAuthentication();
            if (
                ((UserPrincipal) current.getPrincipal()).roles().contains(Role.ADMIN) ||
                current.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch("BOOKING_DETAIL"::equals)
            ) throw new IllegalStateException("Context service còn quyền cũ ngoài DB");
            return true;
        }
    }
}
