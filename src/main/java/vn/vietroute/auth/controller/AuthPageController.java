package vn.vietroute.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Các trang Thymeleaf chỉ render giao diện; submit bằng Axios vào API auth dùng chung. */
@Controller
public class AuthPageController {

    @GetMapping("/login")
    public String login() {
        return "auth/login";
    }

    @GetMapping("/account")
    public String account() {
        return "auth/account";
    }

    @GetMapping("/admin")
    public String admin() {
        return "auth/admin";
    }

    @GetMapping("/docs")
    public String docs() {
        return "docs/swagger";
    }
}
