package vn.vietroute.common.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.monitoring.dto.response.SystemStatusResponse;
import vn.vietroute.common.monitoring.service.SystemStatusService;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResponses;

@RestController
@Tag(name = "Vận hành", description = "Quan sát pool/worker/quota và kiểm tra database thật")
public class SystemStatusController extends BaseController {

    private final SystemStatusService system;

    public SystemStatusController(SystemStatusService system, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.system = system;
    }

    @GetMapping(Routes.SYSTEM_STATUS)
    @Operation(
        summary = "Kiểm tra readiness và tình trạng instance",
        description = "Yêu cầu SYSTEM_READ; có truy vấn DB, không dùng làm health check Render Free"
    )
    public ResponseEntity<ApiResponse<SystemStatusResponse>> status(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, system::status);
    }
}
