package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Kiểm hợp đồng mà Swagger/Postman thực sự nhận, không mở connection hoặc đọc .env production. */
class OpenApiContractTest {

    @Test
    void supportsSeparateListAndDetailCodesForFutureBusinessWithoutGrantingRead() {
        var list = Permission.valueOf("BOOKING_LIST");
        var detail = Permission.valueOf("BOOKING_DETAIL");
        assertEquals(Set.of(PermissionAction.LIST), list.actions());
        assertEquals(Set.of(PermissionAction.DETAIL), detail.actions());
        assertFalse(list.equals(detail));
        assertFalse(list.actions().contains(PermissionAction.READ));
    }

    @Test
    void documentsEveryApiWithInheritedQueryFieldsAndPayloads() throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).build();
            var json = root.getBean(ObjectMapper.class);
            var response = mvc.perform(get(Routes.OPENAPI)).andReturn().getResponse();
            assertEquals(200, response.getStatus());
            var spec = json.readTree(response.getContentAsString());
            Set<String> ids = new HashSet<>();
            var mapping = child.getBean(RequestMappingHandlerMapping.class);
            mapping.getHandlerMethods().forEach((route, handler) -> {
                for (String path : route.getPatternValues()) {
                    if (!path.startsWith(Routes.API_ROOT)) continue;
                    for (var verb : route.getMethodsCondition().getMethods()) {
                        var operation = spec.path("paths").path(path).path(verb.name().toLowerCase());
                        assertFalse(operation.isMissingNode(), path);
                        assertFalse(operation.path("summary").asText().isBlank(), path);
                        assertTrue(ids.add(operation.path("operationId").asText()), path);
                        for (var parameter : operation.path("parameters")) {
                            assertFalse(
                                parameter.path("description").asText().isBlank(),
                                path + " " + parameter.path("name")
                            );
                            assertFalse(parameter.path("schema").isMissingNode(), path);
                        }
                        if (operation.has("requestBody")) {
                            for (var media : operation.path("requestBody").path("content")) {
                                var schema = resolve(spec, media.path("schema"));
                                assertTrue(schema.path("properties").size() > 0, path);
                            }
                        }
                    }
                }
            });
            var schemas = spec.path("components").path("schemas");
            schemas.properties().forEach(entry ->
                entry
                    .getValue()
                    .path("properties")
                    .properties()
                    .forEach(field -> {
                        assertTrue(
                            !field.getValue().path("description").asText().isBlank() ||
                                !resolve(spec, field.getValue()).path("description").asText().isBlank(),
                            entry.getKey() + "." + field.getKey()
                        );
                    })
            );
            var resources = spec
                .path("paths")
                .path(Routes.ACCESS + Routes.ACCESS_RESOURCES)
                .path("get");
            for (String name : List.of("kind", "query", "page", "size"))
                assertFalse(parameter(resources, name).isMissingNode(), name);
            assertEquals(20, parameter(resources, "size").path("schema").path("default").asInt());
            assertEquals(160, parameter(resources, "query").path("schema").path("maxLength").asInt());
            assertFalse(parameter(resources, "kind").path("required").asBoolean());
            var users = spec
                .path("paths")
                .path(Routes.ACCESS + "/users")
                .path("get");
            assertFalse(parameter(users, "username").isMissingNode());
            var journal = spec.path("paths").path(Routes.API_JOURNAL).path("get");
            for (String name : List.of("traceId", "userId", "method", "errorsOnly", "page", "size"))
                assertFalse(parameter(journal, name).isMissingNode(), name);
            // Client được dùng mã registry mới, Swagger không khóa role/permission thành enum bootstrap.
            assertFalse(schemas.path("Permission").has("enum"));
            assertEquals("REPORT_READ", json.readValue("\"REPORT_READ\"", Permission.class).name());
        }
    }

    private static JsonNode parameter(JsonNode operation, String name) {
        return StreamSupport.stream(operation.path("parameters").spliterator(), false)
            .filter(item -> item.path("name").asText().equals(name))
            .findFirst()
            .orElse(operation.path("missing"));
    }

    private static JsonNode resolve(JsonNode spec, JsonNode schema) {
        return schema.has("$ref") ? spec.at(schema.path("$ref").asText().substring(1)) : schema;
    }
}
