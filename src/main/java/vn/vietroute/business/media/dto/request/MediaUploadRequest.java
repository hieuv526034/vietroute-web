package vn.vietroute.business.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.web.multipart.MultipartFile;
import vn.vietroute.common.base.BaseRequest;

/** Chỉ nhận file; owner, URL, publicId và audit hoàn toàn do server tạo. */
@Getter
@Setter
@Schema(description = "Upload một ảnh công khai JPEG/PNG, tối đa 5 MiB, 4096 px mỗi chiều và 6 triệu pixel")
public class MediaUploadRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Schema(
        description = "Nội dung file ảnh thật; không nhận URL hoặc SVG",
        type = "string",
        format = "binary",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    private MultipartFile file;
}
