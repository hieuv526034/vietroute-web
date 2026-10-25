package vn.vietroute.business.booking.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Hai endpoint VIEW riêng, dùng component chung nhưng không lấy chế độ/quyền từ query client. */
@Controller
public class BookingPageController {

    @GetMapping(Routes.BOOKINGS_PAGE)
    @AccessFunction(group = Groups.BOOKING, function = Functions.BOOKING, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.BOOKING, action = PermissionAction.VIEW)
    public String staff(Model model) {
        model.addAttribute("customerBookings", false);
        return "booking/manage";
    }

    @GetMapping(Routes.MY_BOOKINGS_PAGE)
    @AccessFunction(group = Groups.MY_BOOKING, function = Functions.MY_BOOKING, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.MY_BOOKING, action = PermissionAction.VIEW)
    public String customer(Model model) {
        model.addAttribute("customerBookings", true);
        return "booking/manage";
    }
}
