package vn.vietroute.business.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

/** Phiên bản bắt buộc để không xóa nhầm ảnh đã thay đổi kể từ lúc người dùng mở màn hình. */
@Getter
@Setter
public class MediaDeleteRequest extends BaseRequest {

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của ảnh", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long version;
}
