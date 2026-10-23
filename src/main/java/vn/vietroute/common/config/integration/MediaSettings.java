package vn.vietroute.common.config.integration;

import java.util.function.Function;
import lombok.Getter;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Cloudinary tắt mặc định; không có toString để tránh log API secret. */
@Getter
public final class MediaSettings {

    /** Trần byte một ảnh: 5 MiB; còn phải kiểm nội dung thật và số pixel. */
    public static final int MAX_FILE_BYTES = 5 * 1024 * 1024;

    /** Dành thêm 64 KiB cho multipart boundary/metadata; không nới trần byte ảnh. */
    public static final int MAX_REQUEST_BYTES = MAX_FILE_BYTES + 64 * 1024;

    /** Bật upload/xóa Cloudinary; sửa metadata trong DB không cần bật provider. */
    private final boolean enabled;

    /** Tên cloud Cloudinary được kiểm định dạng, không nhận từ request. */
    private final String cloudName;

    /** Định danh API Cloudinary do môi trường cung cấp, không gửi cho browser. */
    private final String apiKey;

    /** Secret ký request Cloudinary, chỉ dùng tại adapter server. */
    private final String apiSecret;

    /** Thư mục đích do server cấu hình; client không được chỉ định public ID/folder. */
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
