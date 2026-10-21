package vn.vietroute.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.web.*;

/** Tập trung mapping exception → HTTP/mã/key; lỗi nội bộ chỉ ghi log phía server. */
@RestControllerAdvice @RequiredArgsConstructor
public class GlobalExceptionHandler {
    private static final Logger LOG = Logger.getLogger(GlobalExceptionHandler.class.getName());
    private final ApiResponses responses;
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> business(BusinessException error, HttpServletRequest request) {
        return result(error.getErrorCode(), error.getArguments(), List.of(), request);
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ApiResponse<Void>> validation(BindException error, HttpServletRequest request) {
        List<FieldViolation> fields = error.getBindingResult().getFieldErrors().stream().map(field -> {
            String key = "validation.invalid";
            try { key = key(field.unwrap(ConstraintViolation.class).getMessageTemplate()); }
            catch (IllegalArgumentException ignored) { /* Không công bố parser/rejectedValue. */ }
            return violation(field.getField(), key, request);
        }).toList();
        return result(ErrorCode.VALIDATION_FAILED, new Object[0], fields, request);
    }
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> constraint(ConstraintViolationException error, HttpServletRequest request) {
        List<FieldViolation> fields = error.getConstraintViolations().stream()
                .map(value -> violation(value.getPropertyPath().toString(), key(value.getMessageTemplate()), request)).toList();
        return result(ErrorCode.VALIDATION_FAILED, new Object[0], fields, request);
    }
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiResponse<Void>> methodValidation(HandlerMethodValidationException error, HttpServletRequest request) {
        return result(ErrorCode.VALIDATION_FAILED, new Object[0], List.of(violation("parameter", "validation.invalid", request)), request);
    }
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiResponse<Void>> notFound(Exception error, HttpServletRequest request) {
        return result(ErrorCode.NOT_FOUND, new Object[0], List.of(), request);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, ServletRequestBindingException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiResponse<Void>> invalid(Exception error, HttpServletRequest request) {
        return result(ErrorCode.INVALID_REQUEST, new Object[0], List.of(), request);
    }
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> denied(AccessDeniedException error, HttpServletRequest request) {
        return result(ErrorCode.ACCESS_DENIED, new Object[0], List.of(), request);
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> method(Exception error, HttpServletRequest request) {
        return result(ErrorCode.METHOD_NOT_ALLOWED, new Object[0], List.of(), request);
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> media(Exception error, HttpServletRequest request) {
        return result(ErrorCode.UNSUPPORTED_MEDIA, new Object[0], List.of(), request);
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiResponse<Void>> accept(Exception error, HttpServletRequest request) {
        return result(ErrorCode.NOT_ACCEPTABLE, new Object[0], List.of(), request);
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        // Log có traceId để điều tra; không trả exception message hoặc SQL ra client.
        LOG.log(Level.SEVERE, "Request failed traceId=" + RequestTraceFilter.ensure(request, null), error);
        return result(ErrorCode.INTERNAL_ERROR, new Object[0], List.of(), request);
    }
    private FieldViolation violation(String field, String key, HttpServletRequest request) {
        var code = vn.vietroute.common.constant.ValidationCode.fromKey(key);
        return new FieldViolation(field, code.getCode(), code.getMessageKey(),
                responses.message(code.getMessageKey(), new Object[0], responses.locale(request)));
    }
    private static String key(String template) {
        return template != null && template.matches("\\{[A-Za-z0-9.]+}")
                ? template.substring(1, template.length() - 1) : "validation.invalid";
    }
    private ResponseEntity<ApiResponse<Void>> result(ErrorCode code, Object[] args, List<FieldViolation> fields, HttpServletRequest request) {
        return ResponseEntity.status(code.getStatus()).header("Content-Language", responses.locale(request).getLanguage())
                .body(responses.error(code, args, fields, request));
    }
}
