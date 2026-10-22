package vn.vietroute.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Stream;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiErrorWriter;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Xác minh JWT rồi đọc lại tài khoản/quyền để khóa user hoặc logout có hiệu lực ngay. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    private final UserRepository users;

    private final ApiErrorWriter errors;

    private final AuthCookies cookies;

    public JwtAuthenticationFilter(JwtService jwt, UserRepository users, ApiErrorWriter errors, AuthCookies cookies) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
        this.cookies = cookies;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (WebRoutePolicy.skipsJwt(path)) {
            chain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        // Bearer được ưu tiên khi có header; chỉ dùng cookie HttpOnly khi client không gửi Authorization.
        String token = null;
        if (authorization != null) {
            if (
                !authorization.startsWith(Http.BEARER_PREFIX) || authorization.length() <= Http.BEARER_PREFIX.length()
            ) {
                errors.write(request, response, ErrorCode.TOKEN_INVALID);
                return;
            }
            token = authorization.substring(Http.BEARER_PREFIX.length());
        } else if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                .filter(value -> Http.AUTH_COOKIE.equals(value.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
        }
        if (token != null) {
            try {
                Jwt decoded = jwt.decode(token);
                Object versionClaim = decoded.getClaim("tokenVersion");
                Number version = versionClaim instanceof Number value ? value : null;
                UserAccount user = users.findById(Long.valueOf(decoded.getSubject()));
                // Không tin role trong JWT: trạng thái/quyền/tokenVersion hiện tại luôn lấy lại từ DB.
                if (
                    user == null || !user.isActive() || version == null || user.getTokenVersion() != version.longValue()
                ) {
                    throw new IllegalArgumentException("Token revoked");
                }
                UserPrincipal principal = new UserPrincipal(
                    user.getId(),
                    user.getUsername(),
                    user.getRoles(),
                    user.getTokenVersion()
                );
                // Quyền chức năng lấy từ mapping DB mỗi request; thay quyền role có hiệu lực ngay, không chờ JWT hết hạn.
                var authorities = Stream.concat(
                    user
                        .getRoles()
                        .stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())),
                    user
                        .getPermissions()
                        .stream()
                        .map(permission -> new SimpleGrantedAuthority(permission.name()))
                ).toList();
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)
                );
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException exception) {
                // Token cookie cũ được xóa để trang công khai vẫn mở được; API cần đăng nhập phải trả AUTH004.
                SecurityContextHolder.clearContext();
                cookies.clear(request, response);
                boolean publicPage = WebRoutePolicy.recoversInvalidCookie(path, request.getMethod());
                if (authorization != null || !publicPage) {
                    errors.write(request, response, ErrorCode.TOKEN_INVALID);
                    return;
                }
            } catch (BusinessException exception) {
                errors.write(request, response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
