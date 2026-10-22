package vn.vietroute.common.job.model;

import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Chỉ mã lỗi có kiểm soát được lưu; không đính kèm response/token/nội dung email của provider. */
public final class JobFailure extends Exception {

    private final String code;

    private final boolean retryable;

    public JobFailure(String code, boolean retryable) {
        super(code);
        if (!code.matches(Regex.JOB_ERROR_CODE)) {
            throw new IllegalArgumentException("Invalid internal error code");
        }
        this.code = code;
        this.retryable = retryable;
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }
}
