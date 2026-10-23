package vn.vietroute.common.config.integration;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Credentials chỉ trong Environment, không có toString. Gmail API dùng HTTPS nên phù hợp Render Free. */
@Getter
public final class MailSettings {

    /** Bật gửi email thật; mặc định false, job không giả báo đã gửi khi tắt. */
    private final boolean enabled;

    /** Chọn gmail HTTPS OAuth hoặc smtp STARTTLS; mặc định gmail. */
    private final String provider;

    /** Một địa chỉ gửi đã được provider cấp quyền sử dụng. */
    private final String from;

    /** OAuth client ID Gmail/Workspace từ môi trường. */
    private final String clientId;

    /** OAuth secret chỉ dùng phía server, không trả qua API cấu hình. */
    private final String clientSecret;

    /** Refresh token dùng lấy access token Gmail, không lưu vào nhật ký. */
    private final String refreshToken;

    /** Máy chủ SMTP, mặc định smtp.gmail.com; chỉ dùng khi chọn smtp. */
    private final String smtpHost;

    /** Cổng SMTP STARTTLS, mặc định 587, không đồng nghĩa SMTPS implicit 465. */
    private final int smtpPort;

    /** Tài khoản xác thực SMTP do môi trường cung cấp. */
    private final String smtpUser;

    /** Mật khẩu ứng dụng SMTP, không phải mật khẩu tài khoản web. */
    private final String smtpPassword;

    public MailSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag(Env.MAIL_ENABLED, false);
        provider = values.text(Env.MAIL_PROVIDER, "gmail");
        if (!provider.equals("gmail") && !provider.equals("smtp")) {
            throw new IllegalStateException("Invalid " + Env.MAIL_PROVIDER.key());
        }
        from = values.text(Env.MAIL_FROM, "");
        clientId = values.text(Env.GMAIL_CLIENT_ID, "");
        clientSecret = values.text(Env.GMAIL_CLIENT_SECRET, "");
        refreshToken = values.text(Env.GMAIL_REFRESH_TOKEN, "");
        smtpHost = values.text(Env.SMTP_HOST, "smtp.gmail.com");
        smtpPort = values.number(Env.SMTP_PORT, 587, 1, 65535);
        smtpUser = values.text(Env.SMTP_USER, "");
        smtpPassword = values.text(Env.SMTP_PASSWORD, "");
        if (enabled && !validAddress(from)) {
            throw new IllegalStateException(Env.MAIL_FROM.key() + " must be one email address");
        }
        if (
            enabled &&
            provider.equals("gmail") &&
            (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank())
        ) {
            throw new IllegalStateException("Gmail OAuth client ID/secret/refresh token are required");
        }
        if (
            enabled && provider.equals("smtp") && (smtpUser.isBlank() || smtpPassword.isBlank() || smtpHost.isBlank())
        ) {
            throw new IllegalStateException("SMTP host/user/password are required");
        }
    }

    /** Không nhận danh sách recipient hoặc CRLF từ client, tránh header injection. */
    public static boolean validAddress(String value) {
        if (value == null || value.length() > 254 || value.contains("\r") || value.contains("\n")) {
            return false;
        }
        try {
            InternetAddress[] parsed = InternetAddress.parse(value, true);
            if (parsed.length != 1 || parsed[0].getPersonal() != null) {
                return false;
            }
            parsed[0].validate();
            return value.equals(parsed[0].getAddress()) && value.contains("@");
        } catch (AddressException exception) {
            return false;
        }
    }
}
