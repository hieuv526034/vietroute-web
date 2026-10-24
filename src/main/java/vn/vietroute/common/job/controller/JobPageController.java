package vn.vietroute.common.job.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang vận hành chỉ lấy metadata từ API đã kiểm quyền; không render payload của handler. */
@Controller
@AccessFunction(group = Groups.SYSTEM, function = Functions.JOBS, permissions = { Permissions.JOB_VIEW })
public class JobPageController {

    @GetMapping(Routes.JOBS_PAGE)
    @PreAuthorize(AccessRules.JOB_VIEW)
    public String jobs() {
        return "operations/jobs";
    }
}
