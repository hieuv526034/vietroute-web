import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import vn.vietroute.auth.service.AccountMailComposer;
import vn.vietroute.common.config.RootConfig;

/** Xuất template thật với mã giả chỉ để duyệt giao diện; không khởi tạo app, DB, .env hoặc provider. */
public final class RenderMailTemplates {

    private RenderMailTemplates() {}

    public static void main(String[] args) throws Exception {
        AccountMailComposer composer = new AccountMailComposer(new RootConfig().messageSource());
        Path directory = Path.of("target/mail-preview");
        Files.createDirectories(directory);
        for (String language : List.of("vi", "en")) {
            for (String purpose : List.of("ACTIVATE", "RESET")) {
                var payload = composer.compose(
                    "preview@example.invalid",
                    purpose,
                    "DEMO_ONLY_0123456789abcdefghijklmnopqrstuvwxy",
                    Locale.forLanguageTag(language)
                );
                Files.writeString(
                    directory.resolve(purpose.toLowerCase(Locale.ROOT) + "-" + language + ".html"),
                    payload.html()
                );
            }
        }
        System.out.println("Rendered 4 mail previews with fake codes in target/mail-preview");
    }
}
