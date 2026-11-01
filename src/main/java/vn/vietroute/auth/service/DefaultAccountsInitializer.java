package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.repo.DefaultAccountsRepository;
import vn.vietroute.auth.repo.DefaultAccountsRepository.NewAccount;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Ngoại lệ cấp sẵn được xác nhận cho custom01/driver01/operator01; tài khoản tạo qua API vẫn xác minh bình thường. */
@Component
@RequiredArgsConstructor
public class DefaultAccountsInitializer {

    private final DefaultAccountsRepository accounts;
    private final ConfigurationEnvironment environment;
    private final PasswordEncoder passwords;

    public boolean enabled() {
        return new ConfigValues(environment).flag(Env.DEFAULT_ACCOUNTS_ENABLED, false);
    }

    /** Chỉ startup init tường minh; kiểm cả tập trước batch và giữ hash/khóa/trạng thái bản ghi đã tồn tại. */
    public void initialize(Connection connection) throws SQLException {
        if (!enabled()) return;
        var missing = accounts.missing(connection);
        var values = new ArrayList<NewAccount>();
        add(
            values,
            missing,
            "custom01",
            "Khách hàng VietRoute",
            "CUSTOMER",
            Env.DEFAULT_CUSTOMER_EMAIL,
            Env.DEFAULT_CUSTOMER_PASSWORD
        );
        add(
            values,
            missing,
            "driver01",
            "Tài xế VietRoute",
            "DRIVER",
            Env.DEFAULT_DRIVER_EMAIL,
            Env.DEFAULT_DRIVER_PASSWORD
        );
        add(
            values,
            missing,
            "operator01",
            "Điều hành VietRoute",
            "OPERATOR",
            Env.DEFAULT_OPERATOR_EMAIL,
            Env.DEFAULT_OPERATOR_PASSWORD
        );
        accounts.insert(connection, values);
    }

    private void add(
        List<NewAccount> result,
        List<String> missing,
        String username,
        String name,
        String role,
        Env emailKey,
        Env passwordKey
    ) {
        if (!missing.contains(username)) return;
        var values = new ConfigValues(environment);
        String email = values.text(emailKey, "").strip().toLowerCase(Locale.ROOT),
            password = values.text(passwordKey, "");
        if (!MailSettings.validAddress(email) || email.startsWith("replace_")) throw new IllegalStateException(
            emailKey.key() + " phải là email do chủ tài khoản cung cấp"
        );
        if (
            !StrongPasswordValidator.valid(password) ||
            password.startsWith("REPLACE_") ||
            password.startsWith("replace_")
        ) throw new IllegalStateException(passwordKey.key() + " phải thỏa chính sách mật khẩu và không là placeholder");
        result.add(new NewAccount(username, name, role, email, passwords.encode(password)));
    }
}
