package vn.vietroute.auth.controller;

import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.util.UriComponentsBuilder;
import vn.vietroute.auth.security.AuthNavigation;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Các trang Thymeleaf chỉ render giao diện; submit bằng Axios vào API auth dùng chung. */
@Controller
public class AuthPageController {

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

    @GetMapping(Routes.ADMIN)
    public String admin() {
        return "auth/admin";
    }

    @GetMapping(Routes.DOCS)
    public String docs() {
        return "docs/swagger";
    }
}
