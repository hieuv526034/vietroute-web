package vn.vietroute.common.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.monitoring.dto.response.ActivitySnapshot;
import vn.vietroute.common.monitoring.dto.response.OnlineVisitorResponse;
import vn.vietroute.common.monitoring.dto.response.RequestAnomalyResponse;
import vn.vietroute.common.monitoring.service.ActivityService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

@RestController
@Tag(
    name = "Lưu lượng truy cập",
    description = "Hiện diện và tín hiệu bất thường dùng chung PostgreSQL; ADMIN + SYSTEM_READ"
)
@AccessFunction(group = Groups.SYSTEM, function = Functions.MONITORING)
public class ActivityController extends BaseController {

    private final ActivityService activity;

    public ActivityController(ActivityService activity, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.activity = activity;
    }

    @PostMapping(Routes.PRESENCE)
    @Operation(
        summary = "Nhịp hiện diện trình duyệt",
        description = "CSRF + quota vẫn bắt buộc; filter tự ghi định danh server, không nhận user/visitor từ body"
    )
    public ApiResult<Boolean> heartbeat(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, activity::enabled);
    }

    @AccessFunction(group = Groups.SYSTEM, permissions = { Permissions.SYSTEM_READ }, adminOnly = true)
    @GetMapping(Routes.ACTIVITY)
    @Operation(
        summary = "Tổng quan online và request",
        description = "ADMIN + SYSTEM_READ; DB lỗi trả 503, không trả số 0 giả"
    )
    public ApiResult<ActivitySnapshot> snapshot(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, activity::snapshot);
    }

    @AccessFunction(group = Groups.SYSTEM, permissions = { Permissions.SYSTEM_READ }, adminOnly = true)
    @GetMapping(Routes.ACTIVITY_ONLINE)
    @Operation(
        summary = "Phân trang trình duyệt online",
        description = "ADMIN + SYSTEM_READ; cửa sổ 3 phút, không chứa IP/cookie/token"
    )
    public ApiResult<PageResponse<OnlineVisitorResponse>> online(@Valid PageRequest page, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> activity.online(page));
    }

    @AccessFunction(group = Groups.SYSTEM, permissions = { Permissions.SYSTEM_READ }, adminOnly = true)
    @GetMapping(Routes.ACTIVITY_ANOMALIES)
    @Operation(
        summary = "Tín hiệu request bất thường",
        description = "ADMIN + SYSTEM_READ; tối đa 100 phút/chủ thể trong 5 phút gần nhất, ngưỡng 401/403>=20,429>=10,5xx>=10 hoặc lượng request cấu hình"
    )
    public ApiResult<List<RequestAnomalyResponse>> anomalies(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, activity::anomalies);
    }
}
