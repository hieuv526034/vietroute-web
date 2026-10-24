package vn.vietroute.common.constant;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Mã máy đọc không đổi khi dịch; messageKey tham chiếu đủ hai bundle vi/en. */
@Getter
public enum ErrorCode {
    VALIDATION_FAILED("COM001", "error.validation", HttpStatus.BAD_REQUEST),
    INVALID_REQUEST("COM002", "error.request", HttpStatus.BAD_REQUEST),
    LOCALE_UNSUPPORTED("COM003", "error.locale", HttpStatus.BAD_REQUEST),
    NOT_FOUND("COM004", "error.notFound", HttpStatus.NOT_FOUND),
    CONFLICT("COM005", "error.conflict", HttpStatus.CONFLICT),
    INTERNAL_ERROR("COM006", "error.internal", HttpStatus.INTERNAL_SERVER_ERROR),
    DATABASE_UNAVAILABLE("COM007", "error.database", HttpStatus.SERVICE_UNAVAILABLE),
    INVALID_CODE("VAL003", "validation.code", HttpStatus.BAD_REQUEST),
    INVALID_CREDENTIALS("AUTH001", "auth.invalidCredentials", HttpStatus.UNAUTHORIZED),
    UNAUTHENTICATED("AUTH002", "auth.unauthenticated", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("AUTH003", "auth.denied", HttpStatus.FORBIDDEN),
    TOKEN_INVALID("AUTH004", "auth.tokenInvalid", HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED("AUTH005", "auth.locked", HttpStatus.TOO_MANY_REQUESTS),
    CSRF_INVALID("AUTH006", "auth.csrf", HttpStatus.FORBIDDEN),
    PASSWORD_INCORRECT("AUTH007", "auth.passwordIncorrect", HttpStatus.BAD_REQUEST),
    PASSWORD_REUSED("AUTH008", "auth.passwordReused", HttpStatus.BAD_REQUEST),
    ACCOUNT_FLOW_DISABLED("AUTH009", "auth.flowDisabled", HttpStatus.SERVICE_UNAVAILABLE),
    ACCOUNT_CODE_INVALID("AUTH010", "auth.codeInvalid", HttpStatus.BAD_REQUEST),
    PASSWORD_CHANGE_REQUIRED("AUTH011", "auth.passwordChangeRequired", HttpStatus.FORBIDDEN),
    STALE_VERSION("COM008", "error.staleVersion", HttpStatus.CONFLICT),
    RESOURCE_IN_USE("COM015", "error.inUse", HttpStatus.CONFLICT),
    METHOD_NOT_ALLOWED("COM009", "error.method", HttpStatus.METHOD_NOT_ALLOWED),
    UNSUPPORTED_MEDIA("COM010", "error.media", HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    NOT_ACCEPTABLE("COM011", "error.accept", HttpStatus.NOT_ACCEPTABLE),
    RATE_LIMITED("COM012", "error.rateLimit", HttpStatus.TOO_MANY_REQUESTS),
    SERVICE_BUSY("COM013", "error.busy", HttpStatus.SERVICE_UNAVAILABLE),
    PAYLOAD_TOO_LARGE("COM014", "error.payload", HttpStatus.PAYLOAD_TOO_LARGE),
    MAIL_DISABLED("MAIL001", "mail.disabled", HttpStatus.SERVICE_UNAVAILABLE),
    JOB_NOT_RETRYABLE("JOB001", "job.notRetryable", HttpStatus.CONFLICT),
    MEDIA_DISABLED("MEDIA001", "media.disabled", HttpStatus.SERVICE_UNAVAILABLE),
    MEDIA_INVALID("MEDIA002", "media.invalid", HttpStatus.BAD_REQUEST),
    MEDIA_UNAVAILABLE("MEDIA003", "media.unavailable", HttpStatus.SERVICE_UNAVAILABLE);

    private final String code;

    private final String messageKey;

    private final HttpStatus status;

    ErrorCode(String code, String messageKey, HttpStatus status) {
        this.code = code;
        this.messageKey = messageKey;
        this.status = status;
    }
}
