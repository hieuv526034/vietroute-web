package vn.vietroute.business.carrier.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang công khai độc lập menu nhân sự, chỉ dùng API projection nhà xe đã công bố. */
@Controller
public class PublicCarrierPageController {

    @GetMapping(Routes.PUBLIC_CARRIERS_PAGE)
    public String page() {
        return "carrier/public";
    }
}
