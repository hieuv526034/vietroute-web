package vn.vietroute.common.monitoring.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.UserRolesRequest;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogDeleteRequest;
import vn.vietroute.business.catalog.dto.request.CatalogUpdateRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.job.dto.request.RetryJobRequest;
import vn.vietroute.common.web.response.ApiResponse;

/** Projection theo hợp đồng tường minh; field/DTO mới mặc định bị bỏ, không blacklist secret theo tên. */
@Component
@RequiredArgsConstructor
public class ApiJournalProjection {

    public static final int MAX_SNAPSHOT = 4096;

    private final ObjectMapper mapper;

    private static final Set<Class<?>> REQUEST_TYPES = Set.of(
        CatalogCreateRequest.class,
        CatalogUpdateRequest.class,
        CatalogDeleteRequest.class,
        RolePermissionsRequest.class,
        UserRolesRequest.class,
        RetryJobRequest.class
    );
    private static final Set<String> NUMBERS = Set.of(
        "id",
        "sortOrder",
        "version",
        "page",
        "size",
        "totalElements",
        "totalPages"
    );
    private static final Set<String> FLAGS = Set.of("active", "hasNext", "hasPrevious");

    private static final Set<String> ENUMS = Set.of("type", "roles", "permissions");

    public boolean supports(Class<?> type) {
        return REQUEST_TYPES.contains(type);
    }

    /** Không capture auth/mail/media/access response/PII, kể cả tên field không giống secret. */
    public String request(Object value) {
        if (value == null || !supports(value.getClass())) return "{\"capture\":\"BODY_OMITTED\"}";
        return encode(project(mapper.valueToTree(value)));
    }

    public String response(ApiResponse<?> response, String path) {
        ObjectNode result = mapper.createObjectNode();
        result.put("success", response.success());
        result.put("code", response.code());
        result.put("messageKey", response.messageKey());
        result.put("locale", response.locale());
        ArrayNode errors = result.putArray("errors");
        response
            .errors()
            .stream()
            .limit(10)
            .forEach(error -> {
                ObjectNode field = errors.addObject();
                field.put("field", error.field().matches(Regex.JOURNAL_FIELD) ? error.field() : "REDACTED");
                field.put("code", error.code());
                field.put("messageKey", error.messageKey());
            });
        Object data = response.data();
        if (
            (path.startsWith(Routes.CATALOG_PREFIX) || path.startsWith(Routes.ADMIN_CATALOGS)) &&
            data instanceof CatalogResponse
        ) {
            result.set("data", project(mapper.valueToTree(data)));
        } else if (path.startsWith(Routes.CATALOG_PREFIX) && data instanceof PageResponse<?> page) {
            ObjectNode summary = result.putObject("data");
            summary.put("totalElements", page.totalElements());
            summary.put("page", page.page());
            summary.put("size", page.size());
            ArrayNode content = summary.putArray("content");
            page.content()
                .stream()
                .limit(3)
                .filter(CatalogResponse.class::isInstance)
                .forEach(item -> content.add(project(mapper.valueToTree(item))));
            summary.put("capture", "FIRST_3_ITEMS_ONLY");
        } else result.put("data", "OMITTED_BY_POLICY");
        return encode(result);
    }

    /** Không giữ chuỗi tự do như tên, địa chỉ, message hay tham số; giữ cấu trúc và enum/số hợp lệ. */
    private JsonNode project(JsonNode node) {
        ObjectNode result = mapper.createObjectNode();
        node.properties()
            .stream()
            .limit(20)
            .forEach(entry -> {
                String key = entry.getKey();
                JsonNode value = entry.getValue();
                if (NUMBERS.contains(key) && value.isIntegralNumber()) result.set(key, value);
                else if (FLAGS.contains(key) && value.isBoolean()) result.set(key, value);
                else if (ENUMS.contains(key)) {
                    if (value.isArray()) {
                        ArrayNode array = result.putArray(key);
                        value
                            .valueStream()
                            .limit(32)
                            .forEach(item -> array.add(enumValue(item)));
                    } else result.put(key, enumValue(value));
                } else if (Set.of("code", "nameVi", "nameEn", "name").contains(key)) result.put(key, "[REDACTED]");
            });
        return result;
    }

    private String enumValue(JsonNode value) {
        return value.isTextual() && value.textValue().matches(Regex.JOURNAL_ENUM) ? value.textValue() : "[REDACTED]";
    }

    private String encode(JsonNode value) {
        String json = value.toString();
        return json.length() <= MAX_SNAPSHOT ? json : "{\"capture\":\"OMITTED_SIZE_LIMIT\"}";
    }
}
