package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ công bố khả năng sử dụng, không có credential/provider URL. */
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccountOptionsResponse {

    @Schema(description = "Đăng ký/kích hoạt/đặt lại qua email đã được cấu hình mail và worker")
    private boolean emailFlowsEnabled;

    @Schema(description = "Số lần sai mật khẩu liên tiếp trước khi khóa")
    private int maxLoginFailures;

    @Schema(description = "Thời gian khóa tạm, phút")
    private int lockMinutes;

    @Schema(description = "Số mật khẩu cũ bị cấm ngoài mật khẩu hiện tại")
    private int previousPasswords;

    public AccountOptionsResponse(
        boolean emailFlowsEnabled,
        int maxLoginFailures,
        int lockMinutes,
        int previousPasswords
    ) {
        this.emailFlowsEnabled = emailFlowsEnabled;
        this.maxLoginFailures = maxLoginFailures;
        this.lockMinutes = lockMinutes;
        this.previousPasswords = previousPasswords;
    }

    public boolean emailFlowsEnabled() {
        return emailFlowsEnabled;
    }

    public int maxLoginFailures() {
        return maxLoginFailures;
    }

    public int lockMinutes() {
        return lockMinutes;
    }

    public int previousPasswords() {
        return previousPasswords;
    }
}
