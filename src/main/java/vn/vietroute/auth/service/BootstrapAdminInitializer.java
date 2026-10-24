package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.repository.AdminRecoveryRepository;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Lifecycle bootstrap dùng chung cho root init và servlet sync; không mở API nhận username/hash tùy ý. */
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer {

    private final AppSettings settings;

    private final PasswordEncoder passwords;

    private final AdminRecoveryRepository recovery;

    public String username() {
        return settings.getBootstrapUsername().toLowerCase(Locale.ROOT);
    }

    /** Caller phải sở hữu transaction; account đã có luôn giữ mật khẩu, trạng thái và yêu cầu đổi mật khẩu. */
    public long initialize(Connection connection, long actor) throws SQLException {
        return recovery.initialize(
            connection,
            username(),
            () -> {
                if (!StrongPasswordValidator.valid(settings.getBootstrapPassword())) throw new IllegalStateException(
                    "Cần cấu hình mật khẩu bootstrap hợp lệ để tạo lại tài khoản quản trị"
                );
                return passwords.encode(settings.getBootstrapPassword());
            },
            actor
        );
    }
}
