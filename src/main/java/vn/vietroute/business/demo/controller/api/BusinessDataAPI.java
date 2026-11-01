package vn.vietroute.business.demo.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.demo.service.DemoSeedService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API khởi tạo dữ liệu tường minh; không nhận dữ liệu/role/audit tùy ý và không gọi mail/provider ngoài. */
@RestController
public class BusinessDataAPI extends BaseController {

    private final DemoSeedService seeds;

    public BusinessDataAPI(DemoSeedService seeds, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.seeds = seeds;
    }

    @PostMapping(Routes.ADMIN_DEMO)
    @Operation(
        summary = "Khởi tạo dữ liệu nghiệp vụ",
        description = "ADMIN và mọi quyền CREATE liên quan. Chỉ INSERT bản ghi thiếu từ bộ dữ liệu VietRoute, do dự án tự xây dựng, giữ dữ liệu có sẵn; chạy lại không tạo trùng. Không tạo account, không gửi mail, endpoint này không tạo tuyến hoặc membership; startup có cờ khởi tạo riêng."
    )
    public ApiResult<DemoSeedResponse> seed(@AuthenticationPrincipal UserPrincipal actor, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> seeds.seed(actor));
    }
}
