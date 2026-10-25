package vn.vietroute.business.catalog.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Mỗi danh mục có URL/VIEW riêng; HTML dùng chung nhưng không cho đổi loại bằng select client. */
@Controller
public class CatalogPageController {

    @GetMapping(Routes.CATALOG_VEHICLE_TYPES_PAGE)
    @AccessFunction(
        group = Groups.VEHICLE_TYPE,
        function = Functions.VEHICLE_TYPE,
        actions = { PermissionAction.VIEW },
        permissions = { Permissions.CATALOG_VIEW }
    )
    @AccessOperation(
        resource = Groups.VEHICLE_TYPE,
        action = PermissionAction.VIEW,
        aliases = { Permissions.CATALOG_VIEW }
    )
    public String vehicleTypes(Model model) {
        return page(model, "VEHICLE_TYPE", Functions.VEHICLE_TYPE, Routes.ADMIN_VEHICLE_TYPES, "ui.vehicleType");
    }

    @GetMapping(Routes.CATALOG_BOOKING_SOURCES_PAGE)
    @AccessFunction(
        group = Groups.BOOKING_SOURCE,
        function = Functions.BOOKING_SOURCE,
        actions = { PermissionAction.VIEW },
        permissions = { Permissions.CATALOG_VIEW }
    )
    @AccessOperation(
        resource = Groups.BOOKING_SOURCE,
        action = PermissionAction.VIEW,
        aliases = { Permissions.CATALOG_VIEW }
    )
    public String bookingSources(Model model) {
        return page(
            model,
            "BOOKING_SOURCE",
            Functions.BOOKING_SOURCE,
            Routes.ADMIN_BOOKING_SOURCES,
            "ui.bookingSource"
        );
    }

    private String page(Model model, String type, String function, String api, String title) {
        model.addAttribute("catalogType", type);
        model.addAttribute("catalogFunction", function);
        model.addAttribute("catalogApi", api);
        model.addAttribute("catalogTitle", title);
        return "catalog/manage";
    }
}
