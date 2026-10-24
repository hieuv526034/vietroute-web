package vn.vietroute.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.dto.request.AccessResourceSearchRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.AccessResourceRepository;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Fixture chỉ trong test: registry/policy DB giả, không thay hoặc bỏ method security của service/controller. */
@Configuration
public class AccessTestConfig {

    /**
     * Các test MVC cũ dùng auth giả để mô tả dữ liệu DB fixture. Chỉ thay DAO, giữ bean accessGuard thật.
     * Bộ DatabaseAccessGuardTest tách DB state khỏi principal/authorities để kiểm thu hồi và giả quyền.
     */
    @Bean
    @Primary
    public AuthorizationFixture isolatedAuthorizationRepository() {
        return new AuthorizationFixture();
    }

    /** Chế độ strict dùng state độc lập với Authentication; fallback chỉ giữ dữ liệu giả của test UI cũ. */
    public static class AuthorizationFixture extends AuthorizationRepository {

        public final Map<Long, AuthorizationState> states = new HashMap<>();

        public boolean strict;

        public int reads;

        public int failAt = Integer.MAX_VALUE;

        public AuthorizationFixture() {
            super(null);
        }

        @Override
        public AuthorizationState find(long id) {
            if (++reads >= failAt) throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
            if (strict || states.containsKey(id)) return states.get(id);
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal user) || user.id() != id) return null;
            var codes = auth.getAuthorities().stream().map(Object::toString).collect(Collectors.toSet());
            return new AuthorizationState(
                id,
                true,
                false,
                false,
                user.tokenVersion(),
                user.roles(),
                codes
                    .stream()
                    .filter(code -> !code.startsWith("ROLE_"))
                    .collect(Collectors.toSet())
            );
        }
    }

    /** Các fixture cũ biểu diễn role đã qua V013: VIEW được chuyển tiếp một lần từ quyền legacy. Test ACCESS03 dùng tập quyền tường minh riêng. */
    public static List<SimpleGrantedAuthority> upgradedAuthorities(String... codes) {
        Set<String> result = new LinkedHashSet<>(List.of(codes));
        for (String code : codes) {
            if (code.startsWith("CATALOG_")) result.add("CATALOG_VIEW");
            if (code.startsWith("MEDIA_")) result.add("MEDIA_VIEW");
            if (code.startsWith("ACCESS_")) result.add("ACCESS_VIEW");
            if (code.equals("JOB_READ")) result.add("JOB_VIEW");
            if (code.equals("SYSTEM_READ")) result.addAll(
                List.of("SYSTEM_VIEW", "CONFIGURATION_VIEW", "JOURNAL_VIEW", "CONFIGURATION_READ")
            );
        }
        return result.stream().map(SimpleGrantedAuthority::new).toList();
    }

    /** Projection giả chỉ trong test; giữ method security của AccessResourceService thật. */
    @Bean
    @Primary
    public AccessResourceRepository isolatedAccessResources() {
        return new AccessResourceRepository(null) {
            @Override
            public PageResponse<AccessResourceResponse> page(AccessResourceSearchRequest page) {
                return PageResponse.of(
                    List.of(
                        new AccessResourceResponse(
                            "MENU",
                            "GET /media",
                            "MediaPageController#media",
                            "Thư viện ảnh",
                            false,
                            true,
                            Instant.EPOCH,
                            Instant.EPOCH,
                            "MEDIA",
                            List.of("MEDIA_READ")
                        )
                    ),
                    page,
                    1
                );
            }
        };
    }

    @Bean
    @Primary
    public PolicyFixture isolatedAccessRegistry() {
        return new PolicyFixture();
    }

    @Bean
    @Primary
    public AccessRepository isolatedAccessRepository() {
        return new AccessRepository(null) {
            @Override
            public List<PermissionResponse> permissions(Locale locale) {
                return Arrays.stream(Permission.values())
                    .map(permission ->
                        new PermissionResponse(
                            permission,
                            permission.resource(),
                            permission.actions(),
                            permission.messageKey(),
                            ResourceBundle.getBundle("i18n.messages", locale).getString(permission.messageKey())
                        )
                    )
                    .toList();
            }
        };
    }

    public static class PolicyFixture extends AccessRegistryRepository {

        public List<AccessPolicyResponse> rules = List.of();

        public PolicyFixture() {
            super(null);
        }

        @Override
        public List<AccessPolicyResponse> policies() {
            return List.copyOf(rules);
        }
    }
}
