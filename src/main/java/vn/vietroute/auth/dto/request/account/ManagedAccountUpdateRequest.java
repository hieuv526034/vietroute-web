package vn.vietroute.auth.dto.request.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Sửa tên hiển thị; username bất biến, email/mật khẩu theo vòng đời xác minh riêng")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ManagedAccountUpdateRequest {

    @Schema(description = "Tên hiển thị tài khoản")
    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.length}")
    private String displayName;

    @Schema(description = "Version hiện tại", minimum = "0")
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    private Long version;

    public ManagedAccountUpdateRequest(String displayName, Long version) {
        this.displayName = displayName;
        this.version = version;
    }

    public String displayName() {
        return displayName;
    }

    public Long version() {
        return version;
    }
}
