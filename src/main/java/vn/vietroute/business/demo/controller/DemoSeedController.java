package vn.vietroute.business.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessFunction.Match;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.demo.service.DemoSeedService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API tạo DEMO tường minh; không nhận dữ liệu/role/audit tùy ý và không gọi mail/provider ngoài. */
@RestController
public class DemoSeedController extends BaseController {

    private final DemoSeedService seeds;

    public DemoSeedController(DemoSeedService seeds, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.seeds = seeds;
    }

    @PostMapping(Routes.ADMIN_DEMO)
    @Operation(
        summary = "Tạo dữ liệu DEMO nghiệp vụ",
        description = "ADMIN và mọi quyền CREATE liên quan. Chỉ INSERT dữ liệu thiếu mang mã/nhãn DEMO, giữ dữ liệu có sẵn; chạy lại không tạo trùng. Không tạo account, không gửi mail, không tự chạy lúc startup."
    )
    @AccessFunction(
        group = Groups.DEMO,
        function = Functions.DEMO,
        actions = { PermissionAction.CREATE },
        adminOnly = true,
        match = Match.ALL,
        permissions = {
            Groups.CARRIER + "_CREATE",
            Groups.VEHICLE + "_CREATE",
            Groups.DRIVER + "_CREATE",
            Groups.PROVINCE + "_CREATE",
            Groups.LOCATION + "_CREATE",
            Groups.VEHICLE_TYPE + "_CREATE",
            Groups.BOOKING_SOURCE + "_CREATE",
        }
    )
    public ApiResult<DemoSeedResponse> seed(@AuthenticationPrincipal UserPrincipal actor, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> seeds.seed(actor));
    }
}
