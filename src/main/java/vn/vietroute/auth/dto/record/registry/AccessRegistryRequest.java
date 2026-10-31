package vn.vietroute.auth.dto.record.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Tên hiển thị và trạng thái role/quyền, mã ổn định nằm trên URL")
public record AccessRegistryRequest(
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên tiếng Việt")
    String nameVi,
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên tiếng Anh")
    String nameEn,
    @Schema(description = "Cho phép gán và sử dụng") boolean active,
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version registry đọc gần nhất, tạo mới dùng 0")
    long version
) {}
