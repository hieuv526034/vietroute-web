package vn.vietroute.common.media.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Cập nhật thông tin ảnh, không nhận public ID/owner hoặc URL từ client")
public record MediaUpdateRequest(
    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.invalid}")
    @Schema(description = "Tên ảnh dễ tra cứu")
    String title,
    @NotNull(message = "{validation.required}")
    @Size(max = 300, message = "{validation.invalid}")
    @Schema(description = "Mô tả thay thế hỗ trợ người dùng trình đọc màn hình")
    String altText,
    @Min(value = 0, message = "{validation.invalid}") @Schema(description = "Version ảnh đọc gần nhất") long version
) {}
