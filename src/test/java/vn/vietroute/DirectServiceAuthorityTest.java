package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessService;
import vn.vietroute.auth.service.AccountLifecycleService;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.auth.service.ProfileService;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Lời gọi bean không qua interceptor: snapshot DB phải được dùng cả guard lẫn kiểm tập quyền được ủy quyền trong thân hàm. */
class DirectServiceAuthorityTest {

    @Test
    void selfServiceBeansRejectAnotherUsersPrincipalBeforeReadingOrWriting() {
        try (var root = new AnnotationConfigWebApplicationContext()) {
            root.setServletContext(new MockServletContext("src/main/webapp", new FileSystemResourceLoader()));
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, NoWrites.class);
            root.refresh();
            var current = new UserPrincipal(8L, "self", Set.of(Role.CUSTOMER), 0);
            var foreign = new UserPrincipal(9L, "other", Set.of(Role.CUSTOMER), 0);
            SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(current, null, List.of())
            );
            List<Runnable> calls = List.of(
                () -> root.getBean(AuthService.class).me(foreign),
                () -> root.getBean(AuthService.class).me(new UserPrincipal(null, "invalid", Set.of(Role.CUSTOMER), 0)),
                () -> root.getBean(AuthService.class).logout(foreign),
                () -> root.getBean(AuthService.class).changePassword(foreign, null),
                () -> root.getBean(ProfileService.class).update(null, foreign),
                () -> root.getBean(AccountLifecycleService.class).bindEmail(foreign, null, Locale.ENGLISH)
            );
            for (var call : calls) {
                assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class, call::run).getErrorCode());
            }
            assertEquals(0, root.getBean(RefuseTransactions.class).calls.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    void staleAdminOrPermissionCannotPassDelegationInsideRealService() {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, NoWrites.class);
            root.refresh();
            servlet.setParent(root);
            servlet.setServletContext(web);
            servlet.register(WebConfig.class);
            servlet.refresh();
            var db = root.getBean(AccessTestConfig.AuthorizationFixture.class);
            db.strict = true;
            db.states.put(
                8L,
                new AuthorizationState(8, true, false, false, true, 0, Set.of(Role.DRIVER), Set.of("ACCESS_ASSIGN"))
            );
            var request = new RolePermissionsRequest();
            request.setPermissions(Set.of(Permission.CATALOG_DELETE));
            request.setVersion(0L);
            for (var oldRoles : List.of(Set.of(Role.ADMIN), Set.of(Role.DRIVER))) {
                var actor = new UserPrincipal(8L, "delegate", oldRoles, 0);
                var stale = UsernamePasswordAuthenticationToken.authenticated(
                    actor,
                    null,
                    List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ACCESS_ASSIGN"),
                        new SimpleGrantedAuthority("CATALOG_DELETE")
                    )
                );
                SecurityContextHolder.getContext().setAuthentication(stale);
                var error = assertThrows(BusinessException.class, () ->
                    root.getBean(AccessService.class).replace(Role.CUSTOMER, request, actor)
                );
                assertEquals(ErrorCode.ACCESS_DENIED, error.getErrorCode());
            }
            assertEquals(0, root.getBean(RefuseTransactions.class).calls.get());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Configuration
    static class NoWrites {

        @Bean
        @Primary
        RefuseTransactions noSqlWrites() {
            return new RefuseTransactions();
        }
    }

    /** Regression không được mở JDBC, kể cả khi chưa sửa: phát hiện ngay thân hàm vượt được kiểm ủy quyền. */
    static class RefuseTransactions extends JdbcTransactions {

        final AtomicInteger calls = new AtomicInteger();

        RefuseTransactions() {
            super(null);
        }

        @Override
        public <T> T execute(SqlWork<T> action) {
            calls.incrementAndGet();
            throw new AssertionError("Không được bắt đầu transaction khi chỉ có quyền cũ");
        }
    }
}
