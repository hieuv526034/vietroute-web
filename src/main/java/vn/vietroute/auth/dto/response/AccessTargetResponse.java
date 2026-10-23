package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Menu/endpoint thực có trong ứng dụng, dùng chọn chính sách")
public record AccessTargetResponse(
    @Schema(description = "MENU hoặc ENDPOINT") String kind,
    @Schema(description = "Phương thức + template path đã đăng ký") String target,
    @Schema(description = "Controller xử lý, không chứa payload hoặc secret") String handler
) {}
