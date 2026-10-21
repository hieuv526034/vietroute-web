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

    /** Dịch bằng key ổn định; args chỉ chứa dữ liệu được phép xuất hiện trong thông báo công khai. */
    public String message(String key, Object[] args, Locale locale) {
        return messages.getMessage(key, args, key, locale);
    }

    /** Dùng cùng thứ tự ưu tiên lang/header với MVC và filter để không lệch ngôn ngữ. */
    public Locale locale(HttpServletRequest request) {
        return localeResolver.resolveLocale(request);
    }

    /** Bọc DTO thành công; dữ liệu audit/thời gian luôn do server cung cấp. */
    public <T> ApiResponse<T> success(SuccessCode code, T data, HttpServletRequest request) {
        return envelope(true, code.getCode(), code.getMessageKey(), new Object[0], data, List.of(), request);
    }

    /** Generic T giữ hợp đồng của endpoint, nhưng data luôn null khi thất bại và không cần ép kiểu. */
    public <T> ApiResponse<T> error(
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
