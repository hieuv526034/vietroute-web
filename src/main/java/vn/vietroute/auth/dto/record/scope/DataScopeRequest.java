package vn.vietroute.auth.dto.record.scope;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

@Schema(description = "Metadata phạm vi quản trị; không phải trạng thái nhận booking của nhà xe")
public record DataScopeRequest(
    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.DATA_SCOPE_CODE, message = "{validation.code}")
    @Size(max = 50, message = "{validation.invalid}")
    @Schema(description = "Mã bất biến, chữ hoa/số/gạch dưới")
    String code,
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên phạm vi tiếng Việt")
    String nameVi,
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên phạm vi tiếng Anh")
    String nameEn,
    @Schema(description = "Bật phạm vi bảo mật; tắt sẽ chặn toàn bộ truy cập scoped") boolean active,
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version đọc gần nhất, tạo dùng 0")
    long version
) {}
