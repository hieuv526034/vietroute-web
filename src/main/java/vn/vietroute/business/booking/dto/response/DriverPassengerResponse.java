package vn.vietroute.business.booking.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Danh sách phục vụ đón/trả cho đúng tài xế được giao; không gồm email/customer ID/giá/tiền/audit. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DriverPassengerResponse {

    @Schema(description = "Mã đơn")
    private String bookingCode;

    @Schema(description = "Thứ tự đón")
    private int pickupOrder;

    @Schema(description = "Thứ tự trả")
    private int dropoffOrder;

    @Schema(description = "Điểm đón Việt")
    private String pickupNameVi;

    @Schema(description = "Điểm đón Anh")
    private String pickupNameEn;

    @Schema(description = "Điểm trả Việt")
    private String dropoffNameVi;

    @Schema(description = "Điểm trả Anh")
    private String dropoffNameEn;

    @Schema(description = "Số chỗ")
    private int seats;

    @Schema(description = "Liên hệ đón trả")
    private String contactName;

    @Schema(description = "Điện thoại liên hệ")
    private String contactPhone;

    @Schema(description = "CONFIRMED hoặc COMPLETED")
    private String status;
}
