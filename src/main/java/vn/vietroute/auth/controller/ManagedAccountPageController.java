package vn.vietroute.auth.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang danh tính toàn hệ thống chỉ ADMIN; không mở cho quản lý nhà xe chưa có scope DB. */
@Controller
@AccessFunction(
    group = Groups.ACCOUNTS,
    function = Functions.ACCOUNTS,
    actions = { PermissionAction.VIEW },
    resource = Groups.ACCOUNT,
    adminOnly = true
)
public class ManagedAccountPageController {

    @GetMapping(Routes.ACCOUNTS_PAGE)
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.VIEW, adminOnly = true)
    public String accounts() {
        return "operations/accounts";
    }
}
