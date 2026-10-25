package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Projection tối thiểu, không lộ email/credential hay dữ liệu từ nhà xe ngoài quyền. */
public record TripChoiceResponse(
    @Schema(description = "ID lựa chọn") long id,
    @Schema(description = "Mã") String code,
    @Schema(description = "Tên hiển thị") String name,
    @Schema(description = "Số chỗ xe, null với lựa chọn khác") Integer capacity
) {}
