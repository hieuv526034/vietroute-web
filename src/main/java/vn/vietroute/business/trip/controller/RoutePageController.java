package vn.vietroute.business.trip.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** VIEW được bảo vệ trong servlet context, kể cả nhập thẳng URL. */
@Controller
public class RoutePageController {

    @GetMapping(Routes.ROUTES_PAGE)
    public String page() {
        return "route/manage";
    }
}
