package vn.vietroute.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.util.Arrays;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.constant.Constants;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.ApiErrorWriter;

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
        if (
            path.equals("/health") ||
            path.equals("/api/v1/auth/login") ||
            path.equals("/api/v1/auth/csrf") ||
            path.startsWith("/assets/") ||
            path.startsWith("/webjars/") ||
            path.startsWith("/docs") ||
            path.equals("/v3/api-docs")
        ) {
            chain.doFilter(request, response);
            return;
        }
        String authorization = request.getHeader("Authorization");
        String token = null;
        if (authorization != null) {
            if (!authorization.startsWith("Bearer ") || authorization.length() <= 7) {
                errors.write(request, response, ErrorCode.TOKEN_INVALID);
                return;
            }
            token = authorization.substring(7);
        } else if (request.getCookies() != null) {
            token = Arrays.stream(request.getCookies())
                .filter(value -> Constants.AUTH_COOKIE.equals(value.getName()))
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
                var authorities = user
                    .getRoles()
                    .stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                    .toList();
                var context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities)
                );
                SecurityContextHolder.setContext(context);
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                cookies.clear(request, response);
                boolean publicPage =
                    path.equals("/") ||
                    path.equals("/login") ||
                    path.equals("/api/v1/auth/logout") ||
                    ("GET".equals(request.getMethod()) && path.startsWith("/api/v1/catalogs/"));
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
