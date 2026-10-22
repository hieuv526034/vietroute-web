package vn.vietroute.common.media;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.web.multipart.MultipartFile;
import vn.vietroute.common.config.MediaSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Kiểm nội dung ảnh thật trước khi upload; không tin extension hoặc Content-Type do client khai báo. */
public final class ImageValidator {

    private ImageValidator() {}

    public static byte[] validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.MEDIA_INVALID);
        }
        if (file.getSize() > MediaSettings.MAX_FILE_BYTES) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
        }
        try (var source = file.getInputStream()) {
            byte[] bytes = source.readNBytes(MediaSettings.MAX_FILE_BYTES + 1);
            if (bytes.length > MediaSettings.MAX_FILE_BYTES) {
                throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
            }
            try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) {
                    throw new BusinessException(ErrorCode.MEDIA_INVALID);
                }
                ImageReader reader = readers.next();
                try {
                    reader.setInput(input, true, true);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    int width = reader.getWidth(0);
                    int height = reader.getHeight(0);
                    // Đọc header trước khi giải nén để chặn ảnh nhỏ theo byte nhưng khổng lồ theo pixel.
                    if (
                        !format.matches(Regex.IMAGE_READER_FORMAT) ||
                        width < 1 ||
                        height < 1 ||
                        width > 4096 ||
                        height > 4096 ||
                        (long) width * height > 6000000
                    ) {
                        throw new BusinessException(ErrorCode.MEDIA_INVALID);
                    }
                    BufferedImage image = reader.read(0);
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    // Mã hóa lại chỉ pixel: loại metadata/EXIF và nội dung đính kèm sau ảnh.
                    if (!ImageIO.write(image, format, output) || output.size() > MediaSettings.MAX_FILE_BYTES) {
                        throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE);
                    }
                    return output.toByteArray();
                } finally {
                    reader.dispose();
                }
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(ErrorCode.MEDIA_INVALID);
        }
    }
}
