package vn.vietroute.common.job;

/** Chỉ mã lỗi có kiểm soát được lưu; không đính kèm response/token/nội dung email của provider. */
public final class JobFailure extends Exception {

    private final String code;

    private final boolean retryable;

    public JobFailure(String code, boolean retryable) {
        super(code);
        if (!code.matches("[A-Z][A-Z0-9_]{2,39}")) {
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
