package vn.vietroute.common.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.AccessConstants.Functions;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;
import vn.vietroute.common.monitoring.service.ApiJournalService;
import vn.vietroute.common.web.response.ApiResponses;
import vn.vietroute.common.web.response.ApiResult;

/** API chỉ đọc nhật ký đã lọc, không có endpoint replay hoặc xem raw credentials. */
@RestController
@Tag(name = "API Journal", description = "Nhật ký HTTP lưu PostgreSQL dùng chung, chỉ ADMIN và SYSTEM_READ")
@AccessFunction(
    group = Groups.SYSTEM,
    function = Functions.JOURNAL,
    permissions = { Permissions.SYSTEM_READ },
    adminOnly = true
)
public class ApiJournalController extends BaseController {

    private final ApiJournalService journal;

    public ApiJournalController(ApiJournalService journal, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.journal = journal;
    }

    @GetMapping(Routes.API_JOURNAL)
    @Operation(
        summary = "Tra nhật ký API",
        description = "ADMIN + SYSTEM_READ. Lọc/paging trên PostgreSQL, mặc định giữ 7 ngày; bản ghi đã commit vẫn còn sau restart. Không cache lịch sử trong RAM/raw body/header; tự loại API nhật ký khỏi capture."
    )
    public ApiResult<ApiJournalResponse> page(
        @Valid @ModelAttribute ApiJournalSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> journal.page(search));
    }

    @GetMapping(Routes.API_JOURNAL_DETAIL)
    @Operation(
        summary = "Xem request/response đã lọc",
        description = "ADMIN + SYSTEM_READ. UUID là ID bản ghi dùng chung giữa các node; hết retention/đã dọn trả 404, DB lỗi trả 503. Projection bỏ auth/file/PII và chuỗi tự do."
    )
    public ApiResult<ApiJournalDetail> detail(
        @Parameter(
            description = "UUID bản ghi do hệ thống cấp",
            example = "caeae4ef-f5a4-4b16-9a34-06e50d6be073"
        ) @PathVariable UUID id,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> journal.find(id));
    }
}
