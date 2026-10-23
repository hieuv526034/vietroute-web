package vn.vietroute.support;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.repository.AccessRepository;

/** Fixture chỉ trong test: registry/policy DB giả, không thay hoặc bỏ method security của service/controller. */
@Configuration
public class AccessTestConfig {

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
