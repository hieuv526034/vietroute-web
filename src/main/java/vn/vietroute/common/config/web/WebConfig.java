package vn.vietroute.common.config.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.multipart.support.StandardServletMultipartResolver;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;
import vn.vietroute.auth.security.AccessPolicyInterceptor;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.filter.RequestTraceFilter;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

/** Servlet context quản lý MVC, Thymeleaf, locale và Swagger; kế thừa cấu hình MVC để ghi đè locale mặc định. */
@Configuration
// WAR có root/servlet context riêng: advisor của root chỉ bảo vệ service, MVC phải bật để bảo vệ controller.
@EnableMethodSecurity
@ComponentScan(
    basePackages = { "vn.vietroute.common", "vn.vietroute.auth", "vn.vietroute.business" },
    useDefaultFilters = false,
    includeFilters = @ComponentScan.Filter(
        type = FilterType.ANNOTATION,
        classes = { Controller.class, ControllerAdvice.class }
    )
)
public class WebConfig extends DelegatingWebMvcConfiguration {

    private final AccessManagementService access;

    private final MessageSource messages;

    private final ObjectMapper mapper;

    private final SupportedLocaleResolver locales;

    public WebConfig(
        MessageSource messages,
        ObjectMapper mapper,
        SupportedLocaleResolver locales,
        AccessManagementService access
    ) {
        this.access = access;
        this.messages = messages;
        this.mapper = mapper;
        this.locales = locales;
    }

    /** Chỉ parse multipart/form-data; giới hạn byte do Servlet registration áp dụng trước controller. */
    @Bean
    public StandardServletMultipartResolver multipartResolver() {
        StandardServletMultipartResolver resolver = new StandardServletMultipartResolver();
        resolver.setStrictServletCompliance(true);
        return resolver;
    }

    /** Ghi đè bean locale mặc định của MVC; vẫn dùng cùng resolver với security/filter ở root. */
    @Bean
    @Override
    public LocaleResolver localeResolver() {
        return locales;
    }

    @Bean
    public SpringResourceTemplateResolver templateResolver(ApplicationContext context) {
        SpringResourceTemplateResolver resolver = new SpringResourceTemplateResolver();
        resolver.setApplicationContext(context);
        resolver.setPrefix("/WEB-INF/views/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        return resolver;
    }

    @Bean
    public SpringTemplateEngine templateEngine(SpringResourceTemplateResolver resolver) {
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        // Template của đồ án không cần sinh class SpEL động; giữ chế độ diễn giải dễ kiểm soát trên RAM nhỏ.
        engine.setEnableSpringELCompiler(false);
        return engine;
    }

    @Bean
    public ThymeleafViewResolver viewResolver(SpringTemplateEngine engine) {
        ThymeleafViewResolver resolver = new ThymeleafViewResolver();
        resolver.setTemplateEngine(engine);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        return resolver;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(Routes.ASSET_MATCHER).addResourceLocations(Routes.ASSETS);
        registry
            .addResourceHandler(Routes.WEBJAR_MATCHER)
            .addResourceLocations("classpath:/META-INF/resources/webjars/");
    }

    @Bean
    public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages);
        return validator;
    }

    @Override
    public Validator getValidator() {
        return validator();
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        // Giữ thứ tự converter chuẩn để String/byte[] không bị JSON encode thêm một lớp.
        for (int index = 0; index < converters.size(); index++) {
            if (converters.get(index) instanceof MappingJackson2HttpMessageConverter) {
                converters.set(index, new MappingJackson2HttpMessageConverter(mapper));
                return;
            }
        }
        converters.add(new MappingJackson2HttpMessageConverter(mapper));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        super.addInterceptors(registry);
        registry.addInterceptor(new AccessPolicyInterceptor(access));
        registry.addInterceptor(
            new HandlerInterceptor() {
                @Override
                public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                    RequestTraceFilter.ensure(request, response);
                    String explicit = SupportedLocaleResolver.explicitLanguage(request);
                    if (explicit != null && !SupportedLocaleResolver.supported(explicit)) {
                        throw new BusinessException(ErrorCode.LOCALE_UNSUPPORTED);
                    }
                    response.setHeader(
                        HttpHeaders.CONTENT_LANGUAGE,
                        new SupportedLocaleResolver().resolveLocale(request).getLanguage()
                    );
                    return true;
                }
            }
        );
    }
}
