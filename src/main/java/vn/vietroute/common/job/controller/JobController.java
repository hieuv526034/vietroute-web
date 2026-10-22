package vn.vietroute.common.job.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.job.dto.request.RetryJobRequest;
import vn.vietroute.common.job.dto.response.JobResponse;
import vn.vietroute.common.job.service.JobService;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResponses;

@RestController
@RequestMapping(Routes.JOBS)
@Tag(name = "Worker", description = "Theo dõi và retry công việc nền có quyền chức năng")
public class JobController extends BaseController {

    private final JobService jobs;

    public JobController(JobService jobs, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.jobs = jobs;
    }

    @GetMapping
    @Operation(summary = "Danh sách job có phân trang", description = "Yêu cầu JOB_READ, không trả payload")
    public ResponseEntity<ApiResponse<PageResponse<JobResponse>>> page(
        @Valid PageRequest page,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> jobs.page(page));
    }

    @GetMapping(Routes.BY_ID)
    @Operation(summary = "Xem trạng thái job", description = "Yêu cầu JOB_READ")
    public ResponseEntity<ApiResponse<JobResponse>> find(@PathVariable UUID id, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> jobs.find(id));
    }

    @PostMapping(Routes.JOB_RETRY_ACTION)
    @Operation(summary = "Chạy lại job DEAD", description = "Yêu cầu JOB_RETRY và version hiện hành")
    public ResponseEntity<ApiResponse<JobResponse>> retry(
        @PathVariable UUID id,
        @Valid @RequestBody RetryJobRequest body,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> jobs.retry(id, body.getVersion()));
    }
}
