package vn.vietroute.common.monitoring.filter;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.constant.ApplicationConstants.Http;

/** Chỉ gắn vào security chain đúng một route; token read-only riêng, không tin cookie hoặc JWT tài khoản. */
public final class MetricsScrapeFilter extends OncePerRequestFilter {

    private final MonitoringSettings settings;

    private final PrometheusMeterRegistry registry;

    private final Semaphore scrapeSlot = new Semaphore(1);

    public MetricsScrapeFilter(MonitoringSettings settings, PrometheusMeterRegistry registry) {
        this.settings = settings;
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        response.setHeader(HttpHeaders.CACHE_CONTROL, Http.NO_STORE);
        response.setContentType(Http.TEXT_UTF8);
        if (!settings.isScrapeEnabled()) {
            response.setStatus(404);
        } else if (!settings.accepts(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
            response.setStatus(401);
        } else if (!request.getMethod().equals("GET")) {
            response.setHeader(HttpHeaders.ALLOW, "GET");
            response.setStatus(405);
        } else if (!scrapeSlot.tryAcquire()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            response.setStatus(503);
        } else {
            try {
                response.setContentType(Http.PROMETHEUS);
                response.getWriter().write(registry.scrape());
            } finally {
                scrapeSlot.release();
            }
        }
    }
}
