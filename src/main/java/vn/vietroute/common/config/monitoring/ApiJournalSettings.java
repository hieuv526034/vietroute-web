package vn.vietroute.common.config.monitoring;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Nhật ký PostgreSQL dùng chung; mặc định giữ 7 ngày, không giữ lịch sử trong RAM. */
@Getter
public final class ApiJournalSettings {

    private final boolean enabled;

    private final int capacity;

    private final int retentionMinutes;

    public ApiJournalSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag(Env.API_JOURNAL_ENABLED, true);
        capacity = values.number(Env.API_JOURNAL_CAPACITY, 10000, 20, 100000);
        retentionMinutes = values.number(Env.API_JOURNAL_MINUTES, 10080, 1, 43200);
    }
}
