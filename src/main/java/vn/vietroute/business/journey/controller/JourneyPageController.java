package vn.vietroute.business.journey.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang tìm/chi tiết công khai; thao tác đặt vé riêng vẫn cần MY_BOOKING_CREATE và owner server. */
@Controller
public class JourneyPageController {

    @GetMapping(Routes.JOURNEYS_PAGE)
    public String page() {
        return "journey/search";
    }
}
