package vn.vietroute.business.bootstrap.controller.api;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.bootstrap.dto.response.BusinessDataResponse;
import vn.vietroute.business.bootstrap.service.BusinessDataService;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API khởi tạo dữ liệu tường minh; không nhận dữ liệu/role/audit tùy ý và không gọi mail/provider ngoài. */
@RestController
public class BusinessDataAPI extends BaseController {

    private final BusinessDataService seeds;

    public BusinessDataAPI(BusinessDataService seeds, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.seeds = seeds;
    }

    @PostMapping(Routes.ADMIN_DEMO)
    @Operation(
        summary = "Khởi tạo dữ liệu nghiệp vụ",
        description = "Cần grant POST /api/v1/admin/demo và phạm vi toàn cục của chính endpoint. Chỉ thêm danh mục/nhà xe/xe/hồ sơ tài xế còn thiếu từ bộ nền, giữ dữ liệu có sẵn. API không tạo account, tuyến, chuyến, đơn hoặc membership; bộ vận hành đầy đủ chỉ chạy startup khi bật các cờ init tương ứng."
    )
    public ApiResult<BusinessDataResponse> seed(
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> seeds.seed(actor));
    }
}
