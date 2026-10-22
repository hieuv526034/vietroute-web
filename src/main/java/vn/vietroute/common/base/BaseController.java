package vn.vietroute.common.base;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.function.Supplier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.web.response.ApiResponse;
import vn.vietroute.common.web.response.ApiResponses;

/**
 * Khung xử lý cho API nghiệp vụ: nhận kết quả service, bọc response và bắt lỗi tại biên HTTP.
 * Controller con không mở transaction, không tự tạo mã lỗi và không sao chép try/catch.
 */
public abstract class BaseController {

    private final ApiResponses responses;

    private final ApiExceptionMapper exceptions;

    protected BaseController(ApiResponses responses, ApiExceptionMapper exceptions) {
        this.responses = responses;
        this.exceptions = exceptions;
    }

    /** API đọc/cập nhật thông thường trả HTTP 200; callback chỉ chạy đúng một lần. */
    protected final <T> ResponseEntity<ApiResponse<T>> execute(
        SuccessCode code,
        HttpServletRequest request,
        Supplier<T> action
    ) {
        return execute(HttpStatus.OK, code, request, action);
    }

    /**
     * Service hoàn tất/commit rồi mới tạo response thành công; lỗi đã rollback được mapper đổi mã.
     * Supplier dùng cho luồng đồng bộ, không chuyển request hoặc transaction sang thread khác.
     */
    protected final <T> ResponseEntity<ApiResponse<T>> execute(
        HttpStatus status,
        SuccessCode code,
        HttpServletRequest request,
        Supplier<T> action
    ) {
        try {
            T data = action.get();
            return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, Http.NO_STORE)
                .header(HttpHeaders.CONTENT_LANGUAGE, locale(request).getLanguage())
                .body(responses.success(code, data, request));
        } catch (Exception error) {
            return exceptions.toResponse(error, request);
        }
    }

    /** Locale chỉ phục vụ thông báo và mapping dữ liệu ra HTTP, không lưu vào entity. */
    protected final Locale locale(HttpServletRequest request) {
        return responses.locale(request);
    }
}
