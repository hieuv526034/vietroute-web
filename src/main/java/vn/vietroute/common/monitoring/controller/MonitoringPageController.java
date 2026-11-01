package vn.vietroute.common.monitoring.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang vận hành chỉ đọc; quyền backend độc lập với việc ẩn/hiện menu. */
@Controller
public class MonitoringPageController {

    /** Chặn tại backend trước khi render, kể cả người dùng tự nhập URL thay vì bấm menu. */
    @GetMapping(Routes.MONITORING_PAGE)
    public String monitoring() {
        return "monitoring";
    }
}
