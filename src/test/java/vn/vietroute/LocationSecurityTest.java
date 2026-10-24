package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.location.dto.request.LocationLookupRequest;
import vn.vietroute.business.location.dto.request.LocationSearchRequest;
import vn.vietroute.business.location.dto.request.ProvinceLookupRequest;
import vn.vietroute.business.location.entity.Location;
import vn.vietroute.business.location.entity.Province;
import vn.vietroute.business.location.repository.LocationRepository;
import vn.vietroute.business.location.service.LocationService;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Hai context WAR và guard production; SQL/filter/cạnh tranh/version kiểm thêm trên PostgreSQL Docker CI. */
class LocationSecurityTest {

    private static AnnotationConfigWebApplicationContext root;
    private static AnnotationConfigWebApplicationContext child;
    private static MockMvc mvc;
    private static ObjectMapper json;

    @BeforeAll
    static void start() {
        var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
        root = new AnnotationConfigWebApplicationContext();
        root.setServletContext(web);
        root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, Fixture.class);
        root.refresh();
        child = new AnnotationConfigWebApplicationContext();
        child.setParent(root);
        child.setServletContext(web);
        child.register(WebConfig.class);
        child.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
        json = root.getBean(ObjectMapper.class);
    }

    @AfterAll
    static void close() {
        if (child != null) child.close();
        if (root != null) root.close();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publicLookupUsesMinimalProjectionAndEnglishNamesWithoutLogin() throws Exception {
        for (String route : List.of(Routes.PROVINCES, Routes.LOCATIONS)) {
            var response = mvc.perform(get(route).param("lang", "en")).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            var first = json.readTree(response.getContentAsString()).at("/data/content/0");
            assertTrue(first.path("name").asText().startsWith("Demo"));
            for (String hidden : List.of("active", "createdBy", "updatedBy", "version", "nameVi", "provinceActive"))
                assertFalse(first.has(hidden), hidden);
        }
        assertEquals(
            400,
            mvc.perform(get(Routes.LOCATIONS).param("provinceId", "0")).andReturn().getResponse().getStatus()
        );
        assertEquals(
            400,
            mvc.perform(get(Routes.PROVINCES).param("size", "101")).andReturn().getResponse().getStatus()
        );
        assertEquals(
            400,
            mvc.perform(get(Routes.LOCATIONS).param("query", "bad\nquery")).andReturn().getResponse().getStatus()
        );
    }

    @Test
    void adminAndPermissionAreBothRequiredForUrlsAndDirectServiceCalls() throws Exception {
        var all = new String[] { "LOCATION_VIEW", "LOCATION_READ", "LOCATION_CREATE", "LOCATION_UPDATE" };
        var ordinary = actor(false, all);
        var admin = actor(true, "LOCATION_VIEW", "LOCATION_READ");
        for (String route : List.of(Routes.LOCATION_PAGE, Routes.ADMIN_PROVINCES, Routes.ADMIN_LOCATIONS)) {
            assertEquals(
                route.equals(Routes.LOCATION_PAGE) ? 302 : 401,
                mvc.perform(get(route)).andReturn().getResponse().getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(route).with(authentication(ordinary)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                403,
                mvc
                    .perform(get(route).with(authentication(actor(true))))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
            assertEquals(
                200,
                mvc
                    .perform(get(route).with(authentication(admin)))
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
        }
        SecurityContextHolder.getContext().setAuthentication(ordinary);
        assertThrows(AccessDeniedException.class, () ->
            root.getBean(LocationService.class).locations(new LocationSearchRequest(), Locale.ENGLISH)
        );
        assertFalse(Arrays.stream(Permission.values()).anyMatch(value -> value.name().startsWith("LOCATION_")));
        assertTrue(root.getBean(AccessRegistryRepository.class).builtIn("permissions", "LOCATION_CREATE"));
    }

    @Test
    void invalidCoordinatesReferencesAndImmutableFieldsFailBeforeTransaction() throws Exception {
        String valid =
            "{\"code\":\"PLACE01\",\"provinceId\":1,\"nameVi\":\"Địa điểm\",\"nameEn\":\"Place\",\"address\":\"Địa chỉ\",\"latitude\":10,\"longitude\":106}";
        for (String body : List.of(
            valid.replace("\"latitude\":10", "\"latitude\":91"),
            valid.replace("\"longitude\":106", "\"longitude\":181"),
            valid.replace("\"latitude\":10", "\"latitude\":10.1234567"),
            valid.replace("\"provinceId\":1", "\"provinceId\":0"),
            valid.replace("\"nameVi\":\"Địa điểm\"", "\"nameVi\":\" \"")
        )) {
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.ADMIN_LOCATIONS)
                            .with(authentication(actor(true, "LOCATION_CREATE")))
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
        }
        assertEquals(
            403,
            mvc
                .perform(
                    post(Routes.ADMIN_LOCATIONS)
                        .with(authentication(actor(false, "LOCATION_CREATE")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(valid)
                )
                .andReturn()
                .getResponse()
                .getStatus()
        );
        String update = valid
            .replace("\"code\":\"PLACE01\",", "")
            .replace("\"provinceId\":1,", "\"provinceId\":2,\"version\":0,");
        assertEquals(
            400,
            mvc
                .perform(
                    put(Routes.ADMIN_LOCATIONS + "/1")
                        .with(authentication(actor(true, "LOCATION_UPDATE")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update)
                )
                .andReturn()
                .getResponse()
                .getStatus()
        );
    }

    @Test
    void openApiCoversInheritedQueryBodyAndPublicSecurityContract() throws Exception {
        var spec = json.readTree(mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse().getContentAsString());
        var operation = spec.path("paths").path(Routes.ADMIN_LOCATIONS).path("get");
        String parameters = operation.path("parameters").toString();
        for (String name : List.of("page", "size", "query", "active", "provinceId"))
            assertTrue(parameters.contains("\"name\":\"" + name + "\""), name);
        var publicRead = spec.path("paths").path(Routes.LOCATIONS).path("get");
        assertTrue(publicRead.path("security").isEmpty());
        var body = spec.path("components").path("schemas").path("LocationCreateRequest").path("properties");
        for (String name : List.of(
            "nameVi",
            "nameEn",
            "sortOrder",
            "active",
            "code",
            "provinceId",
            "latitude",
            "longitude",
            "address"
        ))
            assertTrue(body.has(name), name);
        assertFalse(body.has("actor"));
    }

    private static UsernamePasswordAuthenticationToken actor(boolean admin, String... codes) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(1L, "fixture", admin ? Set.of(Role.ADMIN) : Set.of(Role.OPERATOR), 0),
            null,
            Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()
        );
    }

    @Configuration
    static class Fixture {

        @Bean
        @Primary
        LocationRepository isolatedLocations() {
            return new LocationRepository(null) {
                @Override
                public PageResponse<Province> provinces(ProvinceLookupRequest page, Boolean active) {
                    return PageResponse.of(
                        List.of(
                            Province.builder()
                                .id(1L)
                                .code("DEMO01")
                                .nameVi("Tỉnh demo")
                                .nameEn("Demo province")
                                .active(true)
                                .createdAt(Instant.EPOCH)
                                .updatedAt(Instant.EPOCH)
                                .createdBy(9L)
                                .updatedBy(9L)
                                .build()
                        ),
                        page,
                        1
                    );
                }

                @Override
                public PageResponse<Location> locations(
                    LocationLookupRequest page,
                    Boolean active,
                    boolean publicOnly
                ) {
                    return PageResponse.of(
                        List.of(
                            Location.builder()
                                .id(2L)
                                .code("DEMO02")
                                .provinceId(1L)
                                .provinceNameVi("Tỉnh demo")
                                .provinceNameEn("Demo province")
                                .provinceActive(true)
                                .nameVi("Điểm demo")
                                .nameEn("Demo place")
                                .address("Địa chỉ demo")
                                .latitude(BigDecimal.TEN)
                                .longitude(BigDecimal.TEN)
                                .active(true)
                                .createdAt(Instant.EPOCH)
                                .updatedAt(Instant.EPOCH)
                                .createdBy(9L)
                                .updatedBy(9L)
                                .build()
                        ),
                        page,
                        1
                    );
                }
            };
        }
    }
}
