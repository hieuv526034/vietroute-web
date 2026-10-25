package vn.vietroute.business.location.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang danh mục dùng chung chỉ ADMIN; quyền VIEW độc lập READ/CREATE/UPDATE và được kiểm lại khi nhập URL. */
@Controller
@AccessFunction(
    group = Groups.LOCATION,
    function = Functions.LOCATION,
    actions = { PermissionAction.VIEW },
    adminOnly = true
)
public class LocationPageController {

    @GetMapping(Routes.LOCATION_PAGE)
    @AccessOperation(resource = Groups.LOCATION, action = PermissionAction.VIEW, adminOnly = true)
    public String locations() {
        return "location/manage";
    }

    /** URL menu riêng chỉ mở nhóm địa điểm; quyền dữ liệu vẫn tại API/service. */
    @GetMapping(Routes.CATALOG_LOCATIONS_PAGE)
    @AccessOperation(resource = Groups.LOCATION, action = PermissionAction.VIEW, adminOnly = true)
    public String places(Model model) {
        model.addAttribute("geoSection", "locations");
        return "location/manage";
    }

    /** VIEW tỉnh thành độc lập, quyền LOCATION_VIEW cũ được giữ trong phạm vi địa lý hiện hữu. */
    @GetMapping(Routes.CATALOG_PROVINCES_PAGE)
    @AccessFunction(
        group = Groups.PROVINCE,
        function = Functions.PROVINCE,
        actions = { PermissionAction.VIEW },
        permissions = { "LOCATION_VIEW" },
        adminOnly = true
    )
    @AccessOperation(
        resource = Groups.PROVINCE,
        action = PermissionAction.VIEW,
        aliases = { "LOCATION_VIEW" },
        adminOnly = true
    )
    public String provinces(Model model) {
        model.addAttribute("geoSection", "provinces");
        return "location/manage";
    }
}
