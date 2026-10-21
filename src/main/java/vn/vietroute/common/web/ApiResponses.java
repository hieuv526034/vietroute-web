package vn.vietroute.common.web;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.SuccessCode;

/** Dịch thông báo tại biên HTTP, không dịch hoặc gắn locale vào entity/service. */
@Component
@RequiredArgsConstructor
public class ApiResponses {

    private final MessageSource messages;

    private final SupportedLocaleResolver localeResolver;

    private final Clock clock;

    public String message(String key, Object[] args, Locale locale) {
        return messages.getMessage(key, args, key, locale);
    }

    public Locale locale(HttpServletRequest request) {
        return localeResolver.resolveLocale(request);
    }

    public <T> ApiResponse<T> success(SuccessCode code, T data, HttpServletRequest request) {
        return envelope(true, code.getCode(), code.getMessageKey(), new Object[0], data, List.of(), request);
    }

    public ApiResponse<Void> error(
        ErrorCode code,
        Object[] args,
        List<FieldViolation> errors,
        HttpServletRequest request
    ) {
        return envelope(false, code.getCode(), code.getMessageKey(), args, null, errors, request);
    }

    private <T> ApiResponse<T> envelope(
        boolean success,
        String code,
        String key,
        Object[] args,
        T data,
        List<FieldViolation> errors,
        HttpServletRequest request
    ) {
        Locale locale = locale(request);
        return new ApiResponse<>(
            success,
            code,
            key,
            message(key, args, locale),
            data,
            List.copyOf(errors),
            Instant.now(clock),
            request.getRequestURI(),
            RequestTraceFilter.ensure(request, null),
            locale.getLanguage()
        );
    }
}
