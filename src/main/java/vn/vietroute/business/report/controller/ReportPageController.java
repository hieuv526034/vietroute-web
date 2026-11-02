package vn.vietroute.business.report.controller;

import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Business;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** VIEW bảo vệ URL trực tiếp ở servlet context, không chỉ ẩn menu. */
@Controller
public class ReportPageController {

    @GetMapping(Routes.REPORTS_PAGE)
    public String page(Model model) {
        LocalDate today = LocalDate.now(ZoneId.of(Business.TIME_ZONE));
        model.addAttribute("reportFrom", today.minusDays(29));
        model.addAttribute("reportTo", today);
        return "report/summary";
    }
}
