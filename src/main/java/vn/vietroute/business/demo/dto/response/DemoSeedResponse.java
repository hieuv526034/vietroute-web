package vn.vietroute.business.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Chỉ trả số lượng, không trả credentials/account hoặc cấu hình môi trường. */
public record DemoSeedResponse(
    @Schema(description = "Số bản ghi mới tạo trong lần chạy") int created,
    @Schema(description = "Số bản ghi DEMO đã có, giữ nguyên") int reused,
    @Schema(description = "Số mục danh mục trong bộ dữ liệu") int catalogs,
    @Schema(description = "Số tỉnh minh họa") int provinces,
    @Schema(description = "Số địa điểm minh họa") int locations,
    @Schema(description = "Số nhà xe minh họa") int carriers,
    @Schema(description = "Số xe minh họa") int vehicles,
    @Schema(description = "Số hồ sơ tài xế minh họa, chưa tạo tài khoản") int drivers
) {}
