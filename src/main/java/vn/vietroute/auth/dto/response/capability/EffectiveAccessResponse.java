package vn.vietroute.auth.dto.response.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Nguồn cấp quyền hiện tại cho một user, không trả email/hash/token")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class EffectiveAccessResponse {

    @Schema(description = "ID user")
    private long userId;

    @Schema(description = "Username")
    private String username;

    @Schema(description = "Tài khoản đang hoạt động")
    private boolean active;

    @Schema(
        description = "Mã quyền -> các role active đang cấp quyền đó; rỗng khi tài khoản tắt, pending, dùng mật khẩu tạm hoặc chưa xác minh email"
    )
    private Map<String, Set<String>> sources;

    public EffectiveAccessResponse(long userId, String username, boolean active, Map<String, Set<String>> sources) {
        this.userId = userId;
        this.username = username;
        this.active = active;
        this.sources = sources;
    }

    public long userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public boolean active() {
        return active;
    }

    public Map<String, Set<String>> sources() {
        return sources;
    }
}
