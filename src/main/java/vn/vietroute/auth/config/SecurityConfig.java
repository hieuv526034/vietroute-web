package vn.vietroute.auth.config;

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessMethodExpressionHandler;
import vn.vietroute.auth.security.AuthCookies;
import vn.vietroute.auth.security.JwtAuthenticationFilter;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.monitoring.filter.ActivityFilter;
import vn.vietroute.common.monitoring.filter.ApiJournalFilter;
import vn.vietroute.common.monitoring.filter.HttpMetricsFilter;
import vn.vietroute.common.monitoring.filter.MetricsScrapeFilter;
import vn.vietroute.common.monitoring.service.ActivityService;
import vn.vietroute.common.monitoring.service.ApiJournalStore;
import vn.vietroute.common.monitoring.service.ApplicationMetrics;
import vn.vietroute.common.traffic.TrafficGuardFilter;
import vn.vietroute.common.web.response.ApiErrorWriter;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Security chạy ở root context, dùng matcher tường minh và không tạo HTTP session đăng nhập. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /** Cấu hình sớm ở root context: mọi service dùng chung một biểu thức và MethodInvocation thật. */
    @Bean
    public static MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        return new AccessMethodExpressionHandler();
    }

    /** Chain riêng chỉ đọc metrics; không chạy JWT/DB/quota nên vẫn scrape được khi DB hoặc quota lỗi. */
    @Bean
    @Order(1)
    public SecurityFilterChain metricsFilterChain(
        HttpSecurity http,
        MonitoringSettings settings,
        PrometheusMeterRegistry registry
    ) throws Exception {
        http.securityMatcher(matcher(Routes.METRICS))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(AbstractHttpConfigurer::disable)
            .csrf(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().denyAll())
            .addFilterBefore(new MetricsScrapeFilter(settings, registry), AuthorizationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
        HttpSecurity http,
        JwtService jwt,
        UserRepository users,
        ApiErrorWriter errors,
        AuthCookies cookies,
        AppSettings settings,
        TrafficGuardFilter traffic,
        ApplicationMetrics metrics,
        ActivityService activity,
        TrafficSettings trafficSettings,
        ApiJournalStore journal,
        Clock clock
    ) throws Exception {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath(Routes.HOME);
        csrf.setCookieCustomizer(builder -> builder.secure(settings.isCookieSecure()).sameSite(Http.SAME_SITE_LAX));
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // Mỗi cấu hình chỉ gọi disable: dùng method reference để tránh lambda dư và cảnh báo IDE.
            .requestCache(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .csrf(config ->
                config
                    .csrfTokenRepository(csrf)
                    .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                    // Bearer không tự được trình duyệt gửi như cookie nên không cần CSRF token.
                    .ignoringRequestMatchers(request -> {
                        String value = request.getHeader(HttpHeaders.AUTHORIZATION);
                        return value != null && value.startsWith(Http.BEARER_PREFIX);
                    })
            )
            .authorizeHttpRequests(auth ->
                auth
                    .requestMatchers(
                        WebRoutePolicy.publicMatchers()
                            .stream()
                            .map(SecurityConfig::matcher)
                            .toArray(PathPatternRequestMatcher[]::new)
                    )
                    .permitAll()
                    .requestMatchers(
                        PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, Routes.CATALOG_MATCHER)
                    )
                    .permitAll()
                    // Trang danh mục và API kiểm quyền hành động tại method/service; custom role từ DB cũng dùng được.
                    .requestMatchers(matcher(Routes.ADMIN_API_MATCHER))
                    .authenticated()
                    .requestMatchers(matcher(Routes.ADMIN_MATCHER))
                    .authenticated()
                    .anyRequest()
                    .authenticated()
            )
            .exceptionHandling(config ->
                config
                    .authenticationEntryPoint((request, response, exception) -> {
                        if (
                            request
                                .getRequestURI()
                                .substring(request.getContextPath().length())
                                .startsWith(Routes.API_ROOT)
                        ) {
                            errors.write(request, response, ErrorCode.UNAUTHENTICATED);
                        } else {
                            response.sendRedirect(request.getContextPath() + Routes.LOGIN);
                        }
                    })
                    .accessDeniedHandler((request, response, exception) ->
                        errors.write(
                            request,
                            response,
                            exception instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.ACCESS_DENIED
                        )
                    )
            )
            .headers(headers ->
                headers.contentSecurityPolicy(csp ->
                    csp.policyDirectives(
                        "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: https://res.cloudinary.com; font-src 'self'; " +
                            "connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"
                    )
                )
            )
            .addFilterBefore(new HttpMetricsFilter(metrics), SecurityContextHolderFilter.class)
            // Metrics bao cả thời gian I/O ghi log; journal vẫn đứng trước CSRF/JWT/authorization.
            .addFilterAfter(new ActivityFilter(activity, settings, trafficSettings), HttpMetricsFilter.class)
            .addFilterAfter(new ApiJournalFilter(journal, clock), ActivityFilter.class)
            .addFilterBefore(traffic, CsrfFilter.class)
            .addFilterBefore(
                new JwtAuthenticationFilter(jwt, users, errors, cookies),
                UsernamePasswordAuthenticationFilter.class
            );
        return http.build();
    }

    private static PathPatternRequestMatcher matcher(String path) {
        return PathPatternRequestMatcher.withDefaults().matcher(path);
    }
}
