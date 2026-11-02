package vn.vietroute.business.catalog.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Mỗi danh mục có URL/VIEW riêng; HTML dùng chung nhưng không cho đổi loại bằng select client. */
@Controller
public class CatalogItemPageController {

    @GetMapping(Routes.CATALOG_VEHICLE_TYPES_PAGE)
    public String vehicleTypes(Model model) {
        return page(model, "VEHICLE_TYPE", "VEHICLE_TYPE", Routes.ADMIN_VEHICLE_TYPES, "ui.vehicleType");
    }

    @GetMapping(Routes.CATALOG_BOOKING_SOURCES_PAGE)
    public String bookingSources(Model model) {
        return page(model, "BOOKING_SOURCE", "BOOKING_SOURCE", Routes.ADMIN_BOOKING_SOURCES, "ui.bookingSource");
    }

    private String page(Model model, String type, String function, String api, String title) {
        model.addAttribute("catalogType", type);
        model.addAttribute("catalogFunction", function);
        model.addAttribute("catalogApi", api);
        model.addAttribute("catalogTitle", title);
        return "catalog/manage";
    }
}
