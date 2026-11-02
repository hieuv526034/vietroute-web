package vn.vietroute.business.bootstrap.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang opt-in dữ liệu minh họa; không có truy vấn/mutation chỉ vì mở trang. */
@Controller
public class BusinessDataPageController {

    @GetMapping(Routes.DEMO_PAGE)
    public String page() {
        return "demo/manage";
    }
}
