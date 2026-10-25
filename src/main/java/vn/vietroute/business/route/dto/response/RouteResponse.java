package vn.vietroute.business.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

/** Response không có tenant/audit do client tự gán; điểm chi tiết chỉ nạp ở GET theo ID. */
public record RouteResponse(
    @Schema(description = "ID tuyến") long id,
    @Schema(description = "ID nhà xe") long carrierId,
    @Schema(description = "ID scope từ DB") long scopeId,
    @Schema(description = "Tên nhà xe") String carrierName,
    @Schema(description = "Mã tuyến bất biến") String code,
    @Schema(description = "Tên Việt") String nameVi,
    @Schema(description = "Tên Anh") String nameEn,
    @Schema(description = "Mô tả") String description,
    @Schema(description = "Đang sử dụng") boolean active,
    @Schema(description = "Số điểm") int stopCount,
    @Schema(description = "Version chống ghi đè") long version,
    @Schema(description = "Thời điểm tạo UTC") Instant createdAt,
    @Schema(description = "Thời điểm sửa UTC") Instant updatedAt,
    @Schema(description = "Actor tạo") long createdBy,
    @Schema(description = "Actor sửa") long updatedBy,
    @Schema(description = "Có quyền UPDATE trong scope; backend luôn kiểm lại") boolean canUpdate,
    @Schema(description = "Danh sách điểm khi đọc chi tiết, rỗng trên trang danh sách") List<RouteStopResponse> stops
) {}
