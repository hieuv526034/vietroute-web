package vn.vietroute.common.media.storage;

import com.cloudinary.Cloudinary;
import java.io.IOException;
import java.util.Map;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Chỉ backend gọi signed Upload API qua HTTPS; không đưa secret/public ID tùy ý cho trình duyệt. */
public final class CloudinaryMediaStorage implements MediaStorage {

    private final MediaSettings settings;

    private final Cloudinary client;

    public CloudinaryMediaStorage(MediaSettings settings) {
        this(
            settings,
            new Cloudinary(
                Map.of(
                    "cloud_name",
                    settings.getCloudName(),
                    "api_key",
                    settings.getApiKey(),
                    "api_secret",
                    settings.getApiSecret(),
                    "secure",
                    true
                )
            )
        );
    }

    /** Cho phép kiểm thử giao thức SDK bằng client giả trong cùng package, không đổi endpoint bằng ENV. */
    CloudinaryMediaStorage(MediaSettings settings, Cloudinary client) {
        this.settings = settings;
        this.client = client;
    }

    @Override
    public StoredImage upload(String publicId, byte[] bytes) {
        requireEnabled();
        try {
            Map<?, ?> result = client
                .uploader()
                .upload(
                    bytes,
                    Map.of(
                        "public_id",
                        publicId,
                        "resource_type",
                        "image",
                        "type",
                        "upload",
                        "overwrite",
                        false,
                        "allowed_formats",
                        new String[] { "jpg", "png" },
                        "timeout",
                        20000
                    )
                );
            String format = String.valueOf(result.get("format"));
            long version = ((Number) result.get("version")).longValue();
            if (
                !publicId.equals(result.get("public_id")) || !format.matches(Regex.IMAGE_STORAGE_FORMAT) || version < 1
            ) {
                throw new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
            }
            // Tự dựng URL trên host cố định; không nhận URL tùy ý từ HTTP/client để lưu vào DB.
            String base = "https://res.cloudinary.com/" + settings.getCloudName() + "/image/upload/";
            String path = "v" + version + "/" + publicId + "." + format;
            return new StoredImage(
                base + path,
                base + "c_limit,w_640,h_480,q_auto,f_auto/" + path,
                format,
                ((Number) result.get("bytes")).longValue(),
                ((Number) result.get("width")).intValue(),
                ((Number) result.get("height")).intValue()
            );
        } catch (IOException | RuntimeException exception) {
            // SDK exception có thể chứa response của nhà cung cấp: không đưa vào log/API.
            throw new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
        }
    }

    @Override
    public void delete(String publicId) {
        requireEnabled();
        try {
            Map<?, ?> result = client
                .uploader()
                .destroy(
                    publicId,
                    Map.of("resource_type", "image", "type", "upload", "invalidate", true, "timeout", 20000)
                );
            if (!"ok".equals(result.get("result")) && !"not found".equals(result.get("result"))) {
                throw new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
            }
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.MEDIA_UNAVAILABLE);
        }
    }

    private void requireEnabled() {
        if (!settings.isEnabled()) {
            throw new BusinessException(ErrorCode.MEDIA_DISABLED);
        }
    }
}
