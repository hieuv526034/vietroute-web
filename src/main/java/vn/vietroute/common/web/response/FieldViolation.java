package vn.vietroute.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/** Không trả rejectedValue để tránh lộ mật khẩu/dữ liệu cá nhân trong lỗi validation. */
@Schema(description = "Lỗi của một trường đầu vào")
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public class FieldViolation {

    @Schema(description = "Tên trường", example = "username")
    private final String field;

    @Schema(description = "Mã validation ổn định", example = "VAL001")
    private final String code;

    @Schema(description = "Key dịch", example = "validation.required")
    private final String messageKey;

    @Schema(description = "Thông báo theo locale")
    private final String message;
}
