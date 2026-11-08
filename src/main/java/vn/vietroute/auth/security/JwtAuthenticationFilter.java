package vn.vietroute.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.api_journal.filter.ApiJournalFilter.ApiJournalState;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Xác minh JWT rồi đọc lại tài khoản/quyền để khóa user hoặc logout có hiệu lực ngay. */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwt;

    private final UserRepo users;

    private final ApiResponses errors;

    private final AuthCookies cookies;

    public JwtAuthenticationFilter(JwtService jwt, UserRepo users, ApiResponses errors, AuthCookies cookies) {
        this.jwt = jwt;
        this.users = users;
        this.errors = errors;
        this.cookies = cookies;
    }

    /** Whitelist chỉ là các luồng tự phục vụ hoặc dữ liệu công khai; không nhận URL tùy ý từ người dùng. */
    private static boolean accountSetupRoute(String path, String method) {
        return (
            WebRoutePolicy.publicApi(path, method) ||
            Set.of(
                Routes.ACCOUNT,
                Routes.HOME,
                Routes.LOGIN,
                Routes.AUTH + Routes.ME_ACTION,
                Routes.AUTH + Routes.PASSWORD_ACTION,
                Routes.AUTH + Routes.AUTH_CAPABILITIES
            ).contains(path)
        );
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
                User user = users.load(Long.valueOf(decoded.getSubject()));
                // Không tin role trong JWT: trạng thái/quyền/tokenVersion hiện tại luôn lấy lại từ DB.
                if (
                    user == null ||
                    user.getId() == SystemActor.ID ||
                    !user.isActive() ||
                    version == null ||
                    user.getTokenVersion() != version.longValue()
                ) {
                    throw new IllegalArgumentException("Token revoked");
                }
                UserPrincipal principal = new UserPrincipal(user, null);
                // Quyền chức năng lấy từ mapping DB mỗi request; thay quyền role có hiệu lực ngay, không chờ JWT hết hạn.
                List<SimpleGrantedAuthority> authorities = user
                    .getTargets()
                    .stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        user.isMustChangePassword() ? List.of() : authorities
                    )
                );
                SecurityContextHolder.setContext(context);
                request.setAttribute(Http.VERIFIED_USER_ATTRIBUTE, user.getId());
                request.setAttribute(Http.JOURNAL_USER_ATTRIBUTE, user.getId());
                // Phiên chưa hoàn tất thiết lập chỉ được tự phục vụ; không miễn xác thực theo role hay username.
                if (user.isMustChangePassword() && !accountSetupRoute(path, request.getMethod())) {
                    if (path.startsWith(Routes.API_ROOT)) errors.write(
                        request,
                        response,
                        ErrorCode.PASSWORD_CHANGE_REQUIRED
                    );
                    else response.sendRedirect(request.getContextPath() + Routes.ACCOUNT);
                    return;
                }
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
                // Lỗi DB lúc kiểm JWT xảy ra trước MVC: chỉ giữ SQLState đã lọc để tra cứu sau khi DB hồi phục.
                ApiJournalState.failure(request, exception);
                errors.write(request, response, exception.getErrorCode());
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
