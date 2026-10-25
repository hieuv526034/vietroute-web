package vn.vietroute.business.carrier.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang staff dùng layout/modal chung; mỗi màn VIEW độc lập với READ và scope dữ liệu. */
@Controller
public class CarrierPageController {

    @GetMapping(Routes.CARRIERS_PAGE)
    @AccessFunction(group = Groups.CARRIER, function = Functions.CARRIER, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.CARRIER, action = PermissionAction.VIEW)
    public String carriers(Model model, @AuthenticationPrincipal UserPrincipal actor) {
        model.addAttribute("carrierStateEditable", actor.roles().contains(Role.ADMIN));
        return page(model, "carriers", Functions.CARRIER);
    }

    @GetMapping(Routes.VEHICLES_PAGE)
    @AccessFunction(group = Groups.VEHICLE, function = Functions.VEHICLE, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.VEHICLE, action = PermissionAction.VIEW)
    public String vehicles(Model model) {
        return page(model, "vehicles", Functions.VEHICLE);
    }

    @GetMapping(Routes.DRIVERS_PAGE)
    @AccessFunction(group = Groups.DRIVER, function = Functions.DRIVER, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.DRIVER, action = PermissionAction.VIEW)
    public String drivers(Model model) {
        return page(model, "drivers", Functions.DRIVER);
    }

    private String page(Model model, String kind, String feature) {
        model.addAttribute("fleetKind", kind);
        model.addAttribute("fleetFeature", feature);
        return "carrier/manage";
    }
}
