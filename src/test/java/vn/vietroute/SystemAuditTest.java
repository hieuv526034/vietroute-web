package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AuthService;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.config.AppSettings;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.dto.response.JobResponse;
import vn.vietroute.common.job.repository.JobRepository;
import vn.vietroute.common.job.service.JobService;
import vn.vietroute.support.JournalTestConfig;

/** Kiểm actor kỹ thuật không có đường đăng nhập/cấp JWT; mọi port giả đều không kết nối DB. */
class SystemAuditTest {

    @Test
    void bootstrapCannotUseReservedSystemUsername() {
        assertThrows(IllegalStateException.class, () ->
            new AppSettings(Map.of("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME", SystemActor.USERNAME)::get)
        );
    }

    @Test
    void systemCannotReceiveJwtEvenWhenCallerMarksItActive() {
        var jwt = new JwtService(new AppSettings(key -> null), Clock.systemUTC());
        var error = assertThrows(BusinessException.class, () -> jwt.issue(system()));
        assertEquals(ErrorCode.TOKEN_INVALID, error.getErrorCode());
    }

    @Test
    void loginRejectsSystemWithoutChangingItsAuditOrFailureCounter() {
        var repository = new UserRepository(null) {
            @Override
            public UserAccount findByUsername(Connection connection, String username, boolean lock) {
                assertEquals(SystemActor.USERNAME, username);
                return system();
            }

            @Override
            public void recordFailure(Connection connection, long id, int attempts, Instant until) {
                throw new AssertionError("Không cập nhật actor kỹ thuật từ request đăng nhập");
            }
        };
        var transactions = new JdbcTransactions(null) {
            @Override
            public <T> T execute(SqlWork<T> work) {
                try {
                    return work.apply(null);
                } catch (SQLException exception) {
                    throw new AssertionError(exception);
                }
            }
        };
        var auth = new AuthService(repository, transactions, new BCryptPasswordEncoder(), null, Clock.systemUTC());
        var request = new LoginRequest();
        request.setUsername(SystemActor.USERNAME);
        request.setPassword("Not-A-System-Password-2026!");
        var error = assertThrows(BusinessException.class, () -> auth.login(request));
        assertEquals(ErrorCode.INVALID_CREDENTIALS, error.getErrorCode());
    }

    @Test
    void assigningRolesToSystemIsDeniedInProductionContextHierarchy() throws Exception {
        // Dựng root/servlet tách biệt như WAR, tránh chỉ kiểm menu hoặc context gộp.
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var servlet = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, JournalTestConfig.class);
            root.refresh();
            servlet.setParent(root);
            servlet.setServletContext(web);
            servlet.register(WebConfig.class);
            servlet.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(servlet).apply(springSecurity()).build();
            var principal = new UserPrincipal(1L, "admin", Set.of(Role.ADMIN), 0);
            var admin = UsernamePasswordAuthenticationToken.authenticated(
                principal,
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ACCESS_ASSIGN"))
            );
            var response = mvc
                .perform(
                    put(Routes.ACCESS + Routes.USER_ROLES_ACTION.replace("{id}", "0"))
                        .with(authentication(admin))
                        .with(csrf())
                        .contentType("application/json")
                        .content("{\"roles\":[\"ADMIN\"],\"version\":0}")
                )
                .andReturn()
                .getResponse();
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("AUTH003"));
        }
    }

    @Test
    void manualJobRetryPassesAuthenticatedActorToRepository() {
        var recorded = new AtomicLong(-1);
        var repository = new JobRepository(null) {
            @Override
            public void retry(Connection connection, UUID id, long version, long actor) {
                recorded.set(actor);
            }

            @Override
            public JobResponse find(UUID id) {
                return null;
            }
        };
        var transactions = new JdbcTransactions(null) {
            @Override
            public <T> T execute(SqlWork<T> work) {
                try {
                    return work.apply(null);
                } catch (SQLException exception) {
                    throw new AssertionError(exception);
                }
            }
        };
        var actor = new UserPrincipal(42L, "operator", Set.of(Role.OPERATOR), 0);
        new JobService(repository, transactions).retry(UUID.randomUUID(), 3, actor);
        assertEquals(actor.id(), recorded.get());
    }

    private UserAccount system() {
        // Cố ý active=true để chứng minh lớp Java vẫn chặn dù dữ liệu giả không đúng CHECK của DB.
        return UserAccount.builder().id(SystemActor.ID).username(SystemActor.USERNAME).active(true).build();
    }
}
