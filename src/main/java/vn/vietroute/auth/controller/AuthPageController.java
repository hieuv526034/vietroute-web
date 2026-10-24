package vn.vietroute.auth.controller;

import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.AuthNavigation;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Các trang Thymeleaf chỉ render giao diện; submit bằng Axios vào API auth dùng chung. */
@Controller
@AccessFunction(group = Groups.AUTH, authenticated = true)
public class AuthPageController {

    /** Trang public không chứa token trên URL; mọi xác minh gửi POST JSON có CSRF. */
    @GetMapping(Routes.AUTH_LIFECYCLE_PAGE)
    public String lifecycle(@AuthenticationPrincipal UserPrincipal principal, Locale locale) {
        if (principal != null) return (
            "redirect:" +
            UriComponentsBuilder.fromPath(Routes.ACCOUNT)
                .queryParam(Http.LANGUAGE_PARAMETER, locale.getLanguage())
                .build()
                .toUriString()
        );
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
    public String account() {
        return "auth/account";
    }

    @AccessFunction(group = Groups.CATALOG, function = Functions.CATALOG, permissions = { Permissions.CATALOG_VIEW })
    @GetMapping(Routes.ADMIN)
    @PreAuthorize(AccessRules.CATALOG_VIEW)
    public String admin() {
        return "auth/admin";
    }

    @GetMapping(Routes.DOCS)
    public String docs() {
        return "docs/swagger";
    }
}
