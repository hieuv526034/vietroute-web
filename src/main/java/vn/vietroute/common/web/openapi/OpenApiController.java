package vn.vietroute.common.web.openapi;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResult;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Tạo OpenAPI từ mapping Spring MVC và @Operation/@Schema, không đưa Spring Boot vào WAR. */
@RestController
@AccessFunction(group = "API")
public class OpenApiController {

    private final RequestMappingHandlerMapping mappings;

    public OpenApiController(RequestMappingHandlerMapping mappings) {
        this.mappings = mappings;
    }

    @GetMapping(value = Routes.OPENAPI, produces = Http.JSON_UTF8)
    @Operation(hidden = true, summary = "Cung cấp tài liệu OpenAPI")
    public JsonNode document(HttpServletRequest request) {
        OpenAPI api = new OpenAPI()
            .openapi("3.0.3")
            .info(
                new Info()
                    .title("VietRoute API")
                    .version("0.2.0")
                    .description(
                        "API vi/en. Chọn lang hoặc X-Locale. Mọi kết quả dùng envelope code/messageKey/traceId. " +
                            "Cookie mode: gọi " +
                            Routes.AUTH_CSRF +
                            " trước POST; Bearer mode: Authorize bằng JWT."
                    )
            )
            .servers(
                List.of(new Server().url(request.getContextPath().isEmpty() ? Routes.HOME : request.getContextPath()))
            )
            .components(
                new Components()
                    .addSecuritySchemes(
                        "bearerAuth",
                        new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    )
                    .addSecuritySchemes(
                        "cookieAuth",
                        new SecurityScheme()
                            .type(SecurityScheme.Type.APIKEY)
                            .in(SecurityScheme.In.COOKIE)
                            .name(Http.AUTH_COOKIE)
                    )
            )
            .paths(new Paths());
        Schema<?> errorSchema = schema(new TypeReference<ApiResponse<Void>>() {}.getType(), api);
        mappings.getHandlerMethods().forEach((mapping, handler) -> {
            var method = handler.getMethod();
            Operation description = method.getAnnotation(Operation.class);
            // Endpoint mới cần @Operation để xuất hiện trong tài liệu; không đưa view/static route vào API.
            if (description == null || description.hidden()) {
                return;
            }
            Tag tag = handler.getBeanType().getAnnotation(Tag.class);
            for (String path : mapping.getPatternValues()) {
                if (!path.startsWith(Routes.API_ROOT)) {
                    continue;
                }
                for (RequestMethod verb : mapping.getMethodsCondition().getMethods()) {
                    var operation = OpenApiModels.operation(
                        description.summary(),
                        description.description(),
                        handler.getBeanType().getSimpleName() + "_" + method.getName(),
                        tag == null ? "API" : tag.name()
                    );
                    operation.addParametersItem(
                        new Parameter()
                            .name(Http.LANGUAGE_PARAMETER)
                            .in("query")
                            .description("Ngôn ngữ, ưu tiên cao nhất")
                            .schema(new StringSchema()._enum(List.of("vi", "en")))
                    );
                    operation.addParametersItem(
                        new Parameter()
                            .name(Http.LOCALE_HEADER)
                            .in("header")
                            .description("Ngôn ngữ nếu không có lang")
                            .schema(new StringSchema()._enum(List.of("vi", "en")))
                    );
                    // var giữ đúng kiểu reflection Parameter, tránh trùng tên với model Parameter của Swagger.
                    for (var parameter : method.getParameters()) {
                        if (
                            parameter.isAnnotationPresent(ModelAttribute.class) &&
                            mapping
                                .getConsumesCondition()
                                .getConsumableMediaTypes()
                                .stream()
                                .anyMatch(media -> media.getType().equals("multipart"))
                        ) {
                            OpenApiModels.multipartBody(operation, schema(parameter.getParameterizedType(), api));
                        } else if (parameter.isAnnotationPresent(RequestBody.class)) {
                            OpenApiModels.jsonBody(
                                operation,
                                parameter.getAnnotation(RequestBody.class).required(),
                                schema(parameter.getParameterizedType(), api)
                            );
                        } else if (
                            parameter.isAnnotationPresent(PathVariable.class) ||
                            parameter.isAnnotationPresent(RequestParam.class)
                        ) {
                            PathVariable pv = parameter.getAnnotation(PathVariable.class);
                            RequestParam rp = parameter.getAnnotation(RequestParam.class);
                            String name = pv != null ? pv.value() : rp.value();
                            if (name.isBlank()) {
                                name = parameter.getName();
                            }
                            operation.addParametersItem(
                                new Parameter()
                                    .name(name)
                                    .in(pv != null ? "path" : "query")
                                    .required(pv != null || rp.required())
                                    .schema(schema(parameter.getParameterizedType(), api))
                            );
                        } else if (parameter.isAnnotationPresent(RequestHeader.class)) {
                            // Header bắt buộc như Idempotency-Key phải có ô nhập trên Swagger Try it out.
                            RequestHeader header = parameter.getAnnotation(RequestHeader.class);
                            String name = header.value().isBlank() ? parameter.getName() : header.value();
                            operation.addParametersItem(
                                new Parameter()
                                    .name(name)
                                    .in("header")
                                    .required(header.required())
                                    .schema(schema(parameter.getParameterizedType(), api))
                            );
                        } else if (parameter.getType() == PageRequest.class) {
                            operation.addParametersItem(
                                new Parameter()
                                    .name("page")
                                    .in("query")
                                    .description("Trang bắt đầu từ 0")
                                    .schema(
                                        new IntegerSchema()
                                            ._default(0)
                                            .minimum(BigDecimal.ZERO)
                                            .maximum(BigDecimal.valueOf(1000000))
                                    )
                            );
                            operation.addParametersItem(
                                new Parameter()
                                    .name("size")
                                    .in("query")
                                    .description("Số mục/trang")
                                    .schema(
                                        new IntegerSchema()
                                            ._default(20)
                                            .minimum(BigDecimal.ONE)
                                            .maximum(BigDecimal.valueOf(100))
                                    )
                            );
                        }
                    }
                    boolean publicRoute = WebRoutePolicy.publicApi(path, verb.name());
                    if (!publicRoute) {
                        operation
                            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                            .addSecurityItem(new SecurityRequirement().addList("cookieAuth"));
                    }
                    Type returnType = method.getGenericReturnType();
                    if (returnType instanceof ParameterizedType type && type.getRawType() == ApiResult.class) {
                        // ApiResult<T> rút gọn chữ ký Java; hợp đồng JSON vẫn là ApiResponse<T> đầy đủ.
                        returnType = ResolvableType.forClassWithGenerics(
                            ApiResponse.class,
                            ResolvableType.forType(type.getActualTypeArguments()[0])
                        ).getType();
                    } else if (
                        returnType instanceof ParameterizedType type && type.getRawType() == ResponseEntity.class
                    ) {
                        returnType = type.getActualTypeArguments()[0];
                    }
                    // @ResponseStatus mô tả HTTP thành công; ResponseEntity của base vẫn quyết định status khi lỗi.
                    ResponseStatus declared = AnnotatedElementUtils.findMergedAnnotation(method, ResponseStatus.class);
                    String status = declared == null ? "200" : Integer.toString(declared.value().value());
                    OpenApiModels.response(operation, status, "Thành công", schema(returnType, api), null);
                    Map<String, String> failures = Map.ofEntries(
                        Map.entry("400", "Dữ liệu/locale không hợp lệ"),
                        Map.entry("401", "Chưa đăng nhập hoặc token/credentials sai"),
                        Map.entry("403", "Không có quyền hoặc CSRF sai"),
                        Map.entry("404", "Không tìm thấy"),
                        Map.entry("405", "HTTP method không được hỗ trợ"),
                        Map.entry("406", "Định dạng phản hồi không được hỗ trợ"),
                        Map.entry("409", "Trùng dữ liệu/phiên bản cũ"),
                        Map.entry("413", "Body vượt kích thước cho phép"),
                        Map.entry("415", "Media type không được hỗ trợ"),
                        Map.entry("429", "Vượt quota hoặc tài khoản tạm khóa; xem code và Retry-After"),
                        Map.entry("500", "Lỗi nội bộ có traceId"),
                        Map.entry("503", "Database/Redis chưa sẵn sàng, hệ thống bận hoặc email chưa cấu hình")
                    );
                    failures.forEach((code, text) ->
                        OpenApiModels.response(
                            operation,
                            code,
                            text,
                            errorSchema,
                            errorExample(Integer.parseInt(code), path)
                        )
                    );
                    PathItem item = api.getPaths().computeIfAbsent(path, ignored -> new PathItem());
                    item.operation(PathItem.HttpMethod.valueOf(verb.name()), operation);
                }
            }
        });
        // Swagger dùng mapper riêng để serialize đúng $ref/schema của đặc tả OpenAPI.
        return Json.mapper().valueToTree(api);
    }

    /** Ví dụ lỗi phải đúng mã/status và success=false, tránh để Swagger tự sinh giá trị thành công cho schema chung. */
    private static Map<String, Object> errorExample(int status, String path) {
        ErrorCode code = switch (status) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.ACCESS_DENIED;
            case 404 -> ErrorCode.NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406 -> ErrorCode.NOT_ACCEPTABLE;
            case 409 -> ErrorCode.CONFLICT;
            case 413 -> ErrorCode.PAYLOAD_TOO_LARGE;
            case 415 -> ErrorCode.UNSUPPORTED_MEDIA;
            case 429 -> ErrorCode.RATE_LIMITED;
            case 503 -> ErrorCode.DATABASE_UNAVAILABLE;
            default -> ErrorCode.INTERNAL_ERROR;
        };
        Map<String, Object> example = new LinkedHashMap<>();
        example.put("success", false);
        example.put("code", code.getCode());
        example.put("messageKey", code.getMessageKey());
        example.put("message", "Thông báo tương ứng messageKey theo locale đã chọn");
        example.put("data", null);
        example.put("errors", List.of());
        example.put("timestamp", "2026-10-21T10:00:00Z");
        example.put("path", path);
        example.put("traceId", "caeae4ef-f5a4-4b16-9a34-06e50d6be073");
        example.put("locale", "vi");
        return example;
    }

    private static Schema<?> schema(Type type, OpenAPI api) {
        var resolved = ModelConverters.getInstance().resolveAsResolvedSchema(
            new AnnotatedType(type).resolveAsRef(true)
        );
        if (resolved.referencedSchemas != null) {
            resolved.referencedSchemas.forEach(api.getComponents()::addSchemas);
        }
        return resolved.schema == null ? new ObjectSchema() : resolved.schema;
    }
}
