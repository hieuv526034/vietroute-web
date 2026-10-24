package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Kết quả khôi phục admin; các phiên cũ bị thu hồi, cần đăng nhập lại")
public record AdminRecoveryResponse(
    @Schema(description = "ID tài khoản bootstrap đã khôi phục") long userId,
    @Schema(description = "Số mapping permission của ADMIN sau khi khôi phục") int permissionCount,
    @Schema(description = "Phải đăng nhập lại sau khi phục hồi") boolean loginRequired
) {}
