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

/** Trang vận hành chỉ đọc; quyền backend độc lập với việc ẩn/hiện menu. */
@Controller
@AccessFunction(group = Groups.SYSTEM, function = Functions.MONITORING, permissions = { Permissions.SYSTEM_VIEW })
public class MonitoringPageController {

    /** Chặn tại backend trước khi render, kể cả người dùng tự nhập URL thay vì bấm menu. */
    @PreAuthorize(AccessRules.SYSTEM_VIEW)
    @GetMapping(Routes.MONITORING_PAGE)
    public String monitoring() {
        return "monitoring";
    }
}
