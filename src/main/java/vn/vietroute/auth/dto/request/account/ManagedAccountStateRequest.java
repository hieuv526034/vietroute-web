package vn.vietroute.auth.dto.request.account;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Khóa/mở tài khoản bằng version; mọi phiên cũ đều bị thu hồi")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class ManagedAccountStateRequest {

    @NotNull(message = "{validation.required}")
    @Schema(description = "true để mở, false để khóa")
    private Boolean active;

    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version hiện hành của tài khoản", minimum = "0")
    private Long version;

    public ManagedAccountStateRequest(Boolean active, Long version) {
        this.active = active;
        this.version = version;
    }

    public Boolean active() {
        return active;
    }

    public Long version() {
        return version;
    }
}
