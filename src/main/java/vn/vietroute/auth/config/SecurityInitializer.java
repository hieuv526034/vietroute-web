package vn.vietroute.auth.config;

import org.springframework.security.web.context.AbstractSecurityWebApplicationInitializer;

/** Đăng ký springSecurityFilterChain cho WAR thuần, không cần Spring Boot hoặc web.xml. */
public class SecurityInitializer extends AbstractSecurityWebApplicationInitializer {}
