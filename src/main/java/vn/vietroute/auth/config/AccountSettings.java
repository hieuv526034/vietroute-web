package vn.vietroute.auth.config;

import lombok.Getter;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Luồng tự phục vụ chỉ mở khi được bật chủ động; mail/worker được service kiểm trước khi tạo dữ liệu. */
@Component
@Getter
public final class AccountSettings {

    /** Mặc định tắt để không công khai đăng ký trước khi người vận hành cấu hình email. */
    private final boolean enabled;

    /** Trần đăng ký toàn hệ thống trong 24 giờ, mặc định 100; khóa DB bảo vệ giữa nhiều node. */
    private final int registrationsPerDay;

    public AccountSettings(ConfigurationEnvironment environment) {
        ConfigValues values = new ConfigValues(environment);
        enabled = values.flag(Env.AUTH_SELF_SERVICE_ENABLED, false);
        registrationsPerDay = values.number(Env.AUTH_REGISTRATIONS_PER_DAY, 100, 1, 1000);
    }
}
