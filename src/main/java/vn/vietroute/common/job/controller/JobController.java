package vn.vietroute.common.job.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.job.dto.request.RetryJobRequest;
import vn.vietroute.common.job.dto.response.JobResponse;
import vn.vietroute.common.job.model.JobStatus;
import vn.vietroute.common.job.service.JobService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

@RestController
@RequestMapping(Routes.JOBS)
@Tag(name = "Worker", description = "Theo dõi và retry công việc nền có quyền chức năng")
@AccessFunction(
    group = Groups.SYSTEM,
    function = Functions.JOBS,
    actions = { PermissionAction.READ },
    resource = Groups.JOB
)
public class JobController extends BaseController {

    private final JobService jobs;

    public JobController(JobService jobs, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.jobs = jobs;
    }

    @GetMapping
    @Operation(summary = "Danh sách job có phân trang", description = "Yêu cầu JOB_READ, không trả payload")
    public ApiResult<PageResponse<JobResponse>> page(
        @Valid PageRequest page,
        @Parameter(description = "Lọc trạng thái; bỏ trống để xem tất cả") @RequestParam(
            required = false
        ) JobStatus status,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> jobs.page(page, status));
    }

    @GetMapping(Routes.BY_ID)
    @Operation(summary = "Xem trạng thái job", description = "Yêu cầu JOB_READ")
    public ApiResult<JobResponse> find(
        @Parameter(
            description = "UUID bản ghi do hệ thống cấp",
            example = "caeae4ef-f5a4-4b16-9a34-06e50d6be073"
        ) @PathVariable UUID id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> jobs.find(id));
    }

    @AccessFunction(group = Groups.SYSTEM, actions = { PermissionAction.RETRY }, resource = Groups.JOB)
    @PostMapping(Routes.JOB_RETRY_ACTION)
    @Operation(summary = "Chạy lại job DEAD", description = "Yêu cầu JOB_RETRY và version hiện hành")
    public ApiResult<JobResponse> retry(
        @Parameter(
            description = "UUID bản ghi do hệ thống cấp",
            example = "caeae4ef-f5a4-4b16-9a34-06e50d6be073"
        ) @PathVariable UUID id,
        @Valid @RequestBody RetryJobRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> jobs.retry(id, body.getVersion(), actor));
    }
}
