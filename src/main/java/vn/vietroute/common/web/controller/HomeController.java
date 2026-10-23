package vn.vietroute.common.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

@Controller
@AccessFunction(group = Groups.API)
public class HomeController {

    @GetMapping(Routes.HOME)
    public String home(Model model) {
        model.addAttribute("applicationName", ApplicationConstants.NAME);
        return "home";
    }

    /** Kiểm tra web đang chạy; không thực hiện truy vấn hoặc ghi vào database. */
    @GetMapping(value = Routes.HEALTH, produces = Http.TEXT_UTF8)
    @ResponseBody
    @Operation(summary = "Kiểm tra ứng dụng web", description = "Availability probe; không truy vấn database.")
    public String health() {
        return "vietroute-web: UP";
    }
}
