package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Chỉ trả định danh để đối chiếu; không trả email, mật khẩu hoặc credential hash. */
public record DriverAccountResponse(
    @Schema(description = "ID tài khoản DRIVER mới") long userId,
    @Schema(description = "Username đã chuẩn hóa") String username,
    @Schema(description = "ID hồ sơ đã liên kết") long driverId,
    @Schema(description = "Version hồ sơ sau gán") long driverVersion,
    @Schema(description = "Luôn true, phải đổi mật khẩu tạm") boolean mustChangePassword,
    @Schema(description = "Luôn true, phải xác minh email trước nghiệp vụ") boolean emailVerificationRequired,
    @Schema(description = "Luôn false; bật hồ sơ sau khi account hoàn tất thiết lập") boolean driverActive
) {}
