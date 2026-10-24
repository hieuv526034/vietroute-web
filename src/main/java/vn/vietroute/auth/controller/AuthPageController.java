package vn.vietroute.auth.controller;

import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AuthNavigation;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.service.AdminRecoveryService;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Các trang Thymeleaf chỉ render giao diện; submit bằng Axios vào API auth dùng chung. */
@Controller
@AccessFunction(group = Groups.AUTH, authenticated = true)
public class AuthPageController {

    private final AdminRecoveryService recovery;

    public AuthPageController(AdminRecoveryService recovery) {
        this.recovery = recovery;
    }

    /** Trang public không chứa token trên URL; mọi xác minh gửi POST JSON có CSRF. */
    @GetMapping(Routes.AUTH_LIFECYCLE_PAGE)
    public String lifecycle(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        // Phiên đang gắn email phải mở được form nhập mã; chuyển về account ở đây sẽ tạo vòng lặp xác minh.
        model.addAttribute("accountFlowAuthenticated", principal != null);
        return "auth/lifecycle";
    }

    @GetMapping(Routes.LOGIN)
    public String login(@AuthenticationPrincipal UserPrincipal principal, Locale locale) {
        // Người đã đăng nhập không phải nhập lại mật khẩu khi bấm link login cũ hoặc nút Home.
        if (principal != null) {
            return (
                "redirect:" +
                UriComponentsBuilder.fromPath(AuthNavigation.workspace(principal))
                    .queryParam(Http.LANGUAGE_PARAMETER, locale.getLanguage())
                    .build()
                    .toUriString()
            );
        }
        return "auth/login";
    }

    @GetMapping(Routes.ACCOUNT)
    public String account(@AuthenticationPrincipal UserPrincipal principal, Model model) {
        model.addAttribute("adminRecoveryVisible", recovery.visible(principal));
        return "auth/account";
    }

    @AccessFunction(group = Groups.CATALOG, function = Functions.CATALOG, actions = { PermissionAction.VIEW })
    @GetMapping(Routes.ADMIN)
    @AccessOperation(resource = Groups.CATALOG, action = PermissionAction.VIEW)
    public String admin() {
        return "auth/admin";
    }

    @GetMapping(Routes.DOCS)
    public String docs() {
        return "docs/swagger";
    }
}
