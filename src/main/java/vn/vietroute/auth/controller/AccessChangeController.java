package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.AccessChangeSearchRequest;
import vn.vietroute.auth.dto.response.AccessChangeResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AccessChangeService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** Chỉ đọc lịch sử đã lưu DB; không có endpoint sửa/xóa sự kiện hoặc nhận snapshot từ client. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(
    name = "Lịch sử phân quyền",
    description = "Tra thay đổi đã commit, dữ liệu trước–sau whitelist và actor đã xác minh"
)
@AccessFunction(
    group = Groups.ACCESS,
    function = Functions.ACCESS,
    actions = { PermissionAction.READ },
    adminOnly = true
)
public class AccessChangeController extends BaseController {

    private final AccessChangeService changes;

    public AccessChangeController(AccessChangeService changes, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.changes = changes;
    }

    @GetMapping(Routes.ACCESS_HISTORY)
    @Operation(
        summary = "Tra lịch sử thay đổi quyền",
        description = "ADMIN + ACCESS_READ; lọc event/tiền tố target/actorId và page/size. Chỉ sự kiện còn trong retention, không raw body/token/password/email. Snapshot mapping chứa mã bỏ/thêm và tổng trước–sau."
    )
    public ApiResult<PageResponse<AccessChangeResponse>> page(
        @Valid @ModelAttribute AccessChangeSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> changes.page(search));
    }
}
