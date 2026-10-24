package vn.vietroute.auth.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang danh tính toàn hệ thống chỉ ADMIN; không mở cho quản lý nhà xe chưa có scope DB. */
@Controller
@AccessFunction(
    group = Groups.ACCOUNTS,
    function = Functions.ACCOUNTS,
    permissions = { Permissions.ACCOUNT_VIEW },
    adminOnly = true
)
public class ManagedAccountPageController {

    @GetMapping(Routes.ACCOUNTS_PAGE)
    @PreAuthorize(AccessRules.ACCOUNT_VIEW)
    public String accounts() {
        return "operations/accounts";
    }
}
