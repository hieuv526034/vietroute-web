package vn.vietroute.auth.config;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Ngưỡng lưu lịch sử quyền và registry; mặc định nhỏ, có thể tăng có kiểm soát sau nghiệm thu dung lượng/tải. */
@Getter
public final class AccessSettings {

    /** Số ngày lịch sử được tra cứu; cleanup vật lý theo lô khi có thay đổi/bảo trì. */
    private final int auditRetentionDays;

    /** Mục tiêu số sự kiện, không giữ bản sao/queue lịch sử trong RAM. */
    private final int auditCapacity;

    public AccessSettings(Function<String, String> source) {
        var values = new ConfigValues(source);
        auditRetentionDays = values.number(Env.ACCESS_AUDIT_RETENTION_DAYS, 7, 1, 90);
        auditCapacity = values.number(Env.ACCESS_AUDIT_CAPACITY, 1000, 100, 100000);
    }
}
