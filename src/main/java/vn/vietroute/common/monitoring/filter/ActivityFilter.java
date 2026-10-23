package vn.vietroute.common.monitoring.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.monitoring.service.ActivityService;
import vn.vietroute.common.traffic.ClientAddressResolver;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Bao ngoài Security để thấy cả 401/403/429; user ID chỉ lấy attribute do JWT đã kiểm DB đặt vào. */
public final class ActivityFilter extends OncePerRequestFilter {

    private final ActivityService activity;

    private final AppSettings app;

    private final ClientAddressResolver addresses;

    public ActivityFilter(ActivityService activity, AppSettings app, TrafficSettings traffic) {
        this.activity = activity;
        this.app = app;
        addresses = new ClientAddressResolver(traffic.getProxyCidrs());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return (
            !activity.enabled() ||
            WebRoutePolicy.skipsTrafficGuard(path) ||
            path.equals(Routes.METRICS) ||
            path.equals(Routes.AUTH_CSRF) ||
            path.equals(Routes.OPENAPI) ||
            path.startsWith(Routes.DOCS) ||
            path.startsWith(Routes.ACTIVITY)
        );
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String visitor =
            request.getCookies() == null
                ? null
                : Arrays.stream(request.getCookies())
                      .filter(cookie -> Http.VISITOR_COOKIE.equals(cookie.getName()))
                      .map(Cookie::getValue)
                      .findFirst()
                      .orElse(null);
        if (!activity.validCookie(visitor)) {
            String id = UUID.randomUUID().toString();
            visitor = id + "." + activity.hash("cookie:" + id);
            response.addHeader(
                HttpHeaders.SET_COOKIE,
                ResponseCookie.from(Http.VISITOR_COOKIE, visitor)
                    .httpOnly(true)
                    .secure(app.isCookieSecure())
                    .sameSite(Http.SAME_SITE_LAX)
                    .path(request.getContextPath().isEmpty() ? Routes.HOME : request.getContextPath())
                    .maxAge(Duration.ofDays(7))
                    .build()
                    .toString()
            );
        }
        try {
            chain.doFilter(request, response);
        } finally {
            Object verified = request.getAttribute(Http.VERIFIED_USER_ATTRIBUTE);
            activity.record(
                visitor,
                addresses.resolve(request),
                verified instanceof Long id ? id : null,
                response.getStatus()
            );
        }
    }
}
