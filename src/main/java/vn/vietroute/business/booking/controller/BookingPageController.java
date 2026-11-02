package vn.vietroute.business.booking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Hai endpoint VIEW riêng, dùng component chung nhưng không lấy chế độ/quyền từ query client. */
@Controller
public class BookingPageController {

    @GetMapping(Routes.BOOKINGS_PAGE)
    public String staff(Model model) {
        model.addAttribute("customerBookings", false);
        return "booking/manage";
    }

    @GetMapping(Routes.MY_BOOKINGS_PAGE)
    public String customer(Model model) {
        model.addAttribute("customerBookings", true);
        return "booking/manage";
    }
}
