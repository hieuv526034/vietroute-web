package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Danh sách phục vụ đón/trả cho đúng tài xế được giao; không gồm email/customer ID/giá/tiền/audit. */
public record DriverPassengerResponse(
    @Schema(description = "Mã đơn") String bookingCode,
    @Schema(description = "Thứ tự đón") int pickupOrder,
    @Schema(description = "Thứ tự trả") int dropoffOrder,
    @Schema(description = "Điểm đón Việt") String pickupNameVi,
    @Schema(description = "Điểm đón Anh") String pickupNameEn,
    @Schema(description = "Điểm trả Việt") String dropoffNameVi,
    @Schema(description = "Điểm trả Anh") String dropoffNameEn,
    @Schema(description = "Số chỗ") int seats,
    @Schema(description = "Liên hệ đón trả") String contactName,
    @Schema(description = "Điện thoại liên hệ") String contactPhone,
    @Schema(description = "CONFIRMED hoặc COMPLETED") String status
) {}
