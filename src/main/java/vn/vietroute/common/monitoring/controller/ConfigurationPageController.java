package vn.vietroute.common.monitoring.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Chỉ đọc các cấu hình được whitelist; không có form sửa env hoặc trình xem secret. */
@Controller
@AccessFunction(
    group = Groups.SYSTEM,
    function = Functions.CONFIGURATION,
    permissions = { Permissions.CONFIGURATION_VIEW }
)
public class ConfigurationPageController {

    @GetMapping(Routes.CONFIGURATION_PAGE)
    @PreAuthorize(AccessRules.CONFIGURATION_VIEW)
    public String configuration() {
        return "operations/configuration";
    }
}
