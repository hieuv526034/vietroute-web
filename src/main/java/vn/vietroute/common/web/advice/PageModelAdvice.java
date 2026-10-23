package vn.vietroute.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AuthNavigation;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

/** Model layout dùng chung: base path, locale và thông tin principal an toàn, không chứa token/hash. */
@ControllerAdvice
public class PageModelAdvice {

    @ModelAttribute
    public void common(Model model, HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserPrincipal user = auth != null && auth.getPrincipal() instanceof UserPrincipal value ? value : null;
        model.addAttribute("basePath", request.getContextPath());
        model.addAttribute("currentPath", request.getRequestURI());
        model.addAttribute("currentLocale", new SupportedLocaleResolver().resolveLocale(request).getLanguage());
        model.addAttribute("currentUser", user);
        model.addAttribute("workspacePath", AuthNavigation.workspace(user));
        model.addAttribute(
            "catalogAllowed",
            menuAllowed(request, auth, Routes.ADMIN) &&
                (allowed(auth, Permission.CATALOG_READ) ||
                    allowed(auth, Permission.CATALOG_CREATE) ||
                    allowed(auth, Permission.CATALOG_UPDATE) ||
                    allowed(auth, Permission.CATALOG_WRITE) ||
                    allowed(auth, Permission.CATALOG_DELETE))
        );
        model.addAttribute("adminUser", user != null && user.roles().contains(Role.ADMIN));
        model.addAttribute(
            "jobsAllowed",
            allowed(auth, Permission.JOB_READ) && menuAllowed(request, auth, Routes.JOBS_PAGE)
        );
        model.addAttribute(
            "accessAllowed",
            user != null &&
                user.roles().contains(Role.ADMIN) &&
                allowed(auth, Permission.ACCESS_READ) &&
                menuAllowed(request, auth, Routes.ACCESS_PAGE)
        );
        model.addAttribute(
            "monitoringAllowed",
            allowed(auth, Permission.SYSTEM_READ) && menuAllowed(request, auth, Routes.MONITORING_PAGE)
        );
        model.addAttribute(
            "configurationAllowed",
            allowed(auth, Permission.SYSTEM_READ) && menuAllowed(request, auth, Routes.CONFIGURATION_PAGE)
        );
        model.addAttribute("accountAllowed", menuAllowed(request, auth, Routes.ACCOUNT));
        model.addAttribute(
            "mediaAllowed",
            allowed(auth, Permission.MEDIA_READ) && menuAllowed(request, auth, Routes.MEDIA_PAGE)
        );
        model.addAttribute(
            "journalAllowed",
            user != null &&
                user.roles().contains(Role.ADMIN) &&
                allowed(auth, Permission.SYSTEM_READ) &&
                menuAllowed(request, auth, Routes.API_JOURNAL_PAGE)
        );
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

    /** Snapshot do interceptor đặt trên request; không truy vấn DB riêng cho từng menu. */
    private static boolean menuAllowed(HttpServletRequest request, Authentication auth, String path) {
        Object snapshot = request.getAttribute(Http.ACCESS_POLICY_ATTRIBUTE);
        if (snapshot instanceof List<?> policies) {
            for (Object item : policies) {
                if (
                    item instanceof AccessPolicyResponse policy &&
                    policy.target().equals("GET " + path) &&
                    !AccessManagementService.allows(policy, auth)
                ) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean allowed(Authentication auth, Permission permission) {
        return (
            auth != null &&
            auth
                .getAuthorities()
                .stream()
                .anyMatch(value -> value.getAuthority().equals(permission.name()))
        );
    }
}
