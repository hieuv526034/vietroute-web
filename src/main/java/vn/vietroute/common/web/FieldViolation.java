package vn.vietroute.common.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** Không trả rejectedValue để tránh lộ mật khẩu/dữ liệu cá nhân trong lỗi validation. */
@Schema(description = "Lỗi của một trường đầu vào")
public record FieldViolation(
        @Schema(description = "Tên trường", example = "username") String field,
        @Schema(description = "Mã validation ổn định", example = "VAL001") String code,
        @Schema(description = "Key dịch", example = "validation.required") String messageKey,
        @Schema(description = "Thông báo theo locale") String message) { }
