package vn.vietroute.common.web.response;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;

/**
 * Kiểu trả về ngắn cho controller: {@code ApiResult<UserResponse>} vẫn là HTTP response có envelope ApiResponse.
 * Spring xử lý như ResponseEntity nên giữ nguyên status, header, generic DTO và cơ chế serialize hiện tại.
 */
public final class ApiResult<T> extends ResponseEntity<ApiResponse<T>> {

    public ApiResult(ApiResponse<T> body, HttpHeaders headers, HttpStatusCode status) {
        super(body, headers, status);
    }

    /** Bọc kết quả builder chuẩn, không ép kiểu DTO và không thay đổi body/header khi truyền qua base. */
    public static <T> ApiResult<T> from(ResponseEntity<ApiResponse<T>> response) {
        return new ApiResult<>(response.getBody(), response.getHeaders(), response.getStatusCode());
    }
}
