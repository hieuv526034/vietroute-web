package vn.vietroute.common.monitoring.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang vận hành chỉ đọc; quyền backend độc lập với việc ẩn/hiện menu. */
@Controller
@AccessFunction(group = Groups.SYSTEM, function = Functions.MONITORING, actions = { PermissionAction.VIEW })
public class MonitoringPageController {

    /** Chặn tại backend trước khi render, kể cả người dùng tự nhập URL thay vì bấm menu. */
    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.VIEW)
    @GetMapping(Routes.MONITORING_PAGE)
    public String monitoring() {
        return "monitoring";
    }
}
