package vn.vietroute.auth.security;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import vn.vietroute.auth.dto.response.AccessCapabilityResponse;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.service.AccessManagementService;

/** Cùng phép tính cho header và nút: guard source + authorities DB + chính sách siết riêng endpoint. */
public final class AccessCapabilities {

    private AccessCapabilities() {}

    public static List<AccessCapabilityResponse> compute(
        List<AccessResourceResponse> inventory,
        Authentication auth,
        List<AccessPolicyResponse> policies
    ) {
        List<AccessCapabilityResponse> result = new ArrayList<>();
        for (var feature : AccessFeatures.definitions()) {
            Set<String> actions = new LinkedHashSet<>();
            Set<String> targets = new LinkedHashSet<>();
            boolean view = false;
            for (var endpoint : inventory) {
                if (!endpoint.function().equals(feature.code()) || endpoint.publicRoute()) continue;
                boolean allowed =
                    AccessAuthorization.allows(endpoint, auth) &&
                    policies
                        .stream()
                        .filter(policy -> policy.target().equals(endpoint.target()))
                        .allMatch(policy -> AccessManagementService.allows(policy, auth));
                if (!allowed) continue;
                targets.add(endpoint.target());
                if (feature.path() != null && endpoint.target().equals("GET " + feature.path())) view = true;
                // ANY có thể được cấp qua mã WRITE tương thích; action của endpoint được khai báo bằng permission chuẩn.
                endpoint
                    .declaredPermissions()
                    .stream()
                    .filter(code -> !code.endsWith("_WRITE"))
                    .map(Permission::valueOf)
                    .forEach(permission -> permission.actions().forEach(action -> actions.add(action.name())));
            }
            result.add(new AccessCapabilityResponse(feature.code(), view, Set.copyOf(actions), Set.copyOf(targets)));
        }
        return List.copyOf(result);
    }
}
