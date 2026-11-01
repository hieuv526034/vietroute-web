package vn.vietroute.business.carrier.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang staff dùng layout/modal chung; mỗi màn VIEW độc lập với READ và scope dữ liệu. */
@Controller
public class CarrierPageController {

    @GetMapping(Routes.CARRIERS_PAGE)
    public String carriers(Model model, @AuthenticationPrincipal UserPrincipal actor) {
        model.addAttribute("carrierStateEditable", UserPrincipal.globalData());
        return page(model, "carriers", "CARRIER");
    }

    @GetMapping(Routes.VEHICLES_PAGE)
    public String vehicles(Model model) {
        return page(model, "vehicles", "VEHICLE");
    }

    @GetMapping(Routes.DRIVERS_PAGE)
    public String drivers(Model model) {
        return page(model, "drivers", "DRIVER");
    }

    private String page(Model model, String kind, String feature) {
        model.addAttribute("fleetKind", kind);
        model.addAttribute("fleetFeature", feature);
        return "carrier/manage";
    }

    @GetMapping(Routes.PUBLIC_CARRIERS_PAGE)
    public String publicPage() {
        return "carrier/public";
    }
}
