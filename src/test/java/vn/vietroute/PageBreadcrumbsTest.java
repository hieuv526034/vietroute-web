package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import vn.vietroute.auth.dto.response.NavigationItemResponse;
import vn.vietroute.common.web.response.PageBreadcrumbs;

/** Kiểm breadcrumb dùng cây đã lọc quyền, có link nhóm và giữ đường dẫn tương thích địa lý. */
class PageBreadcrumbsTest {

    @Test
    void groupsLinkOnlyToVisibleChildrenAndCurrentPageIsLast() {
        var messages = new StaticMessageSource();
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        var leaf = new NavigationItemResponse("Trips", "/operations/trips", "", true, List.of());
        var group = new NavigationItemResponse("Operations", null, "", true, List.of(leaf));
        var result = PageBreadcrumbs.build(List.of(group), "/operations/trips", Locale.ENGLISH, messages);
        assertEquals(
            List.of("Home", "Operations", "Trips"),
            result.stream().map(NavigationItemResponse::name).toList()
        );
        assertEquals(
            List.of("/", "/operations/trips", "/operations/trips"),
            result.stream().map(NavigationItemResponse::path).toList()
        );
        assertFalse(result.get(1).active());
        assertEquals(true, result.get(2).active());
        assertEquals(List.of(), PageBreadcrumbs.build(List.of(), "/operations/trips", Locale.ENGLISH, messages));
    }

    @Test
    void publicPagesAndLegacyLocationUseTranslatedLabelsWithoutRawUrl() {
        var messages = new StaticMessageSource();
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        messages.addMessage("ui.journey.search", Locale.ENGLISH, "Find a trip");
        assertEquals(
            "Find a trip",
            PageBreadcrumbs.build(List.of(), "/journeys", Locale.ENGLISH, messages).get(1).name()
        );
        var places = new NavigationItemResponse("Places", "/catalogs/locations", "", true, List.of());
        assertEquals(
            "/catalogs/locations",
            PageBreadcrumbs.build(List.of(places), "/operations/locations", Locale.ENGLISH, messages).get(1).path()
        );
        assertEquals(List.of(), PageBreadcrumbs.build(List.of(), "/", Locale.ENGLISH, messages));
    }
}
