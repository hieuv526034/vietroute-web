package vn.vietroute.common.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.monitoring.dto.response.RuntimeStatusResponse;
import vn.vietroute.common.monitoring.dto.response.SystemConfigurationResponse;
import vn.vietroute.common.monitoring.dto.response.SystemStatusResponse;
import vn.vietroute.common.monitoring.service.ApplicationMetrics;
import vn.vietroute.common.monitoring.service.SystemConfigurationService;
import vn.vietroute.common.monitoring.service.SystemStatusService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

@RestController
@Tag(name = "Vận hành", description = "Quan sát pool/worker/quota và kiểm tra database thật")
public class SystemStatusController extends BaseController {

    private final SystemStatusService system;

    private final ApplicationMetrics metrics;

    private final SystemConfigurationService configuration;

    public SystemStatusController(
        SystemStatusService system,
        ApplicationMetrics metrics,
        SystemConfigurationService configuration,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.system = system;
        this.metrics = metrics;
        this.configuration = configuration;
    }

    @GetMapping(Routes.SYSTEM_STATUS)
    @Operation(
        summary = "Kiểm tra readiness và tình trạng instance",
        description = "Yêu cầu SYSTEM_READ; có truy vấn DB, không dùng làm health check Render Free"
    )
    public ApiResult<SystemStatusResponse> status(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, system::status);
    }

    @GetMapping(Routes.SYSTEM_RUNTIME)
    @Operation(
        summary = "Đọc tài nguyên và lưu lượng instance",
        description = "SYSTEM_READ; đọc JVM/registry/cache. JWT vẫn xác minh tài khoản qua DB; metrics vận hành dùng route và token riêng."
    )
    public ApiResult<RuntimeStatusResponse> runtime(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, metrics::snapshot);
    }

    @GetMapping(Routes.SYSTEM_CONFIGURATION)
    @Operation(
        summary = "Xem cấu hình hệ thống an toàn",
        description = "SYSTEM_READ; whitelist thông số không nhạy cảm tại startup. Chỉ đọc, không trả secret/URL/credential, không kiểm provider thật và không sửa env."
    )
    public ApiResult<SystemConfigurationResponse> configuration(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, configuration::read);
    }
}
