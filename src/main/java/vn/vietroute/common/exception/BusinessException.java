package vn.vietroute.common.exception;

import lombok.Getter;
import vn.vietroute.common.constant.ErrorCode;

/** Ném lỗi nghiệp vụ bằng enum/key và tham số; thông báo được dịch khi trả HTTP response. */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final Object[] arguments;
    public BusinessException(ErrorCode errorCode, Object... arguments) {
        super(errorCode.getCode());
        this.errorCode = errorCode;
        this.arguments = arguments.clone();
    }
    public Object[] getArguments() { return arguments.clone(); }
}
