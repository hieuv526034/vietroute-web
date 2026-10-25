package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.vietroute.business.booking.entity.BookingStatus;

/** Chỉ owner hoặc staff scope đã kiểm được nhận PII liên hệ; không dùng cho public trip lookup. */
public record BookingResponse(
    @Schema(description = "ID đơn") long id,
    @Schema(description = "Mã đơn server") String code,
    @Schema(description = "ID chuyến") long tripId,
    @Schema(description = "Mã chuyến") String tripCode,
    @Schema(description = "ID nhà xe") long carrierId,
    @Schema(description = "Scope DB") long scopeId,
    @Schema(description = "Tên nhà xe") String carrierName,
    @Schema(description = "Điện thoại nhà xe") String carrierPhone,
    @Schema(description = "WEB hoặc PHONE") String source,
    @Schema(description = "ID điểm đón snapshot") long pickupStopId,
    @Schema(description = "ID điểm trả snapshot") long dropoffStopId,
    @Schema(description = "Thứ tự đón") int pickupOrder,
    @Schema(description = "Thứ tự trả") int dropoffOrder,
    @Schema(description = "Tên điểm đón Việt") String pickupNameVi,
    @Schema(description = "Tên điểm đón Anh") String pickupNameEn,
    @Schema(description = "Tên điểm trả Việt") String dropoffNameVi,
    @Schema(description = "Tên điểm trả Anh") String dropoffNameEn,
    @Schema(description = "Dự kiến đón UTC") Instant pickupAt,
    @Schema(description = "Dự kiến trả UTC") Instant dropoffAt,
    @Schema(description = "Số chỗ") int seats,
    @Schema(description = "Giá một chỗ chốt lúc gửi") BigDecimal unitPrice,
    @Schema(description = "Tổng giá chốt") BigDecimal totalPrice,
    @Schema(description = "Người liên hệ") String contactName,
    @Schema(description = "Điện thoại liên hệ") String contactPhone,
    @Schema(description = "Trạng thái đơn") BookingStatus status,
    @Schema(description = "Đã thu thủ công") BigDecimal paidAmount,
    @Schema(description = "Đã hoàn thủ công") BigDecimal refundedAmount,
    @Schema(description = "Có tiền cần xử lý hoàn; không tự coi đã hoàn") boolean refundDue,
    @Schema(description = "Version") long version,
    @Schema(description = "Có quyền sửa theo scope/owner; backend còn kiểm trạng thái khi ghi") boolean canUpdate,
    @Schema(description = "Thời gian tạo UTC") Instant createdAt,
    @Schema(description = "Lịch sử khi đọc chi tiết, tối đa 100 mới nhất") List<BookingEventResponse> events
) {}
