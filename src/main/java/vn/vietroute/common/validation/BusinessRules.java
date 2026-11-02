package vn.vietroute.common.validation;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Quy tắc service dùng chung; kiểm tra tenant/trạng thái cụ thể vẫn thuộc service nghiệp vụ. */
public final class BusinessRules {

    private BusinessRules() {}

    public static void require(boolean condition, ErrorCode code, Object... args) {
        if (!condition) {
            throw new BusinessException(code, args);
        }
    }

    public static <T> T requireFound(T value) {
        require(value != null, ErrorCode.NOT_FOUND);
        return value;
    }

    public static void requireVersion(long expected, long current) {
        require(expected == current, ErrorCode.STALE_VERSION);
    }

    /** Chính sách BCrypt giữ nguyên giới hạn 72 byte và độ phức tạp. */
    public static boolean validPassword(String value) {
        return (
            validInitialPassword(value) &&
            value.chars().anyMatch(Character::isUpperCase) &&
            value.chars().anyMatch(Character::isLowerCase) &&
            value.chars().anyMatch(Character::isDigit) &&
            value.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
        );
    }

    /** Riêng tài khoản seed do chủ dự án cấu hình: tối thiểu 8 ký tự, tối đa 72 byte BCrypt. */
    public static boolean validInitialPassword(String value) {
        return (
            value != null &&
            !value.isBlank() &&
            value.length() >= 8 &&
            value.getBytes(StandardCharsets.UTF_8).length <= 72
        );
    }

    /** Email đơn, không display name, khoảng trắng hoặc header injection. */
    public static boolean validEmail(String value) {
        return value != null && value.length() <= 254 && value.matches(Regex.EMAIL_ADDRESS);
    }

    /** Mã sinh ngẫu nhiên vẫn chịu UNIQUE của database. */
    public static String generateCode(String prefix) {
        require(prefix != null && prefix.matches(Regex.CODE_PREFIX), ErrorCode.INVALID_CODE);
        return prefix + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
