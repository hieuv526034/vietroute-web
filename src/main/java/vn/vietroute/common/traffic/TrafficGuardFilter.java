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
import java.util.Locale;
import java.util.concurrent.Semaphore;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.response.ApiErrorWriter;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Bảo vệ trước CSRF/JWT/DB: quota IP, giới hạn request/BCrypt đồng thời và kích thước JSON. */
public final class TrafficGuardFilter extends OncePerRequestFilter {

    private final TrafficSettings settings;

    private final RateLimitStore store;

    private final ApiErrorWriter errors;

    private final ClientAddressResolver addresses;

    private final Semaphore concurrent;

    private final Semaphore logins;

    private final Semaphore uploads = new Semaphore(1);

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
        if (WebRoutePolicy.skipsTrafficGuard(path)) {
            chain.doFilter(request, response);
            return;
        }
        boolean login = WebRoutePolicy.credentialMutation(path);
        boolean upload =
            path.equals(Routes.MEDIA) &&
            request.getMethod().equals("POST") &&
            request.getContentType() != null &&
            request.getContentType().toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
        try {
            String ip = hash(addresses.resolve(request));
            RateLimitStore.Decision decision = store.consume("all:" + ip, settings.getRequestsPerMinute(), 60);
            if (decision.allowed() && login) {
                decision = store.consume("login:" + ip, settings.getLoginsPerMinute(), 60);
            }
            if (!decision.allowed()) {
                response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(decision.retryAfterSeconds()));
                errors.write(request, response, ErrorCode.RATE_LIMITED);
                return;
            }
        } catch (RuntimeException exception) {
            // Không log exception Redis vì thông báo driver có thể chứa URL credentials.
            response.setHeader(HttpHeaders.RETRY_AFTER, "2");
            errors.write(request, response, ErrorCode.SERVICE_BUSY);
            return;
        }
        if (!concurrent.tryAcquire()) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            errors.write(request, response, ErrorCode.SERVICE_BUSY);
            return;
        }
        boolean loginSlot = !login || logins.tryAcquire();
        boolean uploadSlot = !upload || uploads.tryAcquire();
        try {
            if (!loginSlot || !uploadSlot) {
                response.setHeader(HttpHeaders.RETRY_AFTER, "1");
                errors.write(request, response, ErrorCode.SERVICE_BUSY);
                return;
            }
            // Multipart không được đọc trước/getInputStream rồi bọc lại: Tomcat cần stream gốc để parse part.
            // Giới hạn chunked do MultipartConfigElement chốt; chỉ một upload được giải nén mỗi instance.
            if (upload && request.getContentLengthLong() > MediaSettings.MAX_REQUEST_BYTES) {
                errors.write(request, response, ErrorCode.PAYLOAD_TOO_LARGE);
                return;
            }
            if (!upload && path.startsWith(Routes.API_ROOT) && !request.getMethod().matches(Regex.SAFE_HTTP_METHOD)) {
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
            if (upload && uploadSlot) {
                uploads.release();
            }
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
