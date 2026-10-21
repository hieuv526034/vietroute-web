package vn.vietroute.common.validation;

import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Quy tắc service dùng chung; kiểm tra tenant/trạng thái cụ thể vẫn thuộc service nghiệp vụ. */
public final class BusinessRules {
    private BusinessRules() { }
    public static void require(boolean condition, ErrorCode code, Object... args) {
        if (!condition) { throw new BusinessException(code, args); }
    }
    public static <T> T requireFound(T value) {
        require(value != null, ErrorCode.NOT_FOUND);
        return value;
    }
    public static void requireVersion(long expected, long current) {
        require(expected == current, ErrorCode.STALE_VERSION);
    }
}
