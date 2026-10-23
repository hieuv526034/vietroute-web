package vn.vietroute.common.monitoring.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;
import vn.vietroute.common.monitoring.service.ApiJournalService;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResponses;

/** API chỉ đọc nhật ký đã lọc, không có endpoint replay hoặc xem raw credentials. */
@RestController
@Tag(name = "API Journal", description = "Nhật ký HTTP trong RAM từng node, chỉ ADMIN và SYSTEM_READ")
public class ApiJournalController extends BaseController {

    private final ApiJournalService journal;

    public ApiJournalController(ApiJournalService journal, ApiResponses responses, ApiExceptionMapper exceptions) {
        super(responses, exceptions);
        this.journal = journal;
    }

    @GetMapping(Routes.API_JOURNAL)
    @Operation(
        summary = "Tra nhật ký API",
        description = "ADMIN + SYSTEM_READ. Lọc traceId chính xác, route, method, status, code và thời gian; TTL/dung lượng giới hạn, từng node. Không lưu raw body/header; API nhật ký tự loại khỏi capture."
    )
    public ResponseEntity<ApiResponse<ApiJournalResponse>> page(
        @Valid @ModelAttribute ApiJournalSearchRequest search,
        HttpServletRequest request
    ) {
        return execute(SuccessCode.OK, request, () -> journal.page(search));
    }

    @GetMapping(Routes.API_JOURNAL_DETAIL)
    @Operation(
        summary = "Xem request/response đã lọc",
        description = "ADMIN + SYSTEM_READ. UUID là ID bản ghi; hết TTL/đổi node trả 404. Projection bỏ auth/file/PII và chuỗi tự do, không đủ để phát lại nguyên trạng."
    )
    public ResponseEntity<ApiResponse<ApiJournalDetail>> detail(@PathVariable UUID id, HttpServletRequest request) {
        return execute(SuccessCode.OK, request, () -> journal.find(id));
    }
}
