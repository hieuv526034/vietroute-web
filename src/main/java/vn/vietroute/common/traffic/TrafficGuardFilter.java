package vn.vietroute.common.traffic;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.config.TrafficSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.ApiErrorWriter;

/** Bảo vệ trước CSRF/JWT/DB: quota IP, giới hạn request/BCrypt đồng thời và kích thước JSON. */
public final class TrafficGuardFilter extends OncePerRequestFilter {

    private final TrafficSettings settings;

    private final RateLimitStore store;

    private final ApiErrorWriter errors;

    private final ClientAddressResolver addresses;

    private final Semaphore concurrent;

    private final Semaphore logins;

    public TrafficGuardFilter(TrafficSettings settings, RateLimitStore store, ApiErrorWriter errors) {
        this.settings = settings;
        this.store = store;
        this.errors = errors;
        addresses = new ClientAddressResolver(settings.getProxyCidrs());
        concurrent = new Semaphore(settings.getMaxConcurrent());
        logins = new Semaphore(settings.getMaxConcurrentLogins());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.equals("/health") || path.startsWith("/assets/") || path.startsWith("/webjars/")) {
            chain.doFilter(request, response);
            return;
        }
        boolean login = path.equals("/api/v1/auth/login");
        try {
            String ip = hash(addresses.resolve(request));
            RateLimitStore.Decision decision = store.consume("all:" + ip, settings.getRequestsPerMinute(), 60);
            if (decision.allowed() && login) {
                decision = store.consume("login:" + ip, settings.getLoginsPerMinute(), 60);
            }
            if (!decision.allowed()) {
                response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
                errors.write(request, response, ErrorCode.RATE_LIMITED);
                return;
            }
        } catch (RuntimeException exception) {
            // Không log exception Redis vì thông báo driver có thể chứa URL credentials.
            response.setHeader("Retry-After", "2");
            errors.write(request, response, ErrorCode.SERVICE_BUSY);
            return;
        }
        if (!concurrent.tryAcquire()) {
            response.setHeader("Retry-After", "1");
            errors.write(request, response, ErrorCode.SERVICE_BUSY);
            return;
        }
        boolean loginSlot = !login || logins.tryAcquire();
        try {
            if (!loginSlot) {
                response.setHeader("Retry-After", "1");
                errors.write(request, response, ErrorCode.SERVICE_BUSY);
                return;
            }
            if (path.startsWith("/api/") && !request.getMethod().matches("GET|HEAD|OPTIONS")) {
                if (request.getContentLengthLong() > settings.getMaxBodyBytes()) {
                    errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
                    return;
                }
                byte[] body = request.getInputStream().readNBytes(settings.getMaxBodyBytes() + 1);
                if (body.length > settings.getMaxBodyBytes()) {
                    errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
                    return;
                }
                request = new BufferedBodyRequest(request, body);
            }
            chain.doFilter(request, response);
        } finally {
            if (login && loginSlot) {
                logins.release();
            }
            concurrent.release();
        }
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
