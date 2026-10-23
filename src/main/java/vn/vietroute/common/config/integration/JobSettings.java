package vn.vietroute.common.config.integration;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Worker tắt mặc định để không đánh thức Neon; bật có chủ đích trên web hoặc tiến trình worker riêng. */
@Getter
public final class JobSettings {

    /** Bật vòng poll job; false thì không đánh thức Neon để tìm việc. */
    private final boolean enabled;

    /** Số worker thực thi đồng thời trên mỗi node, mặc định 2, tối đa 8. */
    private final int threads;

    /** Khoảng nghỉ giữa các lần tìm job, đơn vị giây, mặc định 10. */
    private final int pollSeconds;

    /** Thời gian giữ quyền xử lý job, đơn vị giây; mặc định 180 để node khác phục hồi khi chết. */
    private final int leaseSeconds;

    /** Số ngày giữ receipt job đã hoàn thành, mặc định 14; cleanup theo lô. */
    private final int retentionDays;

    public JobSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag(Env.WORKER_ENABLED, false);
        threads = values.number(Env.WORKER_THREADS, 2, 1, 8);
        pollSeconds = values.number(Env.WORKER_POLL_SECONDS, 10, 1, 300);
        leaseSeconds = values.number(Env.WORKER_LEASE_SECONDS, 180, 120, 900);
        retentionDays = values.number(Env.JOB_RETENTION_DAYS, 14, 1, 90);
    }
}
