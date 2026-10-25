package vn.vietroute.business.dashboard.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang tổng quan của phiên hiện tại; mỗi khối đọc API nghiệp vụ với guard/scope gốc, không cấp quyền mới. */
@Controller
@AccessFunction(group = Groups.AUTH, authenticated = true)
public class DashboardPageController {

    /** Dashboard không nhúng PII hoặc tổng hợp quyền server vào HTML; tài khoản cần thiết lập vẫn dùng trang account. */
    @GetMapping(Routes.DASHBOARD)
    @AccessOperation(authenticated = true)
    public String page() {
        return "dashboard/index";
    }
}
