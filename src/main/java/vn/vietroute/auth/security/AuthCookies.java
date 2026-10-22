package vn.vietroute.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Cookie JWT chỉ được JavaScript sử dụng gián tiếp; hỗ trợ cả context ROOT và /vietroute. */
@Component
@RequiredArgsConstructor
public class AuthCookies {

    private final AppSettings settings;

    public void set(HttpServletRequest request, HttpServletResponse response, String token) {
        write(request, response, token, settings.getAccessTokenLifetime());
    }

    public void clear(HttpServletRequest request, HttpServletResponse response) {
        write(request, response, "", Duration.ZERO);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, String token, Duration age) {
        String context = request.getContextPath();
        response.addHeader(
            HttpHeaders.SET_COOKIE,
            ResponseCookie.from(Http.AUTH_COOKIE, token)
                .httpOnly(true)
                .secure(settings.isCookieSecure())
                .sameSite(Http.SAME_SITE_LAX)
                .path(context.isEmpty() ? Routes.HOME : context)
                .maxAge(age)
                .build()
                .toString()
        );
    }
}
