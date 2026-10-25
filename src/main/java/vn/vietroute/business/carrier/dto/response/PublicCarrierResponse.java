package vn.vietroute.business.carrier.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Hồ sơ công bố không có scope, audit, phiên bản hoặc dữ liệu nhân sự/khách hàng. */
public record PublicCarrierResponse(
    @Schema(description = "ID nhà xe công khai") long id,
    @Schema(description = "Mã nhà xe") String code,
    @Schema(description = "Tên thương mại") String name,
    @Schema(description = "Điện thoại liên hệ được nhà xe công bố") String phone,
    @Schema(description = "Giới thiệu được công bố") String description,
    @Schema(description = "URL logo READY công khai, null khi chưa có ảnh") String logoUrl
) {}
