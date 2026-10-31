package vn.vietroute.auth.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Locale;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.dto.record.capability.AccessCapabilityResponse;
import vn.vietroute.auth.dto.record.capability.EffectiveAccessResponse;
import vn.vietroute.auth.dto.record.resource.AccessFeatureResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessPolicyInterceptor;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.service.AccessTreeService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

@RestController
@Tag(name = "Cây chức năng", description = "Gán quyền theo chức năng/hành động và xem nguồn quyền hiệu lực")
@AccessFunction(group = Groups.ACCESS, function = Functions.ACCESS, actions = { PermissionAction.READ })
public class AccessTreeController extends BaseController {

    private final AccessTreeService tree;
    private final AccessResourceSynchronizer metadata;

    public AccessTreeController(
        AccessTreeService tree,
        AccessResourceSynchronizer metadata,
        ApiResponses responses,
        ApiExceptionMapper errors
    ) {
        super(responses, errors);
        this.tree = tree;
        this.metadata = metadata;
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_TREE)
    @Operation(
        summary = "Cây chức năng, hành động và endpoint",
        description = "ACCESS_READ; quyền active từ DB, binding từ mapping trên node hiện tại"
    )
    public ApiResult<List<AccessFeatureResponse>> tree(Locale locale, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> tree.tree(metadata.inventory(), locale));
    }

    @GetMapping(Routes.ACCESS + Routes.ACCESS_EFFECTIVE)
    @Operation(
        summary = "Xem nguồn cấp quyền của user",
        description = "ACCESS_READ; hợp các role active, không có quyền hiệu lực nếu tài khoản tắt, pending, mật khẩu tạm hoặc chưa xác minh email"
    )
    public ApiResult<EffectiveAccessResponse> effective(
        @Parameter(description = "ID bản ghi cần thao tác", example = "1") @PathVariable long id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> tree.effective(id));
    }

    @GetMapping(Routes.AUTH + Routes.AUTH_CAPABILITIES)
    @AccessFunction(group = Groups.PROFILE, authenticated = true)
    @Operation(
        summary = "Menu và thao tác được phép của chính user",
        description = "JWT/DB authorities + guard annotation + policy; không nhận user ID hoặc trả token"
    )
    public ApiResult<List<AccessCapabilityResponse>> capabilities(HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () ->
            tree.own(metadata.inventory(), AccessPolicyInterceptor.snapshot(request))
        );
    }
}
