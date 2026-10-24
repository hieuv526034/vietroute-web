package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;
import java.util.Set;

@Schema(description = "Nguồn cấp quyền hiện tại cho một user, không trả email/hash/token")
public record EffectiveAccessResponse(
    @Schema(description = "ID user") long userId,
    @Schema(description = "Username") String username,
    @Schema(description = "Tài khoản đang hoạt động") boolean active,
    @Schema(description = "Mã quyền -> các role active đang cấp quyền đó") Map<String, Set<String>> sources
) {}
