package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeSecurityFixture;

/** Không còn fallback theo permission/ADMIN; method và route template phải đúng binding trong DB. */
class ResourceEntitlementsTest {

    @Test
    void missingOrEmptySnapshotDeniesPrivateResources() {
        var fixture = new NativeSecurityFixture();
        assertThrows(BusinessException.class, () ->
            fixture.service.authorize(fixture.actor(7, "ADMIN", 2), "GET /private", false)
        );
        fixture.state = fixture.ready(Set.of("ADMIN"), Set.of());
        assertThrows(BusinessException.class, () ->
            fixture.service.authorize(fixture.actor(7, "ADMIN", 2), "GET /private", false)
        );
    }

    @Test
    void wildcardsDifferentVerbsAndConcreteIdsDoNotMatchATemplateGrant() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("CUSTOM"), Set.of("GET /items/{id}", "GET /admin/**"));
        for (String target : Set.of("GET /items", "GET /items/7", "DELETE /items/{id}", "GET /admin/accounts"))
            assertThrows(BusinessException.class, () ->
                fixture.service.authorize(fixture.actor(7, "CUSTOM", 2), target, false)
            );
        fixture.service.authorize(fixture.actor(7, "CUSTOM", 2), "GET /items/{id}", false);
    }
}
