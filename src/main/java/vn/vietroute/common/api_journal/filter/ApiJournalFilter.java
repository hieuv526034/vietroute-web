package vn.vietroute.common.api_journal.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import vn.vietroute.common.api_journal.dto.response.ApiJournalDetail;
import vn.vietroute.common.api_journal.dto.response.ApiJournalEntry;
import vn.vietroute.common.api_journal.service.ApiJournalService;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.filter.RequestTraceFilter;

/** Đặt trước Security để thấy 401/403/429/503; không buffer body/response hoặc log header/URL thô. */
@RequiredArgsConstructor
public final class ApiJournalFilter extends OncePerRequestFilter {

    private final ApiJournalService store;

    private final Clock clock;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (
            !store.enabled() ||
            !path.startsWith(Routes.API_ROOT) ||
            path.equals(Routes.API_JOURNAL) ||
            path.startsWith(Routes.API_JOURNAL + "/")
        ) {
            chain.doFilter(request, response);
            return;
        }
        Instant started = Instant.now(clock);
        long nano = System.nanoTime();
        ApiJournalState state = ApiJournalState.start(request);
        String trace = RequestTraceFilter.ensure(request, response);
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            // Chẩn đoán là best effort: lỗi ghi nhận không được che lỗi gốc hoặc làm request nghiệp vụ thất bại.
            try {
                Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
                String endpoint =
                    pattern != null && pattern.toString().startsWith(Routes.API_ROOT) ? pattern.toString() : "UNMAPPED";
                Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
                String controller =
                    handler instanceof HandlerMethod method
                        ? method.getBeanType().getSimpleName() + "#" + method.getMethod().getName()
                        : "PRE_CONTROLLER";
                String method = request.getMethod().matches(Regex.API_METHOD) ? request.getMethod() : "OTHER";
                String kind =
                    request.getContentType() == null
                        ? "OTHER"
                        : request.getContentType().startsWith("application/json")
                          ? "JSON"
                          : request.getContentType().startsWith("multipart/form-data")
                            ? "MULTIPART"
                            : "OTHER";
                String replay = endpoint;
                Object variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (variables instanceof Map<?, ?> values) {
                    for (Entry<?, ?> entry : values.entrySet()) {
                        String value = String.valueOf(entry.getValue());
                        if (value.matches(Regex.JOURNAL_PATH_VALUE)) replay = replay.replace(
                            "{" + entry.getKey() + "}",
                            value
                        );
                    }
                }
                ApiJournalEntry e = new ApiJournalEntry(
                    UUID.randomUUID(),
                    started,
                    trace,
                    method,
                    endpoint,
                    controller,
                    failed ? 500 : response.getStatus(),
                    Math.max(0, (System.nanoTime() - nano) / 1_000_000),
                    state.code,
                    state.messageKey,
                    state.diagnostic,
                    state.sqlState,
                    request.getAttribute(Http.JOURNAL_USER_ATTRIBUTE) instanceof Long actor && actor > 0 ? actor : null,
                    null
                );
                store.add(
                    new ApiJournalDetail(
                        e,
                        store.instanceId(),
                        replay,
                        query(request.getQueryString()),
                        state.requestBody,
                        state.responseBody,
                        kind
                    )
                );
            } catch (RuntimeException ignored) {
                /* Không log exception vì có thể chứa dữ liệu chưa lọc. */
            }
        }
    }

    private Map<String, String> query(String raw) {
        Map<String, String> safe = new LinkedHashMap<>();
        if (raw == null || raw.length() > 2048) return Map.of();
        for (String item : raw.split("&", 33)) {
            String[] pair = item.split("=", 2);
            if (pair.length != 2) continue;
            try {
                String key = URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                boolean valid = switch (key) {
                    case
                        "page",
                        "size",
                        "carrierId",
                        "vehicleId",
                        "driverId",
                        "routeId",
                        "tripId",
                        "pickupStopId",
                        "dropoffStopId",
                        "provinceId" -> value.matches(Regex.JOURNAL_QUERY_NUMBER);
                    case "lang" -> value.equals("vi") || value.equals("en");
                    case "status" -> value.matches(Regex.JOURNAL_ENUM);
                    default -> false;
                };
                if (valid) safe.put(key, value);
            } catch (IllegalArgumentException ignored) {
                /* URL sai không làm hỏng luồng chẩn đoán. */
            }
        }
        return Map.copyOf(safe);
    }

    public static final class ApiJournalState {

        public String requestBody = "{\"capture\":\"OMITTED_OR_NO_BODY\"}";
        public String responseBody = "{\"capture\":\"NO_ENVELOPE\"}";
        public String code = "UNKNOWN";

        public String messageKey = "";

        public String diagnostic = "HTTP_STATUS_ONLY";

        public String sqlState = "";

        private ApiJournalState() {}

        public static ApiJournalState start(HttpServletRequest request) {
            ApiJournalState state = new ApiJournalState();
            request.setAttribute(Http.JOURNAL_STATE, state);
            return state;
        }

        public static ApiJournalState get(HttpServletRequest request) {
            Object value = request.getAttribute(Http.JOURNAL_STATE);
            return value instanceof ApiJournalState state ? state : null;
        }

        /** Chỉ giữ SQLState chuẩn, không message/cause/SQL/parameter của driver. */
        public static void failure(HttpServletRequest request, Exception exception) {
            ApiJournalState state = get(request);
            if (state == null) return;
            Throwable cause = exception;
            for (int i = 0; i < 8 && cause != null; i++, cause = cause.getCause()) {
                if (cause instanceof SQLException sql) {
                    String code = sql.getSQLState();
                    if (code != null && code.matches(Regex.SQL_STATE)) {
                        state.sqlState = code;
                        state.diagnostic = switch (code.substring(0, 2)) {
                            case "08" -> "DB_CONNECTION";
                            case "28" -> "DB_AUTHENTICATION";
                            case "42" -> "DB_SCHEMA_OR_QUERY";
                            case "53" -> "DB_CAPACITY";
                            case "40" -> "DB_TRANSACTION";
                            case "55" -> "DB_LOCK_OR_STATE";
                            default -> "DB_FAILURE";
                        };
                        return;
                    }
                    state.diagnostic = "DB_FAILURE";
                }
            }
            if (state.sqlState.isEmpty() && !state.diagnostic.equals("DB_FAILURE")) state.diagnostic =
                "APPLICATION_ERROR";
        }
    }
}
