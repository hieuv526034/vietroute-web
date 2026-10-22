package vn.vietroute.common.config;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

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
        enabled = values.flag(Env.MEDIA_ENABLED, false);
        cloudName = values.text(Env.CLOUDINARY_CLOUD_NAME, "");
        apiKey = values.text(Env.CLOUDINARY_API_KEY, "");
        apiSecret = values.text(Env.CLOUDINARY_API_SECRET, "");
        folder = values.text(Env.CLOUDINARY_FOLDER, "vietroute");
        if (!folder.matches(Regex.CLOUDINARY_FOLDER)) {
            throw new IllegalStateException("Invalid " + Env.CLOUDINARY_FOLDER.key());
        }
        if (
            enabled &&
            (!cloudName.matches(Regex.CLOUDINARY_NAME) || !apiKey.matches(Regex.CLOUDINARY_KEY) || apiSecret.isBlank())
        ) {
            throw new IllegalStateException("Cloudinary cloud name, API key and API secret are required");
        }
        // Dọn upload dang dở/xóa lỗi cần worker; không nhận file khi thiếu cơ chế phục hồi bền vững.
        if (enabled && !values.flag(Env.WORKER_ENABLED, false)) {
            throw new IllegalStateException(Env.WORKER_ENABLED.key() + " must be true when media is enabled");
        }
    }
}
