package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.business.catalog.repo.CategoryRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;
import vn.vietroute.support.TestJpa;

/** Hai context WAR và guard production; SQL/filter/cạnh tranh/version kiểm thêm trên PostgreSQL Docker CI. */
class CategorySecurityTest {

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
        for (String refType : List.of("PROVINCE", "LOCATION")) {
            var response = mvc
                .perform(get(Routes.CATEGORIES).param("refType", refType).param("lang", "en").param("parentId", "1"))
                .andReturn()
                .getResponse();
            assertEquals(200, response.getStatus());
            var first = json.readTree(response.getContentAsString()).at("/data/content/0");
            assertTrue(first.path("name").asText().startsWith("Demo"));
            for (String hidden : List.of("active", "createdBy", "updatedBy", "version", "nameVi", "provinceActive"))
                assertFalse(first.has(hidden), hidden);
        }
        assertEquals(
            400,
            mvc.perform(get(Routes.CATEGORIES).param("parentId", "0")).andReturn().getResponse().getStatus()
        );
        assertEquals(
            400,
            mvc.perform(get(Routes.CATEGORIES).param("size", "101")).andReturn().getResponse().getStatus()
        );
        assertEquals(
            400,
            mvc.perform(get(Routes.CATEGORIES).param("query", "bad\nquery")).andReturn().getResponse().getStatus()
        );
    }

    @Test
    void exactEndpointsWorkForCustomRolesAndRoleAloneNeverGrantsUrls() throws Exception {
        for (String route : List.of(Routes.CATEGORY_PAGE, Routes.ADMIN_CATEGORY_GROUPS, Routes.ADMIN_CATEGORIES)) {
            assertEquals(
                route.equals(Routes.CATEGORY_PAGE) ? 302 : 401,
                mvc.perform(get(route)).andReturn().getResponse().getStatus()
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
                    .perform(
                        get(route)
                            .param("refType", "PROVINCE")
                            .with(
                                authentication(
                                    actor(
                                        false,
                                        "GET " + Routes.CATEGORY_PAGE,
                                        "GET " + Routes.ADMIN_CATEGORY_GROUPS,
                                        "GET " + Routes.ADMIN_CATEGORIES
                                    )
                                )
                            )
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus()
            );
        }
    }

    @Test
    void invalidReferencesAndServerFieldsFailBeforeTransaction() throws Exception {
        String valid =
            "{\"code\":\"PLACE01\",\"parentId\":1,\"name\":\"Địa điểm\",\"refType\":\"LOCATION\",\"props\":{\"address\":\"Địa chỉ\",\"latitude\":10,\"longitude\":106}}";
        for (String body : List.of(
            valid.replace("\"parentId\":1", "\"parentId\":0"),
            valid.replace("\"name\":\"Địa điểm\"", "\"name\":\" \"")
        )) {
            assertEquals(
                400,
                mvc
                    .perform(
                        post(Routes.ADMIN_CATEGORIES)
                            .with(authentication(actor(true, "POST " + Routes.ADMIN_CATEGORIES)))
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
                    post(Routes.ADMIN_CATEGORIES)
                        .with(authentication(actor(false)))
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
            .replace("\"parentId\":1,", "\"scopeId\":2,\"version\":0,");
        assertEquals(
            400,
            mvc
                .perform(
                    put(Routes.ADMIN_CATEGORIES + "/1")
                        .with(authentication(actor(true, "PUT " + Routes.ADMIN_CATEGORIES + "/{id}")))
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
        var document = mvc
            .perform(get(Routes.OPENAPI).with(authentication(actor(true, "GET " + Routes.OPENAPI))))
            .andReturn()
            .getResponse();
        assertEquals(200, document.getStatus());
        var spec = json.readTree(document.getContentAsString());
        var operation = spec.path("paths").path(Routes.ADMIN_CATEGORIES).path("get");
        String parameters = operation.path("parameters").toString();
        for (String name : List.of("page", "size", "query", "active", "parentId"))
            assertTrue(parameters.contains("\"name\":\"" + name + "\""), name);
        var publicRead = spec.path("paths").path(Routes.CATEGORIES).path("get");
        assertTrue(publicRead.path("security").isEmpty());
        var body = spec.path("components").path("schemas").path("CategoryRequest").path("properties");
        for (String name : List.of("name", "props", "sortOrder", "active", "code", "refType", "parentId", "type"))
            assertTrue(body.has(name), name);
        assertFalse(body.has("actor"));
    }

    private static UsernamePasswordAuthenticationToken actor(boolean admin, String... codes) {
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(1L, "fixture", admin ? Set.of("ADMIN") : Set.of("OPERATOR"), 0),
            null,
            Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()
        );
    }

    @Configuration
    static class Fixture {

        @Bean
        @Primary
        CategoryRepo isolatedLocations() {
            return new CategoryRepo(TestJpa.MANAGER, new ObjectMapper()) {
                @Override
                public PageResponse<Category> search(
                    CategorySearch page,
                    String refType,
                    String type,
                    boolean publicOnly
                ) {
                    boolean location = "LOCATION".equals(refType);
                    return PageResponse.of(
                        List.of(
                            Category.builder()
                                .id(location ? 2L : 1L)
                                .code("DEMO01")
                                .type("VALUE")
                                .refType(refType)
                                .name(location ? "Điểm demo" : "Tỉnh demo")
                                .parentId(location ? 1L : null)
                                .props(Map.of("nameEn", location ? "Demo place" : "Demo province"))
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
