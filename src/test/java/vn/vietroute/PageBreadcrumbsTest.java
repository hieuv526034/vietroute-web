package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.common.web.advice.PageModelAdvice;

/** Kiểm breadcrumb dùng cây đã lọc quyền, có link nhóm và giữ đường dẫn tương thích địa lý. */
class PageBreadcrumbsTest {

    @Test
    void navigationGroupsKeepOnlyGrantedLinksAndUseBusinessOrder() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("ui.nav.group.catalog", Locale.ENGLISH, "Catalogs");
        messages.addMessage("ui.nav.group.transport", Locale.ENGLISH, "Routes and trips");
        messages.addMessage("ui.nav.group.other", Locale.ENGLISH, "Other");
        FunctionDTO trip = menu("Trips", "/operations/trips", "clock", true, List.of());
        FunctionDTO catalog = menu("Places", "/operations/categories", "catalog", false, List.of());
        FunctionDTO custom = menu("Custom page", "/extension", "menu", false, List.of());
        List<FunctionDTO> result = PageModelAdvice.group(List.of(trip, custom, catalog), Locale.ENGLISH, messages);
        assertEquals(List.of("Catalogs", "Routes and trips", "Other"), result.stream().map(FunctionDTO::name).toList());
        assertEquals(
            List.of(catalog, trip, custom),
            result
                .stream()
                .flatMap(value -> value.children().stream())
                .toList()
        );
        assertTrue(result.get(1).active());
        assertFalse(result.get(0).active());
        assertEquals(List.of(), PageModelAdvice.group(List.of(), Locale.ENGLISH, messages));
        assertTrue(result.stream().allMatch(value -> value.path() == null));
    }

    private static FunctionDTO menu(String name, String path, String icon, boolean active, List<FunctionDTO> children) {
        var value = new FunctionDTO();
        value.setName(name);
        value.setPath(path);
        value.setIcon(icon);
        value.setActive(active);
        value.setChildren(children);
        return value;
    }

    @Test
    void catalogMenuDropsLegacyOverviewLinkWithoutAddingOtherCatalogs() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("ui.nav.group.catalog", Locale.ENGLISH, "Catalogs");
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        FunctionDTO legacy = menu("Catalogs", "/admin", "menu", true, List.of());
        FunctionDTO locations = menu("Locations", "/operations/categories", "catalog", true, List.of());
        FunctionDTO vehicles = menu("Vehicle types", "/operations/categories", "catalog", false, List.of());
        List<FunctionDTO> result = PageModelAdvice.group(
            List.of(legacy, locations, vehicles),
            Locale.ENGLISH,
            messages
        );
        assertEquals(1, result.size());
        assertEquals(List.of(locations, vehicles), result.get(0).children());
        assertTrue(result.get(0).active());
        assertEquals(
            List.of("/", "/operations/categories", "/operations/categories"),
            PageModelAdvice.build(result, "/operations/categories", Locale.ENGLISH, messages)
                .stream()
                .map(FunctionDTO::path)
                .toList()
        );
        assertEquals(List.of(), PageModelAdvice.group(List.of(legacy), Locale.ENGLISH, messages));
        List<FunctionDTO> scoped = PageModelAdvice.group(List.of(legacy, vehicles), Locale.ENGLISH, messages);
        assertEquals(List.of(vehicles), scoped.get(0).children());
        assertFalse(scoped.get(0).active());
    }

    @Test
    void categoryNavigationHasOneGrantedDestination() {
        StaticMessageSource messages = new StaticMessageSource();
        messages.addMessage("ui.nav.group.catalog", Locale.ENGLISH, "Catalogs");
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        FunctionDTO category = menu("Categories", "/operations/categories", "catalog", true, List.of());
        List<FunctionDTO> grouped = PageModelAdvice.group(List.of(category), Locale.ENGLISH, messages);
        assertEquals(List.of(category), grouped.get(0).children());
        assertEquals(
            "/operations/categories",
            PageModelAdvice.build(grouped, "/operations/categories", Locale.ENGLISH, messages).get(2).path()
        );
        assertEquals(List.of(), PageModelAdvice.build(List.of(), "/operations/locations", Locale.ENGLISH, messages));
    }

    @Test
    void groupsLinkOnlyToVisibleChildrenAndCurrentPageIsLast() {
        var messages = new StaticMessageSource();
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        var leaf = menu("Trips", "/operations/trips", "", true, List.of());
        var group = menu("Operations", null, "", true, List.of(leaf));
        var result = PageModelAdvice.build(List.of(group), "/operations/trips", Locale.ENGLISH, messages);
        assertEquals(List.of("Home", "Operations", "Trips"), result.stream().map(FunctionDTO::name).toList());
        assertEquals(
            List.of("/", "/operations/trips", "/operations/trips"),
            result.stream().map(FunctionDTO::path).toList()
        );
        assertFalse(result.get(1).active());
        assertEquals(true, result.get(2).active());
        assertEquals(List.of(), PageModelAdvice.build(List.of(), "/operations/trips", Locale.ENGLISH, messages));
    }

    @Test
    void publicPagesAndLegacyLocationUseTranslatedLabelsWithoutRawUrl() {
        var messages = new StaticMessageSource();
        messages.addMessage("ui.home", Locale.ENGLISH, "Home");
        messages.addMessage("ui.journey.search", Locale.ENGLISH, "Find a trip");
        assertEquals(
            "Find a trip",
            PageModelAdvice.build(List.of(), "/journeys", Locale.ENGLISH, messages).get(1).name()
        );
        var places = menu("Places", "/operations/categories", "", true, List.of());
        assertEquals(
            "/operations/categories",
            PageModelAdvice.build(List.of(places), "/operations/categories", Locale.ENGLISH, messages).get(1).path()
        );
        assertEquals(List.of(), PageModelAdvice.build(List.of(), "/", Locale.ENGLISH, messages));
    }
}
