package vn.vietroute.common.web;

import com.fasterxml.jackson.core.type.TypeReference;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.common.base.PageRequest;

/** Tạo OpenAPI từ mapping Spring MVC và @Operation/@Schema, không đưa Spring Boot vào WAR. */
@RestController
public class OpenApiController {
    private final RequestMappingHandlerMapping mappings;
    public OpenApiController(RequestMappingHandlerMapping mappings) { this.mappings = mappings; }
    @GetMapping(value = "/v3/api-docs", produces = "application/json;charset=UTF-8")
    public String document(HttpServletRequest request) throws Exception {
        OpenAPI api = new OpenAPI().openapi("3.0.3").info(new Info().title("VietRoute API").version("0.2.0")
                .description("API vi/en. Chọn lang hoặc X-Locale. Mọi kết quả dùng envelope code/messageKey/traceId. "
                        + "Cookie mode: gọi /api/v1/auth/csrf trước POST; Bearer mode: Authorize bằng JWT."))
                .servers(List.of(new Server().url(request.getContextPath().isEmpty() ? "/" : request.getContextPath())))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP)
                        .scheme("bearer").bearerFormat("JWT"))
                        .addSecuritySchemes("cookieAuth", new SecurityScheme().type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE).name("VIETROUTE_ACCESS")))
                .paths(new Paths());
        Schema<?> errorSchema = schema(new TypeReference<ApiResponse<Void>>() { }.getType(), api);
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            var method = handler.getMethod();
            Operation description = method.getAnnotation(Operation.class);
            // Endpoint mới cần @Operation để xuất hiện trong tài liệu; không đưa view/static route vào API.
            if (description == null || description.hidden()) { return; }
            Tag tag = handler.getBeanType().getAnnotation(Tag.class);
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith("/api/")) { continue; }
                for (RequestMethod verb : mapping.getMethodsCondition().getMethods()) {
                    var operation = new io.swagger.v3.oas.models.Operation().summary(description.summary()).description(description.description())
                            .operationId(handler.getBeanType().getSimpleName() + "_" + method.getName())
                            .tags(List.of(tag == null ? "API" : tag.name()));
                    operation.addParametersItem(new Parameter().name("lang").in("query").description("Ngôn ngữ, ưu tiên cao nhất")
                            .schema(new StringSchema()._enum(List.of("vi", "en"))));
                    operation.addParametersItem(new Parameter().name("X-Locale").in("header").description("Ngôn ngữ nếu không có lang")
                            .schema(new StringSchema()._enum(List.of("vi", "en"))));
                    for (java.lang.reflect.Parameter parameter : method.getParameters()) {
                        if (parameter.isAnnotationPresent(RequestBody.class)) {
                            operation.requestBody(new io.swagger.v3.oas.models.parameters.RequestBody().required(parameter.getAnnotation(RequestBody.class).required())
                                    .content(json(schema(parameter.getParameterizedType(), api))));
                        } else if (parameter.isAnnotationPresent(PathVariable.class) || parameter.isAnnotationPresent(RequestParam.class)) {
                            PathVariable pv = parameter.getAnnotation(PathVariable.class);
                            RequestParam rp = parameter.getAnnotation(RequestParam.class);
                            String name = pv != null ? pv.value() : rp.value();
                            if (name.isBlank()) { name = parameter.getName(); }
                            operation.addParametersItem(new Parameter().name(name).in(pv != null ? "path" : "query")
                                    .required(pv != null || rp.required()).schema(schema(parameter.getParameterizedType(), api)));
                        } else if (parameter.getType() == PageRequest.class) {
                            operation.addParametersItem(new Parameter().name("page").in("query").description("Trang bắt đầu từ 0")
                                    .schema(new IntegerSchema()._default(0).minimum(java.math.BigDecimal.ZERO).maximum(java.math.BigDecimal.valueOf(1000000))));
                            operation.addParametersItem(new Parameter().name("size").in("query").description("Số mục/trang")
                                    .schema(new IntegerSchema()._default(20).minimum(java.math.BigDecimal.ONE).maximum(java.math.BigDecimal.valueOf(100))));
                        }
                    }
                    boolean publicRoute = path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/csrf")
                            || path.equals("/api/v1/auth/logout") || (verb == RequestMethod.GET && path.startsWith("/api/v1/catalogs/"));
                    if (!publicRoute) {
                        operation.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                                .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
                    }
                    Type returnType = method.getGenericReturnType();
                    if (returnType instanceof ParameterizedType type && type.getRawType() == ResponseEntity.class) { returnType = type.getActualTypeArguments()[0]; }
                    var created = method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponse.class);
                    String status = created == null ? "200" : created.responseCode();
                    ApiResponses responses = new ApiResponses().addApiResponse(status, new io.swagger.v3.oas.models.responses.ApiResponse()
                            .description(created == null ? "Thành công" : created.description()).content(json(schema(returnType, api))));
                    Map<String, String> failures = Map.of("400", "Dữ liệu/locale không hợp lệ", "401", "Chưa đăng nhập hoặc token/credentials sai",
                            "403", "Không có quyền hoặc CSRF sai", "404", "Không tìm thấy", "409", "Trùng dữ liệu/phiên bản cũ",
                            "429", "Tài khoản tạm khóa", "500", "Lỗi nội bộ có traceId", "503", "Database chưa sẵn sàng");
                    failures.forEach((code, text) -> responses.addApiResponse(code,
                            new io.swagger.v3.oas.models.responses.ApiResponse().description(text).content(json(errorSchema))));
                    operation.responses(responses);
                    PathItem item = api.getPaths().computeIfAbsent(path, ignored -> new PathItem());
                    item.operation(PathItem.HttpMethod.valueOf(verb.name()), operation);
                }
            }
        });
        // Swagger dùng mapper riêng để serialize đúng $ref/schema của đặc tả OpenAPI.
        return Json.mapper().writeValueAsString(api);
    }
    private static Content json(Schema<?> schema) { return new Content().addMediaType("application/json", new MediaType().schema(schema)); }
    private static Schema<?> schema(Type type, OpenAPI api) {
        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true));
        if (resolved.referencedSchemas != null) { resolved.referencedSchemas.forEach(api.getComponents()::addSchemas); }
        return resolved.schema == null ? new ObjectSchema() : resolved.schema;
    }
}
