package vn.vietroute.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.*;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.ApiErrorWriter;
import vn.vietroute.common.config.AppSettings;

/** Security chạy ở root context, dùng matcher tường minh và không tạo HTTP session đăng nhập. */
@Configuration @EnableWebSecurity @EnableMethodSecurity
public class SecurityConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwt, UserRepository users,
                                                  ApiErrorWriter errors, AuthCookies cookies, AppSettings settings) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");
        csrf.setCookieCustomizer(builder -> builder.secure(settings.isCookieSecure()).sameSite("Lax"));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .csrf(config -> config.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Bearer không tự được trình duyệt gửi như cookie nên không cần CSRF token.
                        .ignoringRequestMatchers(request -> {
                            String value = request.getHeader("Authorization"); return value != null && value.startsWith("Bearer ");
                        }))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(matcher("/"), matcher("/health"), matcher("/login"), matcher("/assets/**"),
                                matcher("/webjars/**"), matcher("/docs"), matcher("/docs/**"), matcher("/v3/api-docs"),
                                matcher("/api/v1/auth/login"), matcher("/api/v1/auth/csrf"), matcher("/api/v1/auth/logout")).permitAll()
                        .requestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/api/v1/catalogs/**")).permitAll()
                        .requestMatchers(matcher("/api/v1/admin/**"), matcher("/admin/**")).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(config -> config
                        .authenticationEntryPoint((request, response, exception) -> {
                            if (request.getRequestURI().substring(request.getContextPath().length()).startsWith("/api/")) {
                                errors.write(request, response, ErrorCode.UNAUTHENTICATED);
                            } else { response.sendRedirect(request.getContextPath() + "/login"); }
                        })
                        .accessDeniedHandler((request, response, exception) -> errors.write(request, response,
                                exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED)))
                .headers(headers -> headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; font-src 'self'; "
                                + "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'")))
                .addFilterBefore(new JwtAuthenticationFilter(jwt, users, errors, cookies), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
    private static PathPatternRequestMatcher matcher(String path) { return PathPatternRequestMatcher.withDefaults().matcher(path); }
}
