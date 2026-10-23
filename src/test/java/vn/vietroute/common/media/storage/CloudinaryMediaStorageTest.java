package vn.vietroute.common.media.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudinary.Cloudinary;
import com.cloudinary.ProgressCallback;
import com.cloudinary.Uploader;
import com.cloudinary.strategies.AbstractUploaderStrategy;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.config.integration.MediaSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.media.storage.CloudinaryMediaStorage;

/** Giả client SDK, kiểm option an toàn/URL và lỗi; không có request mạng thật. */
class CloudinaryMediaStorageTest {

    private final MediaSettings settings = new MediaSettings(
        Map.of(
            "VIETROUTE_MEDIA_ENABLED",
            "true",
            "VIETROUTE_CLOUDINARY_CLOUD_NAME",
            "demo",
            "VIETROUTE_CLOUDINARY_API_KEY",
            "1234",
            "VIETROUTE_CLOUDINARY_API_SECRET",
            "unit-test-secret",
            "VIETROUTE_WORKER_ENABLED",
            "true"
        )::get
    );

    @Test
    void usesServerIdNoOverwriteTimeoutAndFixedHttpsHost() {
        var sdk = new FakeCloudinary();
        var adapter = new CloudinaryMediaStorage(settings, sdk);
        var image = adapter.upload("vietroute/server-uuid", new byte[] { 1 });
        assertEquals("https://res.cloudinary.com/demo/image/upload/v5/vietroute/server-uuid.png", image.url());
        assertTrue(image.thumbnailUrl().contains("c_limit,w_640,h_480,q_auto,f_auto/"));
        assertEquals(false, sdk.options.get("overwrite"));
        assertEquals("image", sdk.options.get("resource_type"));
        assertEquals(20000, sdk.options.get("timeout"));
        assertEquals(64, image.width());
        adapter.delete("vietroute/server-uuid");
        assertEquals(true, sdk.options.get("invalidate"));
    }

    @Test
    void sanitizesProviderErrorsAndRejectsUnexpectedPublicId() {
        var sdk = new FakeCloudinary();
        sdk.fail = true;
        var adapter = new CloudinaryMediaStorage(settings, sdk);
        var error = assertThrows(BusinessException.class, () -> adapter.upload("vietroute/server-uuid", new byte[1]));
        assertEquals(ErrorCode.MEDIA_UNAVAILABLE, error.getErrorCode());
        assertFalse(error.toString().contains("provider-secret"));
        assertThrows(BusinessException.class, () -> adapter.delete("vietroute/server-uuid"));
        sdk.fail = false;
        assertThrows(BusinessException.class, () -> adapter.upload("unexpected-id", new byte[1]));
    }

    @SuppressWarnings({ "rawtypes", "unchecked" }) // Chữ ký override do SDK Cloudinary còn dùng Map raw.
    private static class FakeCloudinary extends Cloudinary {

        private Map options;

        private boolean fail;

        @Override
        public Uploader uploader() {
            return new Uploader(
                this,
                new AbstractUploaderStrategy() {
                    @Override
                    public Map callApi(
                        String action,
                        Map<String, Object> params,
                        Map options,
                        Object file,
                        ProgressCallback callback
                    ) {
                        throw new AssertionError("Test không được gọi mạng thật");
                    }
                }
            ) {
                @Override
                public Map upload(Object file, Map requested) throws IOException {
                    options = requested;
                    if (fail) {
                        throw new IOException("provider-secret");
                    }
                    return Map.of(
                        "public_id",
                        "vietroute/server-uuid",
                        "version",
                        5,
                        "format",
                        "png",
                        "bytes",
                        128,
                        "width",
                        64,
                        "height",
                        32,
                        "secure_url",
                        "https://untrusted.example/ignore"
                    );
                }

                @Override
                public Map destroy(String id, Map requested) throws IOException {
                    options = requested;
                    if (fail) {
                        throw new IOException("provider-secret");
                    }
                    return Map.of("result", "not found");
                }
            };
        }
    }
}
