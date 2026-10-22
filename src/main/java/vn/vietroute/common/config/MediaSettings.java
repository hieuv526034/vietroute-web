package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;

/** Cloudinary tắt mặc định; không có toString để tránh log API secret. */
@Getter
public final class MediaSettings {

    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    public static final int MAX_REQUEST_BYTES = MAX_FILE_BYTES + 64 * 1024;

    private final boolean enabled;

    private final String cloudName;

    private final String apiKey;

    private final String apiSecret;

    private final String folder;

    public MediaSettings(Function<String, String> source) {
        ConfigValues values = new ConfigValues(source);
        enabled = values.flag("VIETROUTE_MEDIA_ENABLED", false);
        cloudName = values.text("VIETROUTE_CLOUDINARY_CLOUD_NAME", "");
        apiKey = values.text("VIETROUTE_CLOUDINARY_API_KEY", "");
        apiSecret = values.text("VIETROUTE_CLOUDINARY_API_SECRET", "");
        folder = values.text("VIETROUTE_CLOUDINARY_FOLDER", "vietroute");
        if (!folder.matches("[a-zA-Z][a-zA-Z0-9_-]{0,39}")) {
            throw new IllegalStateException("Invalid VIETROUTE_CLOUDINARY_FOLDER");
        }
        if (
            enabled &&
            (!cloudName.matches("[a-zA-Z0-9_-]{1,80}") || !apiKey.matches("[0-9]{1,40}") || apiSecret.isBlank())
        ) {
            throw new IllegalStateException("Cloudinary cloud name, API key and API secret are required");
        }
        // Dọn upload dang dở/xóa lỗi cần worker; không nhận file khi thiếu cơ chế phục hồi bền vững.
        if (enabled && !values.flag("VIETROUTE_WORKER_ENABLED", false)) {
            throw new IllegalStateException("VIETROUTE_WORKER_ENABLED must be true when media is enabled");
        }
    }
}
