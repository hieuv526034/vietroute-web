package vn.vietroute.common.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.context.MessageSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import vn.vietroute.common.web.*;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.templateresolver.SpringResourceTemplateResolver;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templatemode.TemplateMode;

@Configuration
@ComponentScan(basePackages = {"vn.vietroute.common", "vn.vietroute.auth", "vn.vietroute.business"},
        useDefaultFilters = false, includeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION,
        classes = {Controller.class, ControllerAdvice.class}))
public class WebConfig extends org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration {
    private final MessageSource messages;
    private final ObjectMapper mapper;
    private final SupportedLocaleResolver locales;
    public WebConfig(MessageSource messages, ObjectMapper mapper, SupportedLocaleResolver locales) {
        this.messages = messages; this.mapper = mapper; this.locales = locales;
    }
    /** Ghi đè bean locale mặc định của MVC; vẫn dùng cùng resolver với security/filter ở root. */
    @Bean @Override public org.springframework.web.servlet.LocaleResolver localeResolver() { return locales; }
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
        engine.setEnableSpringELCompiler(true);
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
        registry.addResourceHandler("/assets/**").addResourceLocations("/assets/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
    }
    @Bean public LocalValidatorFactoryBean validator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages); return validator;
    }
    @Override public Validator getValidator() { return validator(); }
    @Override public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
        converters.add(0, new MappingJackson2HttpMessageConverter(mapper));
    }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        super.addInterceptors(registry);
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                RequestTraceFilter.ensure(request, response);
                String explicit = SupportedLocaleResolver.explicitLanguage(request);
                if (explicit != null && !SupportedLocaleResolver.supported(explicit)) {
                    throw new BusinessException(ErrorCode.LOCALE_UNSUPPORTED);
                }
                response.setHeader("Content-Language", new SupportedLocaleResolver().resolveLocale(request).getLanguage());
                return true;
            }
        });
    }
}
