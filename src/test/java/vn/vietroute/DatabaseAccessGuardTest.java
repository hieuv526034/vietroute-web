package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeSecurityFixture;

/** AUTHDB03 thay engine action bằng snapshot endpoint hiện hành; giữ kiểm DB/thu hồi và chi phí theo tập. */
class DatabaseAccessGuardTest {

    @Test
    void authorityTextAndAdminRoleClaimsCannotCreateEndpointsWithoutDbMapping() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("ADMIN"), Set.of());
        assertThrows(BusinessException.class, () ->
            fixture.service.authorize(fixture.actor(7, "ADMIN", 2), "GET /private", false)
        );
    }

    @Test
    void anyNumberOfRolesAndTargetsUsesOneDbSnapshotPerCheck() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("CUSTOM_A", "CUSTOM_B", "CUSTOM_C"), Set.of("GET /one", "PUT /two/{id}"));
        var actor = fixture.service.authorize(fixture.actor(7, "OLD_CLAIM", 2), "GET /one", false);
        assertEquals(1, fixture.reads);
        fixture.state = fixture.ready(Set.of("CUSTOM_A"), Set.of());
        assertThrows(BusinessException.class, () -> fixture.service.authorize(actor, "GET /one", false));
        assertEquals(2, fixture.reads);
    }
}
