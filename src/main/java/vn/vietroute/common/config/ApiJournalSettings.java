package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Bộ đệm từng node, giới hạn cứng RAM và TTL; không ghi thêm vào Neon cho mỗi HTTP request. */
@Getter
public final class ApiJournalSettings {

    private final boolean enabled;

    private final int capacity;

    private final int retentionMinutes;

    public ApiJournalSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag(Env.API_JOURNAL_ENABLED, true);
        capacity = values.number(Env.API_JOURNAL_CAPACITY, 300, 20, 1000);
        retentionMinutes = values.number(Env.API_JOURNAL_MINUTES, 30, 1, 120);
    }
}
