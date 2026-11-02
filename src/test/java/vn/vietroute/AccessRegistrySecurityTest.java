package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.common.config.application.AppSettings;

/** Role/Function CRUD và CAS thật ở BusinessIntegrationTest; giữ kiểm quota/bộ lọc không mở DB. */
class AccessRegistrySecurityTest {

    @Test
    void capacitySettingsHaveFiniteValidatedBoundsAndConservativeDefaults() {
        var settings = new AppSettings(key -> null);
        assertTrue(settings.getAuthRegistryCapacity() > 0);
        assertThrows(IllegalStateException.class, () ->
            new AppSettings(Map.of("VIETROUTE_ACCESS_REGISTRY_CAPACITY", "999999")::get)
        );
    }

    @Test
    void registrySearchRetainsLiteralInputAndInheritedServerPagination() {
        var search = new RoleSearch();
        search.setQuery("%_! Vai trò");
        search.setPage(2);
        search.setSize(20);
        assertEquals("%_! Vai trò", search.getQuery());
        assertEquals(40L, search.offset());
    }
}
