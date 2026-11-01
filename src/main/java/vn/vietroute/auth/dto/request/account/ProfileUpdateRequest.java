package vn.vietroute.auth.dto.request.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Cập nhật hồ sơ của chính principal; không nhận user ID, role, email hay audit")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ProfileUpdateRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên hiển thị, không để trống")
    private String displayName;

    @Positive(message = "{validation.invalid}")
    @Schema(description = "ID ảnh READY thuộc chính mình; null để bỏ ảnh đại diện")
    private Long avatarMediaId;

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version mới nhất từ auth/me, chặn ghi đè đồng thời")
    private Long version;

    public ProfileUpdateRequest(String displayName, Long avatarMediaId, Long version) {
        this.displayName = displayName;
        this.avatarMediaId = avatarMediaId;
        this.version = version;
    }

    public String displayName() {
        return displayName;
    }

    public Long avatarMediaId() {
        return avatarMediaId;
    }

    public Long version() {
        return version;
    }
}
