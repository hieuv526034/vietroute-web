package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Chỉ công bố khả năng sử dụng, không có credential/provider URL. */
public record AccountOptionsResponse(
    @Schema(description = "Đăng ký/kích hoạt/đặt lại qua email đã được cấu hình mail và worker")
    boolean emailFlowsEnabled,
    @Schema(description = "Số lần sai mật khẩu liên tiếp trước khi khóa") int maxLoginFailures,
    @Schema(description = "Thời gian khóa tạm, phút") int lockMinutes,
    @Schema(description = "Số mật khẩu cũ bị cấm ngoài mật khẩu hiện tại") int previousPasswords
) {}
