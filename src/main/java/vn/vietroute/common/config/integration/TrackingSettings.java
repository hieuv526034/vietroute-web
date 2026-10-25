package vn.vietroute.common.config.integration;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Chia sẻ thiết bị luôn cần tài xế chủ động; Google Maps chưa cấu hình theo lựa chọn chủ dự án. */
@Getter
public final class TrackingSettings {

    public static final int MIN_SAMPLE_SECONDS = 10;
    public static final int STALE_SECONDS = 90;
    public static final int LEASE_SECONDS = 120;
    public static final int MAX_SAMPLE_AGE_SECONDS = 120;
    public static final int FUTURE_SKEW_SECONDS = 15;
    public static final int RETENTION_DAYS = 7;
    public static final int TRIP_CAPACITY = 2000;
    public static final int GLOBAL_CAPACITY = 10000;
    public static final int CLEANUP_BATCH = 500;

    private final boolean enabled;

    public TrackingSettings(Function<String, String> source) {
        enabled = new ConfigValues(source).flag(Env.TRACKING_ENABLED, true);
    }
}
