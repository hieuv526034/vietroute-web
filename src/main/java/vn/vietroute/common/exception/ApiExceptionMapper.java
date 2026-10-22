package vn.vietroute.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.ValidationCode;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;
import vn.vietroute.common.web.FieldViolation;
import vn.vietroute.common.web.RequestTraceFilter;

/** Đổi exception thành HTTP/mã lỗi/key dịch; base API và MVC advice dùng cùng một quy tắc. */
@Component
@RequiredArgsConstructor
public class ApiExceptionMapper {

    private static final Logger LOG = Logger.getLogger(ApiExceptionMapper.class.getName());

    private final ApiResponses responses;

    /**
     * Lỗi nghiệp vụ giữ nguyên mã và tham số dịch; lỗi binding được chuyển thành danh sách field.
     * Exception chưa biết chỉ trả COM006, còn chi tiết được ghi phía server cùng traceId.
     * Không bắt Error của JVM và không trả exception.getMessage() cho client.
     */
    public <T> ResponseEntity<ApiResponse<T>> toResponse(Exception error, HttpServletRequest request) {
        if (error instanceof BusinessException business) {
            return result(business.getErrorCode(), business.getArguments(), List.of(), request);
        }

        // @Valid chạy trước thân controller nên nhánh này cũng được gọi qua GlobalExceptionHandler.
        if (error instanceof BindException binding) {
            List<FieldViolation> fields = binding
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(field -> violation(field.getField(), validationKey(field), request))
                .toList();
            return result(ErrorCode.VALIDATION_FAILED, new Object[0], fields, request);
        }

        if (error instanceof ConstraintViolationException constraint) {
            List<FieldViolation> fields = constraint
                .getConstraintViolations()
                .stream()
                .map(value -> violation(value.getPropertyPath().toString(), key(value.getMessageTemplate()), request))
                .toList();
            return result(ErrorCode.VALIDATION_FAILED, new Object[0], fields, request);
        }

        if (error instanceof HandlerMethodValidationException) {
            return result(
                ErrorCode.VALIDATION_FAILED,
                new Object[0],
                List.of(violation("parameter", "validation.invalid", request)),
                request
            );
        }

        ErrorCode code = classify(error);
        if (code == ErrorCode.INTERNAL_ERROR) {
            LOG.log(Level.SEVERE, "Request failed traceId=" + RequestTraceFilter.ensure(request, null), error);
        }
        ResponseEntity<ApiResponse<T>> response = result(code, new Object[0], List.of(), request);
        // Giữ các header giao thức của MVC, đặc biệt Allow của HTTP 405 và Accept của HTTP 415.
        if (error instanceof ErrorResponse mvc && !mvc.getHeaders().isEmpty()) {
            HttpHeaders headers = new HttpHeaders();
            headers.putAll(response.getHeaders());
            headers.putAll(mvc.getHeaders());
            return new ResponseEntity<>(response.getBody(), headers, response.getStatusCode());
        }
        return response;
    }

    /** HTTP status phản ánh đúng loại lỗi; không dùng HTTP 200 cho phản hồi thất bại. */
    private ErrorCode classify(Exception error) {
        if (error instanceof NoHandlerFoundException || error instanceof NoResourceFoundException) {
            return ErrorCode.NOT_FOUND;
        }
        if (
            error instanceof HttpMessageNotReadableException ||
            error instanceof ServletRequestBindingException ||
            error instanceof MethodArgumentTypeMismatchException
        ) {
            return ErrorCode.INVALID_REQUEST;
        }
        if (error instanceof AccessDeniedException) {
            return ErrorCode.ACCESS_DENIED;
        }
        if (error instanceof HttpRequestMethodNotSupportedException) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (error instanceof HttpMediaTypeNotSupportedException) {
            return ErrorCode.UNSUPPORTED_MEDIA;
        }
        if (error instanceof HttpMediaTypeNotAcceptableException) {
            return ErrorCode.NOT_ACCEPTABLE;
        }
        return ErrorCode.INTERNAL_ERROR;
    }

    /** Chỉ lấy message template của constraint, tuyệt đối không lấy giá trị bị từ chối như mật khẩu. */
    private String validationKey(FieldError field) {
        try {
            return key(field.unwrap(ConstraintViolation.class).getMessageTemplate());
        } catch (IllegalArgumentException ignored) {
            return "validation.invalid";
        }
    }

    private FieldViolation violation(String field, String key, HttpServletRequest request) {
        ValidationCode code = ValidationCode.fromKey(key);
        return new FieldViolation(
            field,
            code.getCode(),
            code.getMessageKey(),
            responses.message(code.getMessageKey(), new Object[0], responses.locale(request))
        );
    }

    private static String key(String template) {
        return template != null && template.matches("\\{[A-Za-z0-9.]+}")
            ? template.substring(1, template.length() - 1)
            : "validation.invalid";
    }

    /** Phản hồi lỗi không được cache; locale và envelope giống lỗi từ filter bảo mật. */
    private <T> ResponseEntity<ApiResponse<T>> result(
        ErrorCode code,
        Object[] args,
        List<FieldViolation> fields,
        HttpServletRequest request
    ) {
        return ResponseEntity.status(code.getStatus())
            // Lỗi vẫn có envelope JSON kể cả khi client yêu cầu định dạng không hỗ trợ (HTTP 406).
            .contentType(MediaType.APPLICATION_JSON)
            .header("Content-Language", responses.locale(request).getLanguage())
            .header("Cache-Control", "no-store")
            .body(responses.error(code, args, fields, request));
    }
}
