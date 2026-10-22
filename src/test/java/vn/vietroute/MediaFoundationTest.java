package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.vietroute.business.media.dto.response.MediaResponse;
import vn.vietroute.business.media.entity.MediaAsset;
import vn.vietroute.common.config.MediaSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.media.CloudinaryMediaStorage;
import vn.vietroute.common.media.ImageValidator;

/** Kiểm ranh giới file/config và dữ liệu công bố; không gọi Cloudinary hoặc database thật. */
class MediaFoundationTest {

    @Test
    void reencodesPixelsAndRemovesAttachedDataWithoutTrustingMimeOrFilename() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(3, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        byte[] original = output.toByteArray();
        output.write("secret-exif-or-polyglot-data".getBytes(StandardCharsets.UTF_8));
        byte[] clean = ImageValidator.validate(
            new MockMultipartFile("file", "fake.svg", "text/plain", output.toByteArray())
        );
        assertArrayEquals(original, clean);
        var decoded = ImageIO.read(new ByteArrayInputStream(clean));
        assertEquals(3, decoded.getWidth());
        assertEquals(2, decoded.getHeight());
        assertFalse(new String(clean, StandardCharsets.ISO_8859_1).contains("secret-exif"));
    }

    @Test
    void rejectsSvgFakeJpegEmptyAndOversizedUploads() {
        for (byte[] invalid : new byte[][] {
            new byte[0],
            "<svg><script/></svg>".getBytes(StandardCharsets.UTF_8),
            "not-an-image".getBytes(StandardCharsets.UTF_8),
        }) {
            BusinessException error = assertThrows(BusinessException.class, () ->
                ImageValidator.validate(new MockMultipartFile("file", "photo.jpg", "image/jpeg", invalid))
            );
            assertEquals(ErrorCode.MEDIA_INVALID, error.getErrorCode());
        }
        var error = assertThrows(BusinessException.class, () ->
            ImageValidator.validate(new MockMultipartFile("file", new byte[MediaSettings.MAX_FILE_BYTES + 1]))
        );
        assertEquals(ErrorCode.PAYLOAD_TOO_LARGE, error.getErrorCode());
    }

    @Test
    void rejectsPixelBombBeforeDecodingAndRejectsTruncatedImage() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), "png", output);
        assertEquals(
            ErrorCode.MEDIA_INVALID,
            assertThrows(BusinessException.class, () ->
                ImageValidator.validate(new MockMultipartFile("file", output.toByteArray()))
            ).getErrorCode()
        );
        byte[] truncated = Arrays.copyOf(output.toByteArray(), 30);
        assertThrows(BusinessException.class, () -> ImageValidator.validate(new MockMultipartFile("file", truncated)));
    }

    @Test
    void validatesCredentialsAndRequiresWorkerOnlyWhenEnabled() {
        var disabled = new MediaSettings(Map.<String, String>of()::get);
        assertFalse(disabled.isEnabled());
        assertThrows(IllegalStateException.class, () ->
            new MediaSettings(Map.of("VIETROUTE_MEDIA_ENABLED", "true")::get)
        );
        var config = Map.of(
            "VIETROUTE_MEDIA_ENABLED",
            "true",
            "VIETROUTE_CLOUDINARY_CLOUD_NAME",
            "demo",
            "VIETROUTE_CLOUDINARY_API_KEY",
            "1234",
            "VIETROUTE_CLOUDINARY_API_SECRET",
            "test-only-secret",
            "VIETROUTE_WORKER_ENABLED",
            "true"
        );
        assertTrue(new MediaSettings(config::get).isEnabled());
        var noWorker = new HashMap<>(config);
        noWorker.put("VIETROUTE_WORKER_ENABLED", "false");
        assertThrows(IllegalStateException.class, () -> new MediaSettings(noWorker::get));
        assertThrows(IllegalStateException.class, () ->
            new MediaSettings(Map.of("VIETROUTE_CLOUDINARY_FOLDER", "../other")::get)
        );
        var transport = new CloudinaryMediaStorage(disabled);
        assertEquals(
            ErrorCode.MEDIA_DISABLED,
            assertThrows(BusinessException.class, () -> transport.upload("any", new byte[0])).getErrorCode()
        );
    }

    @Test
    void hidesPublicUrlsUntilReadyAndAfterDeletionWasRequested() {
        var image = MediaAsset.builder()
            .id(1L)
            .status("READY")
            .publicId("internal-id")
            .url("https://res.cloudinary.com/demo/image/upload/a.png")
            .thumbnailUrl("https://res.cloudinary.com/demo/a.png")
            .build();
        assertEquals(image.getUrl(), MediaResponse.from(image).getUrl());
        image.setStatus("DELETING");
        assertEquals(null, MediaResponse.from(image).getUrl());
        assertEquals(null, MediaResponse.from(image).getThumbnailUrl());
    }
}
