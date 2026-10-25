package vn.vietroute.business.route.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** VIEW được bảo vệ trong servlet context, kể cả nhập thẳng URL. */
@Controller
public class RoutePageController {

    @GetMapping(Routes.ROUTES_PAGE)
    @AccessFunction(group = Groups.ROUTE, function = Functions.ROUTE, actions = { PermissionAction.VIEW })
    @AccessOperation(resource = Groups.ROUTE, action = PermissionAction.VIEW)
    public String page() {
        return "route/manage";
    }
}
