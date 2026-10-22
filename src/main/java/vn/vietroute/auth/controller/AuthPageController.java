package vn.vietroute.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Các trang Thymeleaf chỉ render giao diện; submit bằng Axios vào API auth dùng chung. */
@Controller
public class AuthPageController {

    @GetMapping(Routes.LOGIN)
    public String login() {
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
