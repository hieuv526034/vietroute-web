package vn.vietroute.common.monitoring.controller;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;
import lombok.RequiredArgsConstructor;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;
import vn.vietroute.common.monitoring.service.ApiJournalProjection;
import vn.vietroute.common.monitoring.service.ApiJournalState;

/** Chỉ lấy DTO sau parse thành công, trước validation; malformed/unknown/auth/file không được đọc lại body. */
@ControllerAdvice
@RequiredArgsConstructor
public class ApiJournalBodyAdvice extends RequestBodyAdviceAdapter {

    private final HttpServletRequest request;

    private final ApiJournalProjection projection;

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
