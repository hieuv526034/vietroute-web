package vn.vietroute.business.catalog.controller;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang danh mục dùng chung chỉ ADMIN; quyền VIEW độc lập READ/CREATE/UPDATE và được kiểm lại khi nhập URL. */
@Controller
public class LocationPageController {

    @GetMapping(Routes.LOCATION_PAGE)
    @Operation(summary = "Địa điểm")
    public String locations() {
        return "location/manage";
    }

    /** URL menu riêng chỉ mở nhóm địa điểm; quyền dữ liệu vẫn tại API/service. */
    @GetMapping(Routes.CATALOG_LOCATIONS_PAGE)
    public String places(Model model) {
        model.addAttribute("geoSection", "locations");
        return "location/manage";
    }

    /** VIEW tỉnh thành độc lập, quyền LOCATION_VIEW cũ được giữ trong phạm vi địa lý hiện hữu. */
    @GetMapping(Routes.CATALOG_PROVINCES_PAGE)
    public String provinces(Model model) {
        model.addAttribute("geoSection", "provinces");
        return "location/manage";
    }
}
