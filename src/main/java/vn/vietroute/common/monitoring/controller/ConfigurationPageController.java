package vn.vietroute.common.monitoring.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Chỉ đọc các cấu hình được whitelist; không có form sửa env hoặc trình xem secret. */
@Controller
@AccessFunction(
    group = Groups.SYSTEM,
    function = Functions.CONFIGURATION,
    actions = { PermissionAction.VIEW },
    resource = Groups.CONFIGURATION
)
public class ConfigurationPageController {

    @GetMapping(Routes.CONFIGURATION_PAGE)
    @AccessOperation(resource = Groups.CONFIGURATION, action = PermissionAction.VIEW)
    public String configuration() {
        return "operations/configuration";
    }
}
