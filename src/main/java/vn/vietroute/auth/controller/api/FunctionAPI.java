package vn.vietroute.auth.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.request.FunctionRequest;
import vn.vietroute.auth.dto.search.FunctionSearch;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.service.FunctionService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API theo bảng gốc; quyền endpoint do interceptor, actor/audit chỉ lấy server. */
@RestController
@Tag(name = "FunctionAPI")
public class FunctionAPI extends BaseController {

    private final FunctionService functions;

    public FunctionAPI(FunctionService functions, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.functions = functions;
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_FUNCTIONS)
    @Operation(summary = "Tìm và phân trang chức năng")
    public ApiResult<PageResponse<FunctionDTO>> search(
        @Valid @ModelAttribute FunctionSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.search(search));
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_FUNCTION_ITEM)
    @Operation(summary = "Chi tiết chức năng và endpoint")
    public ApiResult<FunctionDTO> get(@PathVariable String code, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> functions.get(code));
    }

    @PostMapping(Routes.ACCESS + Routes.ACCESS_FUNCTIONS)
    @Operation(
        summary = "Tạo chức năng",
        description = "code/nameVi/nameEn/targets bắt buộc; endpoint phải tồn tại và thuộc quyền actor"
    )
    public ApiResult<FunctionDTO> create(
        @Valid @RequestBody FunctionRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.CREATED, SuccessCode.CREATED, request, () -> functions.save(body, true, actor));
    }

    @PutMapping(Routes.ACCESS + Routes.ACCESS_FUNCTION_ITEM)
    @Operation(
        summary = "Sửa chức năng",
        description = "code body phải khớp URL, version bắt buộc; thay binding và history nguyên tử"
    )
    public ApiResult<FunctionDTO> update(
        @PathVariable String code,
        @Valid @RequestBody FunctionRequest body,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            if (!code.equals(body.getCode())) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            return functions.save(body, false, actor);
        });
    }

    @DeleteMapping(Routes.ACCESS + Routes.ACCESS_FUNCTION_ITEM)
    @Operation(summary = "Xóa chức năng chưa gán role")
    public ApiResult<Void> delete(
        @PathVariable String code,
        @Parameter(description = "Version hiện hành") @RequestParam long version,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> {
            functions.delete(code, version, actor);
            return null;
        });
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_TARGETS)
    @Operation(summary = "Toàn bộ endpoint tự thu thập từ Spring MVC")
    public ApiResult<List<FunctionDTO>> catalog(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, functions::inventory);
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_RESOURCES)
    @Operation(summary = "Tìm endpoint/menu trong danh bạ server")
    public ApiResult<PageResponse<FunctionDTO>> resources(
        @Valid @ModelAttribute FunctionSearch search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> functions.resources(search));
    }
}
