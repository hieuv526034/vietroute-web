package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

/** API client nhận Bearer token; giao diện chỉ sử dụng cookie HttpOnly do server đặt. */
@Schema(description = "Kết quả phát JWT sau đăng nhập hoặc đổi mật khẩu")
public record AuthResponse(
        @Schema(description = "JWT access token; không lưu localStorage") String accessToken,
        @Schema(description = "Kiểu token", example = "Bearer") String tokenType,
        @Schema(description = "Thời điểm hết hạn theo UTC") Instant expiresAt,
        @Schema(description = "Thông tin tài khoản") UserResponse user) { }
