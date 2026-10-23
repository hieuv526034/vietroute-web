package vn.vietroute.support;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
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

    /** Projection giả chỉ trong test; giữ method security của AccessResourceService thật. */
    @Bean
    @Primary
    public AccessResourceRepository isolatedAccessResources() {
        return new AccessResourceRepository(null) {
            @Override
            public PageResponse<AccessResourceResponse> page(PageRequest page) {
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
                            Instant.EPOCH
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
