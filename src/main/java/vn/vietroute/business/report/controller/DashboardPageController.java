package vn.vietroute.business.report.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang tổng quan của phiên hiện tại; mỗi khối đọc API nghiệp vụ với guard/scope gốc, không cấp quyền mới. */
@Controller
public class DashboardPageController {

    /** Dashboard không nhúng PII hoặc tổng hợp quyền server vào HTML; tài khoản cần thiết lập vẫn dùng trang account. */
    @GetMapping(Routes.DASHBOARD)
    public String page() {
        return "dashboard/index";
    }
}
