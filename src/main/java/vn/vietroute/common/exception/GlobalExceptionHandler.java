package vn.vietroute.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.vietroute.common.web.response.ApiResponse;

/**
 * Bắt lỗi MVC xảy ra trước/sau thân controller, ví dụ JSON sai, @Valid hoặc endpoint không tồn tại.
 * Dùng cùng mapper với BaseController để mã lỗi không phụ thuộc điểm phát sinh exception.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    private final ApiExceptionMapper exceptions;

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handle(Exception error, HttpServletRequest request) {
        return exceptions.toResponse(error, request);
    }
}
