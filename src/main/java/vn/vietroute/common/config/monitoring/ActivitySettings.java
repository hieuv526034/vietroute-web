package vn.vietroute.common.config.monitoring;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Bật có chủ đích vì thống kê phát sinh ghi DB; ngưỡng request áp dụng cho mỗi chủ thể mỗi phút. */
@Getter
public final class ActivitySettings {

    private final boolean enabled;

    private final int requestThreshold;

    public ActivitySettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag(Env.ACTIVITY_ENABLED, false);
        requestThreshold = values.number(Env.ACTIVITY_REQUEST_THRESHOLD, 120, 20, 100000);
    }
}
