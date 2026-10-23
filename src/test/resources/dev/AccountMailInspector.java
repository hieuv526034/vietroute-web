import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import vn.vietroute.auth.security.AccountSecrets;
import vn.vietroute.auth.service.AccountMailPayload;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.mail.model.MailPayload;

/** Chỉ fixture CI: đọc outbox của DB tạm, ghi mã vào file riêng để test HTTP; không in token/secret. */
public final class AccountMailInspector {

    private AccountMailInspector() {}

    public static void main(String[] args) throws Exception {
        var json = new RootConfig().objectMapper();
        var encoded = Files.readString(Path.of(args[0]));
        var envelope = json.readValue(encoded, AccountMailPayload.class);
        var secrets = new AccountSecrets(new AppSettings(System::getenv));
        MailPayload payload = json.readValue(secrets.decrypt(envelope.encrypted()), MailPayload.class);
        var found = Pattern.compile("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{43}(?![A-Za-z0-9_-])").matcher(payload.text());
        if (!found.find()) throw new IllegalStateException("CI mail thiếu mã xác minh");
        String token = found.group();
        if (encoded.contains(token) || encoded.contains(payload.to())) {
            throw new IllegalStateException("CI outbox chưa mã hóa");
        }
        Files.writeString(Path.of(args[1]), token);
    }
}
