package vn.vietroute.common.web.advice;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
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
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.FunctionService;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

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
        model.addAttribute("breadcrumbs", build(menu, current, locale, messages));
        Set<String> paths = menu
            .stream()
            .map(item -> item.path())
            .collect(Collectors.toSet());
        model.addAttribute("catalogAllowed", paths.contains(Routes.ADMIN));
        model.addAttribute("accessAllowed", paths.contains(Routes.ACCESS_PAGE));
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
            "access",
            path.equals(Routes.ACCESS_PAGE),
            "journal",
            path.equals(Routes.API_JOURNAL_PAGE)
        );
        model.addAttribute("navigation", navigation);
        model.addAttribute("operationsActive", navigation.get("journal"));
    }

    /** Mỗi mục có link trong ứng dụng; nhóm không có trang chọn trang con đầu tiên mà người dùng được mở. */
    public static List<FunctionDTO> build(List<FunctionDTO> menu, String path, Locale locale, MessageSource messages) {
        if (Routes.HOME.equals(path)) return List.of();
        ArrayList<FunctionDTO> result = new ArrayList<FunctionDTO>();
        result.add(item(messages.getMessage("ui.home", null, locale), Routes.HOME, false));
        String canonical = Routes.LOCATION_PAGE.equals(path) ? Routes.CATALOG_LOCATIONS_PAGE : path;
        if (append(menu, canonical, result)) return List.copyOf(result);
        String key = switch (path) {
            case Routes.DASHBOARD -> "ui.dashboard.title";
            case Routes.ACCOUNT -> "ui.account";
            case Routes.LOGIN -> "ui.login";
            case Routes.PUBLIC_CARRIERS_PAGE -> "ui.publicCarrier.title";
            case Routes.JOURNEYS_PAGE -> "ui.journey.search";
            case Routes.DOCS -> "ui.nav.swagger";
            default -> null;
        };
        if (key != null) result.add(item(messages.getMessage(key, null, locale), path, true));
        return result.size() > 1 ? List.copyOf(result) : List.of();
    }

    private static boolean append(List<FunctionDTO> nodes, String path, List<FunctionDTO> trail) {
        for (FunctionDTO node : nodes) {
            if (path.equals(node.path())) {
                trail.add(item(node.name(), path, true));
                return true;
            }
            ArrayList<FunctionDTO> nested = new ArrayList<FunctionDTO>();
            if (append(node.children(), path, nested)) {
                trail.add(item(node.name(), node.path() != null ? node.path() : firstPath(node.children()), false));
                trail.addAll(nested);
                return true;
            }
        }
        return false;
    }

    private static String firstPath(List<FunctionDTO> nodes) {
        for (FunctionDTO node : nodes) {
            if (node.path() != null) return node.path();
            String child = firstPath(node.children());
            if (child != null) return child;
        }
        return null;
    }

    private static FunctionDTO item(String name, String path, boolean active) {
        FunctionDTO result = new FunctionDTO();
        result.setName(name);
        result.setPath(path);
        result.setIcon("");
        result.setActive(active);
        return result;
    }
}
