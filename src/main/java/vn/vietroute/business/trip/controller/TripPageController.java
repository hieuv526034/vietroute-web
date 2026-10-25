package vn.vietroute.business.trip.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Method security trong servlet context bảo vệ cả truy cập nhập URL trực tiếp. */
@Controller
public class TripPageController {

    @GetMapping(Routes.TRIPS_PAGE)
    @AccessFunction(group = Groups.TRIP, function = Functions.TRIP, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.TRIP, action = PermissionAction.VIEW)
    public String page() {
        return "trip/manage";
    }
}
