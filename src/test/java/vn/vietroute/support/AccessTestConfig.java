package vn.vietroute.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.vietroute.auth.dto.request.AccessResourceSearchRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.AccessResourceRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;

/** Fixture chỉ trong test: registry/policy DB giả, không thay hoặc bỏ method security của service/controller. */
@Configuration
public class AccessTestConfig {

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
