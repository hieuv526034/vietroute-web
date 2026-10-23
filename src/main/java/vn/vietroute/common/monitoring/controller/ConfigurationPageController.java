package vn.vietroute.common.monitoring.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Chỉ đọc các cấu hình được whitelist; không có form sửa env hoặc trình xem secret. */
@Controller
@AccessFunction(group = Groups.SYSTEM)
public class ConfigurationPageController {

    @GetMapping(Routes.CONFIGURATION_PAGE)
    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    public String configuration() {
        return "operations/configuration";
    }
}
