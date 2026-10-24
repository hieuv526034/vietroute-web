package vn.vietroute.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import vn.vietroute.auth.dto.response.AccessCapabilityResponse;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessCapabilities;
import vn.vietroute.auth.security.AccessNavigation;
import vn.vietroute.auth.security.AccessPolicyInterceptor;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.AuthNavigation;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

/** Model layout dùng chung: base path, locale và thông tin principal an toàn, không chứa token/hash. */
@ControllerAdvice
public class PageModelAdvice {

    private final ObjectProvider<AccessResourceSynchronizer> mapping;

    private final MessageSource messages;

    public PageModelAdvice(ObjectProvider<AccessResourceSynchronizer> mapping, MessageSource messages) {
        this.messages = messages;
        this.mapping = mapping;
    }

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        // JSON/health không render header: tránh dựng model/menu và quét metadata cho mọi request API.
        Object handler = request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE);
        if (
            handler instanceof HandlerMethod method &&
            (AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RestController.class) ||
                AnnotatedElementUtils.hasAnnotation(method.getMethod(), ResponseBody.class))
        ) return;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal user = auth != null && auth.getPrincipal() instanceof UserPrincipal value ? value : null;
        model.addAttribute("basePath", request.getContextPath());
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("currentLocale", new SupportedLocaleResolver().resolveLocale(request).getLanguage());
        model.addAttribute("currentUser", user);
        model.addAttribute("workspacePath", AuthNavigation.workspace(user));
        List<AccessPolicyResponse> policies = AccessPolicyInterceptor.snapshot(request);
        var capabilities = AccessCapabilities.compute(mapping.getObject().inventory(), auth, policies);
        Map<String, Boolean> pages = capabilities
            .stream()
            .collect(Collectors.toMap(AccessCapabilityResponse::function, AccessCapabilityResponse::view));
        model.addAttribute(
            "functionNavigation",
            AccessNavigation.build(
                pages,
                request.getRequestURI().substring(request.getContextPath().length()),
                new SupportedLocaleResolver().resolveLocale(request),
                messages
            )
        );
        model.addAttribute("catalogAllowed", pages.getOrDefault(Functions.CATALOG, false));
        model.addAttribute("mediaAllowed", pages.getOrDefault(Functions.MEDIA, false));
        model.addAttribute("jobsAllowed", pages.getOrDefault(Functions.JOBS, false));
        model.addAttribute("accessAllowed", pages.getOrDefault(Functions.ACCESS, false));
        model.addAttribute("monitoringAllowed", pages.getOrDefault(Functions.MONITORING, false));
        model.addAttribute("configurationAllowed", pages.getOrDefault(Functions.CONFIGURATION, false));
        model.addAttribute("journalAllowed", pages.getOrDefault(Functions.JOURNAL, false));
        model.addAttribute("accountAllowed", user != null);
        model.addAttribute("adminUser", user != null && user.roles().contains(Role.ADMIN));
        // Chỉ đánh dấu trang hiện hành; các cờ giao diện không thay thế kiểm quyền ở service.
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Map<String, Boolean> navigation = Map.of(
            "admin",
            path.equals(Routes.ADMIN),
            "account",
            path.equals(Routes.ACCOUNT),
            "docs",
            path.equals(Routes.DOCS),
            "media",
            path.equals(Routes.MEDIA_PAGE),
            "monitoring",
            path.equals(Routes.MONITORING_PAGE),
            "jobs",
            path.equals(Routes.JOBS_PAGE),
            "access",
            path.equals(Routes.ACCESS_PAGE),
            "configuration",
            path.equals(Routes.CONFIGURATION_PAGE),
            "journal",
            path.equals(Routes.API_JOURNAL_PAGE)
        );
        model.addAttribute("navigation", navigation);
        model.addAttribute(
            "operationsActive",
            navigation.get("monitoring") ||
                navigation.get("jobs") ||
                navigation.get("configuration") ||
                navigation.get("journal")
        );
    }
}
