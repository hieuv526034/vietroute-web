package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.support.NativeSecurityFixture;

/** Không còn cây action/VIEW tự suy; giữ khả năng menu/API độc lập và tập cấp quyền tường minh. */
class AccessTreeTest {

    @Test
    void menuGrantDoesNotInventApiCrudOrFutureEndpoints() {
        var fixture = new NativeSecurityFixture();
        fixture.state = fixture.ready(Set.of("CUSTOM"), Set.of("GET /operations/items"));
        fixture.service.authorize(fixture.actor(7, "CUSTOM", 2), "GET /operations/items", false);
        for (String target : Set.of(
            "GET /api/v1/items",
            "POST /api/v1/items",
            "DELETE /api/v1/items/{id}",
            "GET /operations/items/new"
        ))
            assertThrows(BusinessException.class, () ->
                fixture.service.authorize(fixture.actor(7, "CUSTOM", 2), target, false)
            );
    }

    @Test
    void capabilityContractContainsTargetsAndDoesNotPublishAnActionMatrix() throws Exception {
        var dto = new FunctionDTO();
        dto.setFunction("CUSTOM_FUNCTION");
        dto.setView(true);
        dto.setTargets(Set.of("GET /operations/items"));
        String json = new ObjectMapper().writeValueAsString(dto);
        assertTrue(json.contains("targets"));
        assertFalse(json.contains("actions"));
        assertFalse(json.contains("permissions"));
    }
}
