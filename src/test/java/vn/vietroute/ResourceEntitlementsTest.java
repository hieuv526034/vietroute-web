package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import vn.vietroute.auth.security.AccessAuthorization;
import vn.vietroute.auth.security.ResourceEntitlements;

/** Rào tài nguyên mặc định đóng; authority văn bản/đăng nhập không thay mapping tài nguyên cụ thể. */
class ResourceEntitlementsTest {

    @Test
    void missingOrEmptySnapshotDeniesPrivateResourcesEvenWithMatchingAuthority() {
        var binding = new AccessAuthorization.Binding("VEHICLE", List.of("VEHICLE_READ"), "ANY", false, false);
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            "actor",
            null,
            List.of(new SimpleGrantedAuthority("VEHICLE_READ"))
        );
        assertFalse(AccessAuthorization.allows(binding, auth, "GET /api/vehicles"));
        auth.setDetails(new ResourceEntitlements(Set.of(), Set.of(), null, null));
        assertFalse(AccessAuthorization.allows(binding, auth, "GET /api/vehicles"));
        auth.setDetails(new ResourceEntitlements(Set.of("GET /api/vehicles"), Set.of(), null, null));
        assertTrue(AccessAuthorization.allows(binding, auth, "GET /api/vehicles"));
        assertFalse(AccessAuthorization.allows(binding, auth, "GET /api/vehicles/{id}"));
    }

    @Test
    void assignedTargetNeverBypassesMandatoryAdminAllOrSelfServiceDeclaration() {
        var grants = new ResourceEntitlements(Set.of("DELETE /api/accounts/{id}"), Set.of(), null, null);
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            "actor",
            null,
            List.of(new SimpleGrantedAuthority("ACCOUNT_DELETE"))
        );
        auth.setDetails(grants);
        assertFalse(
            AccessAuthorization.allows(
                new AccessAuthorization.Binding("ACCOUNTS", List.of("ACCOUNT_DELETE"), "ALL", true, false),
                auth,
                "DELETE /api/accounts/{id}"
            )
        );
        assertFalse(
            AccessAuthorization.allows(
                new AccessAuthorization.Binding(
                    "ACCOUNTS",
                    List.of("ACCOUNT_DELETE", "ACCESS_ASSIGN"),
                    "ALL",
                    false,
                    false
                ),
                auth,
                "DELETE /api/accounts/{id}"
            )
        );
        assertFalse(grants.allows("GET /account", new AccessAuthorization.Binding("", List.of(), "ANY", false, true)));
        assertFalse(
            AccessAuthorization.allows(
                new AccessAuthorization.Binding("", List.of(), "ANY", false, false),
                auth,
                "GET /missing-guard"
            )
        );
    }

    @Test
    void legacyFallbackUsesOnlyLegacyRolePermissionsAndRespectsAll() {
        var grants = new ResourceEntitlements(Set.of(), Set.of("VEHICLE_READ"), null, null);
        assertTrue(
            grants.allows(
                "GET /vehicles",
                new AccessAuthorization.Binding("V", List.of("VEHICLE_READ"), "ANY", false, false)
            )
        );
        assertFalse(
            grants.allows(
                "PUT /vehicles/{id}",
                new AccessAuthorization.Binding("V", List.of("VEHICLE_UPDATE"), "ANY", false, false)
            )
        );
        assertFalse(
            grants.allows(
                "POST /vehicles",
                new AccessAuthorization.Binding("V", List.of("VEHICLE_READ", "VEHICLE_CREATE"), "ALL", false, false)
            )
        );
    }
}
