package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.ListResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.security.AccessPermissionDefinitions;

/** Metadata source và nhãn module mở rộng độc lập danh sách hằng bootstrap; không mô phỏng authority user. */
class AccessPermissionDefinitionsTest {

    @Test
    void sourceSnapshotIncludesHttpExtrasServiceOnlyViewAndLegacyAliases() {
        var definitions = new AccessPermissionDefinitions();
        var endpoint = new AccessResourceResponse(
            "ENDPOINT",
            "GET /fixture",
            "Fixture#read",
            "",
            false,
            true,
            null,
            null,
            "FUTUREMODULE",
            List.of("FUTUREMODULE_READ", "ANOTHER_DETAIL")
        );
        definitions.remember(List.of(endpoint), Set.of("SERVICEONLY_UPDATE"));
        for (String code : List.of(
            "FUTUREMODULE_READ",
            "ANOTHER_DETAIL",
            "SERVICEONLY_UPDATE",
            "ACCESS_VIEW",
            "CATALOG_WRITE"
        ))
            assertTrue(definitions.contains(code), code);
        assertFalse(definitions.contains("CUSTOMMODULE_READ"));
        definitions.remember(List.of(), Set.of());
        assertFalse(definitions.contains("SERVICEONLY_UPDATE"), "Snapshot mới không giữ metadata đã bỏ khỏi source");
    }

    @Test
    void newModuleUsesItsOwnBilingualKeyAndFallsBackToExactCode() {
        var permission = Permission.valueOf("FUTUREMODULE_READ");
        assertEquals("permission.FUTUREMODULE_READ", permission.messageKey());
        for (String label : List.of("Đọc module mới", "Read future module")) {
            ResourceBundle messages = new ListResourceBundle() {
                @Override
                protected Object[][] getContents() {
                    return new Object[][] {
                        { "permission.FUTUREMODULE_READ", label },
                        { "permission.custom", "generic" },
                    };
                }
            };
            assertEquals(label, AccessPermissionDefinitions.localizedName(permission, messages));
            assertEquals(
                "ANOTHER_READ",
                AccessPermissionDefinitions.localizedName(Permission.valueOf("ANOTHER_READ"), messages)
            );
        }
    }
}
