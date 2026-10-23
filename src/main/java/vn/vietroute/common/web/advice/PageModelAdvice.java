package vn.vietroute.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
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
        model.addAttribute("adminUser", user != null && user.roles().contains(Role.ADMIN));
        model.addAttribute("jobsAllowed", allowed(auth, Permission.JOB_READ));
        model.addAttribute("accessAllowed", allowed(auth, Permission.ACCESS_READ));
        model.addAttribute("monitoringAllowed", allowed(auth, Permission.SYSTEM_READ));
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
