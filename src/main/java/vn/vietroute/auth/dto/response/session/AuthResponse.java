package vn.vietroute.auth.dto.response.session;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.dto.response.account.UserResponse;

/** API client nhận Bearer token; giao diện chỉ sử dụng cookie HttpOnly do server đặt. */
@Schema(description = "Kết quả phát JWT sau đăng nhập hoặc đổi mật khẩu")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AuthResponse {

    @Schema(description = "JWT access token; không lưu localStorage")
    private String accessToken;

    @Schema(description = "Kiểu token", example = "Bearer")
    private String tokenType;

    @Schema(description = "Thời điểm hết hạn theo UTC")
    private Instant expiresAt;

    @Schema(description = "Thông tin tài khoản")
    private UserResponse user;

    public AuthResponse(String accessToken, String tokenType, Instant expiresAt, UserResponse user) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresAt = expiresAt;
        this.user = user;
    }

    public String accessToken() {
        return accessToken;
    }

    public String tokenType() {
        return tokenType;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public UserResponse user() {
        return user;
    }
}
