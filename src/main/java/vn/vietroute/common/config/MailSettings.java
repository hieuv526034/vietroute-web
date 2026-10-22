package vn.vietroute.common.config;

import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import java.util.function.Function;
import lombok.Getter;

/** Credentials chỉ trong Environment, không có toString. Gmail API dùng HTTPS nên phù hợp Render Free. */
@Getter
public final class MailSettings {

    private final boolean enabled;

    private final String provider;

    private final String from;

    private final String clientId;

    private final String clientSecret;

    private final String refreshToken;

    private final String smtpHost;

    private final int smtpPort;

    private final String smtpUser;

    private final String smtpPassword;

    public MailSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag("VIETROUTE_MAIL_ENABLED", false);
        provider = values.text("VIETROUTE_MAIL_PROVIDER", "gmail");
        if (!provider.equals("gmail") && !provider.equals("smtp")) {
            throw new IllegalStateException("Invalid VIETROUTE_MAIL_PROVIDER");
        }
        from = values.text("VIETROUTE_MAIL_FROM", "");
        clientId = values.text("VIETROUTE_GMAIL_CLIENT_ID", "");
        clientSecret = values.text("VIETROUTE_GMAIL_CLIENT_SECRET", "");
        refreshToken = values.text("VIETROUTE_GMAIL_REFRESH_TOKEN", "");
        smtpHost = values.text("VIETROUTE_SMTP_HOST", "smtp.gmail.com");
        smtpPort = values.number("VIETROUTE_SMTP_PORT", 587, 1, 65535);
        smtpUser = values.text("VIETROUTE_SMTP_USER", "");
        smtpPassword = values.text("VIETROUTE_SMTP_PASSWORD", "");
        if (enabled && !validAddress(from)) {
            throw new IllegalStateException("VIETROUTE_MAIL_FROM must be one email address");
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
