package vn.vietroute.auth.dto.response.account;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ trả định danh để đối chiếu; không trả email, mật khẩu hoặc credential hash. */
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class DriverAccountResponse {

    @Schema(description = "ID tài khoản DRIVER mới")
    private long userId;

    @Schema(description = "Username đã chuẩn hóa")
    private String username;

    @Schema(description = "ID hồ sơ đã liên kết")
    private long driverId;

    @Schema(description = "Version hồ sơ sau gán")
    private long driverVersion;

    @Schema(description = "Luôn true, phải đổi mật khẩu tạm")
    private boolean mustChangePassword;

    @Schema(description = "Luôn true, phải xác minh email trước nghiệp vụ")
    private boolean emailVerificationRequired;

    @Schema(description = "Luôn false; bật hồ sơ sau khi account hoàn tất thiết lập")
    private boolean driverActive;

    public DriverAccountResponse(
        long userId,
        String username,
        long driverId,
        long driverVersion,
        boolean mustChangePassword,
        boolean emailVerificationRequired,
        boolean driverActive
    ) {
        this.userId = userId;
        this.username = username;
        this.driverId = driverId;
        this.driverVersion = driverVersion;
        this.mustChangePassword = mustChangePassword;
        this.emailVerificationRequired = emailVerificationRequired;
        this.driverActive = driverActive;
    }

    public long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public long driverId() {
        return driverId;
    }

    public long driverVersion() {
        return driverVersion;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public boolean emailVerificationRequired() {
        return emailVerificationRequired;
    }

    public boolean driverActive() {
        return driverActive;
    }
}
