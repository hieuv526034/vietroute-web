package vn.vietroute.business.demo.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang opt-in dữ liệu minh họa; không có truy vấn/mutation chỉ vì mở trang. */
@Controller
public class DemoPageController {

    @GetMapping(Routes.DEMO_PAGE)
    @AccessFunction(
        group = Groups.DEMO,
        function = Functions.DEMO,
        actions = { PermissionAction.VIEW },
        adminOnly = true
    )
    @AccessOperation(resource = Groups.DEMO, action = PermissionAction.VIEW, adminOnly = true)
    public String page() {
        return "demo/manage";
    }
}
