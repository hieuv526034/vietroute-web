package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.vietroute.support.NativeEndpointWebFixture;

/** Engine mới không cần định nghĩa permission: mapping Spring MVC là nguồn danh bạ duy nhất. */
class AccessPermissionDefinitionsTest {

    @Test
    void newModuleAppearsWithoutCustomAnnotationAndKeepsItsSwaggerDescription() {
        try (var web = new NativeEndpointWebFixture()) {
            var endpoint = web.catalog.find("GET /api/v1/native-probe/{id}");
            assertEquals("Chi tiết tự thu thập", endpoint.getDescription());
            assertTrue(endpoint.getHandler().contains("ProbeAPI#get"));
            assertEquals(
                Set.of("GET", "PUT"),
                web.catalog
                    .assignable()
                    .stream()
                    .filter(item -> item.getPathTemplate().contains("{id}"))
                    .map(item -> item.getHttpMethod())
                    .collect(Collectors.toSet())
            );
        }
    }

    @Test
    void missingHttpMethodFailsBeforePublishingAnIncompleteInventory() {
        assertThrows(IllegalStateException.class, () ->
            new NativeEndpointWebFixture(NativeEndpointWebFixture.InvalidAPI.class)
        );
    }
}
