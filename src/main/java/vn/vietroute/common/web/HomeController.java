package vn.vietroute.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class HomeController {

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("applicationName", "VietRoute");
        return "home";
    }

    /** Kiểm tra web đang chạy; không thực hiện truy vấn hoặc ghi vào database. */
    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    @io.swagger.v3.oas.annotations.Operation(
        summary = "Kiểm tra ứng dụng web",
        description = "Availability probe; không truy vấn database."
    )
    public String health() {
        return "vietroute-web: UP";
    }
}
