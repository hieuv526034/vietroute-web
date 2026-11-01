package vn.vietroute.auth.dto.response.recovery;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Kết quả khôi phục admin; các phiên cũ bị thu hồi, cần đăng nhập lại")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AdminRecoveryResponse {

    @Schema(description = "ID tài khoản bootstrap đã khôi phục")
    private long userId;

    @Schema(description = "Số mapping permission của ADMIN sau khi khôi phục")
    private int permissionCount;

    @Schema(description = "Phải đăng nhập lại sau khi phục hồi")
    private boolean loginRequired;

    public AdminRecoveryResponse(long userId, int permissionCount, boolean loginRequired) {
        this.userId = userId;
        this.permissionCount = permissionCount;
        this.loginRequired = loginRequired;
    }

    public long userId() {
        return userId;
    }

    public int permissionCount() {
        return permissionCount;
    }

    public boolean loginRequired() {
        return loginRequired;
    }
}
