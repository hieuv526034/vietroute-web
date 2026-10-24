package vn.vietroute.business.location.controller;

import org.springframework.stereotype.Controller;
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
}
