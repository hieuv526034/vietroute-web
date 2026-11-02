package vn.vietroute.auth.controller;

import java.util.Locale;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Chỉ render Thymeleaf; đăng nhập và quyền endpoint vẫn do Security/MVC kiểm ở server. */
@Controller
public class UserPageController {

    @GetMapping(Routes.LOGIN)
    public String login(@AuthenticationPrincipal UserPrincipal actor, Locale locale) {
        return actor == null ? "auth/login" : "redirect:" + Routes.DASHBOARD + "?lang=" + locale.getLanguage();
    }

    @GetMapping(Routes.ACCOUNT)
    public String account() {
        return "auth/account";
    }

    @GetMapping(Routes.ACCOUNTS_PAGE)
    public String accounts() {
        return "operations/accounts";
    }

    @GetMapping(Routes.ACCESS_PAGE)
    public String access() {
        return "operations/access";
    }

    @GetMapping(Routes.ACCESS_RESOURCES_PAGE)
    public String resources() {
        return "operations/access-resources";
    }

    @GetMapping(Routes.ADMIN)
    public String admin() {
        return "auth/dashboard";
    }

    @GetMapping(Routes.DOCS)
    public String docs() {
        return "docs/swagger";
    }
}
