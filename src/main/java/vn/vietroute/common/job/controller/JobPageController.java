package vn.vietroute.common.job.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang vận hành chỉ lấy metadata từ API đã kiểm quyền; không render payload của handler. */
@Controller
public class JobPageController {

    @GetMapping(Routes.JOBS_PAGE)
    @PreAuthorize("hasAuthority('JOB_READ')")
    public String jobs() {
        return "operations/jobs";
    }
}
