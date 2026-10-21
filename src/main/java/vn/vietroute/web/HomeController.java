package vn.vietroute.web;

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

    /** Application availability only; this endpoint does not claim database readiness. */
    @GetMapping(value = "/health", produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public String health() {
        return "vietroute-web: UP";
    }
}
