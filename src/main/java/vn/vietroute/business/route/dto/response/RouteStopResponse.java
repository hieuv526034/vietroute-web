package vn.vietroute.business.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

/** Projection tường minh của điểm mẫu; địa chỉ/tọa độ đọc từ danh mục server. */
public record RouteStopResponse(
    @Schema(description = "Thứ tự từ 1") int stopOrder,
    @Schema(description = "ID địa điểm") long locationId,
    @Schema(description = "Tên Việt") String nameVi,
    @Schema(description = "Tên Anh") String nameEn,
    @Schema(description = "Địa chỉ") String address,
    @Schema(description = "Vĩ độ") BigDecimal latitude,
    @Schema(description = "Kinh độ") BigDecimal longitude,
    @Schema(description = "Độ lệch phút đến") int arrivalOffsetMinutes,
    @Schema(description = "Độ lệch phút rời") int departureOffsetMinutes,
    @Schema(description = "Giá cộng dồn VND") BigDecimal cumulativeFare,
    @Schema(description = "Cho phép đón") boolean pickupAllowed,
    @Schema(description = "Cho phép trả") boolean dropoffAllowed
) {}
