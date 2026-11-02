package vn.vietroute.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import vn.vietroute.common.api_journal.filter.ApiJournalFilter.ApiJournalState;
import vn.vietroute.common.api_journal.service.ApiJournalService;
import vn.vietroute.common.web.response.ApiResult;

/**
 * Bắt lỗi MVC xảy ra trước/sau thân controller, ví dụ JSON sai, @Valid hoặc endpoint không tồn tại.
 * Dùng cùng mapper với BaseController để mã lỗi không phụ thuộc điểm phát sinh exception.
 */
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler extends RequestBodyAdviceAdapter {

    private final ApiJournalService projection;

    private final HttpServletRequest request;

    private final ApiExceptionMapper exceptions;

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handle(Exception error, HttpServletRequest request) {
        return exceptions.toResponse(error, request);
    }

    @Override
    public boolean supports(
        MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        return projection.supports(parameter.getParameterType());
    }

    @Override
    public Object afterBodyRead(
        Object body,
        HttpInputMessage input,
        MethodParameter parameter,
        Type targetType,
        Class<? extends HttpMessageConverter<?>> converterType
    ) {
        ApiJournalState state = ApiJournalState.get(request);
        if (state != null) {
            try {
                state.requestBody = projection.request(body);
            } catch (RuntimeException ignored) {
                /* Best effort, không làm lỗi API. */
            }
        }
        return body;
    }
}
