package vn.vietroute.common.job.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;

/** Trang vận hành chỉ lấy metadata từ API đã kiểm quyền; không render payload của handler. */
@Controller
@AccessFunction(
    group = Groups.SYSTEM,
    function = Functions.JOBS,
    actions = { PermissionAction.VIEW },
    resource = Groups.JOB
)
public class JobPageController {

    @GetMapping(Routes.JOBS_PAGE)
    @AccessOperation(resource = Groups.JOB, action = PermissionAction.VIEW)
    public String jobs() {
        return "operations/jobs";
    }
}
