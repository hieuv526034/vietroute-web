package vn.vietroute.common.monitoring.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalEntry;
import vn.vietroute.common.monitoring.service.ApiJournalState;
import vn.vietroute.common.monitoring.service.ApiJournalStore;
import vn.vietroute.common.web.filter.RequestTraceFilter;

/** Đặt trước Security để thấy 401/403/429/503; không buffer body/response hoặc log header/URL thô. */
@RequiredArgsConstructor
public final class ApiJournalFilter extends OncePerRequestFilter {

    private final ApiJournalStore store;
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
                    for (var entry : values.entrySet()) {
                        String value = String.valueOf(entry.getValue());
                        if (value.matches(Regex.JOURNAL_PATH_VALUE)) replay = replay.replace(
                            "{" + entry.getKey() + "}",
                            value
                        );
                    }
                }
                var e = new ApiJournalEntry(
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
                    state.sqlState
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
                    case "page", "size" -> value.matches(Regex.JOURNAL_QUERY_NUMBER);
                    case "lang" -> value.equals("vi") || value.equals("en");
                    case "status" -> value.matches(Regex.JOURNAL_JOB_STATUS);
                    default -> false;
                };
                if (valid) safe.put(key, value);
            } catch (IllegalArgumentException ignored) {
                /* URL sai không làm hỏng luồng chẩn đoán. */
            }
        }
        return Map.copyOf(safe);
    }
}
