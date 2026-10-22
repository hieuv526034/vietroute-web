package vn.vietroute.common.config;

import jakarta.servlet.Filter;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletRegistration;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.support.AbstractAnnotationConfigDispatcherServletInitializer;
import vn.vietroute.common.web.RequestTraceFilter;

/** Điểm khởi tạo WAR trong Tomcat: root hạ tầng, servlet MVC và filter encoding/trace. */
public class WebApplicationInitializer extends AbstractAnnotationConfigDispatcherServletInitializer {

    /** Tomcat chốt kích thước kể cả request chunked; file tạm bị MVC xóa khi request kết thúc. */
    @Override
    protected void customizeRegistration(ServletRegistration.Dynamic registration) {
        registration.setMultipartConfig(
            new MultipartConfigElement("", MediaSettings.MAX_FILE_BYTES, MediaSettings.MAX_REQUEST_BYTES, 0)
        );
    }

    @Override
    protected Class<?>[] getRootConfigClasses() {
        return new Class<?>[] { RootConfig.class };
    }

    @Override
    protected Class<?>[] getServletConfigClasses() {
        return new Class<?>[] { WebConfig.class };
    }

    @Override
    protected String[] getServletMappings() {
        return new String[] { "/" };
    }

    @Override
    protected Filter[] getServletFilters() {
        return new Filter[] { new CharacterEncodingFilter("UTF-8", true), new RequestTraceFilter() };
    }
}
