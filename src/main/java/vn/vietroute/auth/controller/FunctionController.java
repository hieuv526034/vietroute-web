package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.request.function.FunctionCreateRequest;
import vn.vietroute.auth.dto.request.function.FunctionSearchRequest;
import vn.vietroute.auth.dto.request.function.FunctionUpdateRequest;
import vn.vietroute.auth.dto.request.role.RoleFunctionsRequest;
import vn.vietroute.auth.dto.response.function.FunctionResponse;
import vn.vietroute.auth.dto.response.role.RoleFunctionsResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.FunctionService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API Function có DTO class/Swagger đầy đủ; service kiểm quyền độc lập và giữ transaction. */
@RestController
@RequestMapping(Routes.ACCESS)
@Tag(name = "Chức năng", description = "CRUD chức năng và gán role theo User → Role → Function → Menu/API")
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, actions = { PermissionAction.READ })
public class FunctionController extends BaseController {

    private final FunctionService functions;

    public FunctionController(FunctionService functions, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.functions = functions;
    }

    @GetMapping(Routes.ACCESS_FUNCTIONS)
    @Operation(
        summary = "Tìm và phân trang chức năng",
        description = "ACCESS_READ; query/active/page/size được lọc tại PostgreSQL. Bỏ projection thao tác nội bộ khỏi danh sách UI."
    )
    public ApiResult<PageResponse<FunctionResponse>> page(
        @Valid @ModelAttribute FunctionSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.page(search));
    }

    @GetMapping(Routes.ACCESS_FUNCTION_ITEM)
    @Operation(summary = "Chi tiết chức năng và Menu/API", description = "ACCESS_READ; không có code trả 404.")
    public ApiResult<FunctionResponse> get(
        @Parameter(description = "Mã chức năng", example = "BOOKING_MANAGEMENT") @PathVariable String code,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.get(code));
    }

    @PostMapping(Routes.ACCESS_FUNCTIONS)
    @ResponseStatus(HttpStatus.CREATED)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.CREATE })
    @Operation(
        summary = "Tạo chức năng",
        description = "ACCESS_CREATE; code bất biến/không trùng, target phải thuộc danh bạ server và trần ủy quyền actor. Không tự gán role thường."
    )
    public ApiResult<FunctionResponse> create(
        @Valid @RequestBody FunctionCreateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> functions.save(body, 0, actor, true));
    }

    @PutMapping(Routes.ACCESS_FUNCTION_ITEM)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.UPDATE })
    @Operation(
        summary = "Sửa chức năng",
        description = "ACCESS_UPDATE; code path/body phải khớp, version CAS cũ trả 409. Function source chỉ sửa tên; thay binding và projection role nguyên tử cùng lịch sử."
    )
    public ApiResult<FunctionResponse> update(
        @Parameter(description = "Mã chức năng bất biến") @PathVariable String code,
        @Valid @RequestBody FunctionUpdateRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            if (!code.equals(body.getCode())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            return functions.save(body, body.getVersion(), actor, false);
        });
    }

    @DeleteMapping(Routes.ACCESS_FUNCTION_ITEM)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.DELETE })
    @Operation(
        summary = "Xóa mềm chức năng chưa sử dụng",
        description = "ACCESS_DELETE; còn role tham chiếu hoặc version cũ trả 409. Chặn xóa function source; giữ tombstone và lịch sử."
    )
    public ApiResult<Void> delete(
        @Parameter(description = "Mã chức năng") @PathVariable String code,
        @Parameter(description = "Version đọc gần nhất", example = "0") @RequestParam long version,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            functions.delete(code, version, actor);
            return null;
        });
    }

    @GetMapping(Routes.ACCESS_ROLE_FUNCTIONS)
    @Operation(summary = "Đọc chức năng của role", description = "ACCESS_READ; trả version mapping, tập rỗng là deny.")
    public ApiResult<RoleFunctionsResponse> role(
        @Parameter(description = "Mã role", example = "OPERATOR") @PathVariable Role role,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.role(role));
    }

    @PutMapping(Routes.ACCESS_ROLE_FUNCTIONS)
    @AccessFunction(group = Groups.ACCESS, actions = { PermissionAction.ASSIGN })
    @Operation(
        summary = "Gán chức năng cho role",
        description = "ACCESS_ASSIGN; thay tập theo version, ADMIN bất biến. Giữ projection ALL cũ của mapping không đổi; bỏ/lưu rồi cấp lại tường minh để cấp trọn function chuyển tiếp. Không tự nhận API mới."
    )
    public ApiResult<RoleFunctionsResponse> assign(
        @Parameter(description = "Mã role", example = "OPERATOR") @PathVariable Role role,
        @Valid @RequestBody RoleFunctionsRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.assign(role, body, actor));
    }
}
