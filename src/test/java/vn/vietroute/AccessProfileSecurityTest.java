package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.support.NativeSecurityFixture;

/** Giữ projection profile an toàn và quyền phạm vi theo endpoint thay nhánh tên ADMIN. */
class AccessProfileSecurityTest {

    @Test
    void profileProjectionNeverReturnsCredentialOrTokenVersion() throws Exception {
        var user = User.builder()
            .id(7L)
            .username("user")
            .displayName("Người dùng")
            .passwordHash("do-not-return")
            .previousPasswordHash1("old-hash")
            .roles(Set.of("CUSTOM"))
            .targets(Set.of("GET /one"))
            .tokenVersion(99)
            .build();
        String json = new ObjectMapper().writeValueAsString(UserDTO.from(user));
        assertFalse(json.contains("do-not-return"));
        assertFalse(json.contains("old-hash"));
        assertFalse(json.contains("tokenVersion"));
        assertTrue(json.contains("GET /one"));
    }

    @Test
    void customDbRoleDoesNotInheritGlobalDataFromItsName() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("ADMIN"), Set.of("GET /one"));
        try {
            SecurityContextHolder.getContext().setAuthentication(
                fixture.service.authorize(fixture.actor(7, "ADMIN", 2), "GET /one", false)
            );
            assertFalse(UserPrincipal.globalData());
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}
