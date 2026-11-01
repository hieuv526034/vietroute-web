package vn.vietroute.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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
import vn.vietroute.auth.dto.response.FunctionDTO;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.FunctionService;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;
import vn.vietroute.common.web.response.PageBreadcrumbs;

/** Model layout dùng chung: base path, locale và thông tin principal an toàn, không chứa token/hash. */
@ControllerAdvice
public class PageModelAdvice {

    private final FunctionService menus;
    private final MessageSource messages;

    public PageModelAdvice(FunctionService menus, MessageSource messages) {
        this.menus = menus;
        this.messages = messages;
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
        model.addAttribute("workspacePath", user == null ? Routes.LOGIN : Routes.DASHBOARD);
        String current = request.getRequestURI().substring(request.getContextPath().length());
        Locale locale = new SupportedLocaleResolver().resolveLocale(request);
        List<FunctionDTO> menu = menus.navigation(user, current, locale);
        model.addAttribute("functionNavigation", menu);
        model.addAttribute("breadcrumbs", PageBreadcrumbs.build(menu, current, locale, messages));
        Set<String> paths = menu
            .stream()
            .map(item -> item.path())
            .collect(Collectors.toSet());
        model.addAttribute("catalogAllowed", paths.contains(Routes.ADMIN));
        model.addAttribute("mediaAllowed", paths.contains(Routes.MEDIA_PAGE));
        model.addAttribute("jobsAllowed", paths.contains(Routes.JOBS_PAGE));
        model.addAttribute("accessAllowed", paths.contains(Routes.ACCESS_PAGE));
        model.addAttribute("monitoringAllowed", paths.contains(Routes.MONITORING_PAGE));
        model.addAttribute("configurationAllowed", paths.contains(Routes.CONFIGURATION_PAGE));
        model.addAttribute("journalAllowed", paths.contains(Routes.API_JOURNAL_PAGE));
        model.addAttribute("accountAllowed", user != null);
        model.addAttribute("adminUser", UserPrincipal.globalData());
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
