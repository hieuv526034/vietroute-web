package vn.vietroute.business.trip.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** EndpointAccess trong servlet context kiểm grant GET của page, kể cả nhập URL trực tiếp. */
@Controller
public class TripPageController {

    @GetMapping(Routes.TRIPS_PAGE)
    public String page() {
        return "trip/manage";
    }

    @GetMapping(Routes.DRIVER_TRIPS_PAGE)
    public String driverPage() {
        return "trip/driver";
    }
}
