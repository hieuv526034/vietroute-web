package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Worker tắt mặc định để không đánh thức Neon; bật có chủ đích trên web hoặc tiến trình worker riêng. */
@Getter
public final class JobSettings {

    private final boolean enabled;

    private final int threads;

    private final int pollSeconds;

    private final int leaseSeconds;

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
