package vn.vietroute.common.monitoring.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.monitoring.service.ApplicationMetrics;
import vn.vietroute.common.web.filter.RequestTraceFilter;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Đặt trước Security/TrafficGuard; không đưa URL/param/header/IP hoặc userId vào nhãn và log. */
public final class HttpMetricsFilter extends OncePerRequestFilter {

    private static final Logger LOG = Logger.getLogger(HttpMetricsFilter.class.getName());

    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

    private final ApplicationMetrics metrics;

    private final AtomicLong lastLogNanos = new AtomicLong();

    public HttpMetricsFilter(ApplicationMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (WebRoutePolicy.skipsTrafficGuard(path)) {
            chain.doFilter(request, response);
            return;
        }
        String trace = RequestTraceFilter.ensure(request, response);
        String route = group(path);
        String method = METHODS.contains(request.getMethod()) ? request.getMethod() : "OTHER";
        long start = System.nanoTime();
        boolean failed = true;
        metrics.begin();
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            long elapsed = Math.max(0, System.nanoTime() - start);
            int status = failed ? 500 : response.getStatus();
            metrics.finish(route, method, status, elapsed);
            // Metrics luôn ghi đủ; log mẫu tối đa một dòng/giây/node tránh log storm khi quá tải.
            long now = System.nanoTime();
            long last = lastLogNanos.get();
            if (
                (status >= 500 || elapsed >= 1_000_000_000L) &&
                (last == 0 || now - last >= 1_000_000_000L) &&
                lastLogNanos.compareAndSet(last, now)
            ) {
                LOG.info(
                    "event=http_completed route=" +
                        route +
                        " method=" +
                        method +
                        " status=" +
                        status +
                        " durationMs=" +
                        elapsed / 1_000_000 +
                        " traceId=" +
                        trace
                );
            }
        }
    }

    /** Nhóm cố định chặn cardinality khi bot gửi hàng triệu URL/ID khác nhau. */
    private static String group(String path) {
        if (path.equals(Routes.AUTH) || path.startsWith(Routes.AUTH + "/")) {
            return "auth";
        }
        if (path.startsWith(Routes.CATALOG_PREFIX)) {
            return "catalog";
        }
        if (path.equals(Routes.MEDIA) || path.startsWith(Routes.MEDIA + "/")) {
            return "media";
        }
        if (path.startsWith(Routes.ADMIN_API + "/")) {
            return "admin";
        }
        return path.startsWith(Routes.API_ROOT) ? "other_api" : "page";
    }
}
