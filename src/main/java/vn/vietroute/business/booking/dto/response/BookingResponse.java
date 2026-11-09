package vn.vietroute.business.booking.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.business.booking.constant.BookingStatus;

/** Chỉ owner hoặc staff scope đã kiểm được nhận PII liên hệ; không dùng cho public trip lookup. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BookingResponse {

    @Schema(description = "ID đơn")
    private long id;

    @Schema(description = "Mã đơn server")
    private String code;

    @Schema(description = "ID chuyến")
    private long tripId;

    @Schema(description = "Mã chuyến")
    private String tripCode;

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "Scope DB")
    private long scopeId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Điện thoại nhà xe")
    private String carrierPhone;

    @Schema(description = "WEB hoặc PHONE")
    private String source;

    @Schema(description = "ID điểm đón snapshot")
    private long pickupStopId;

    @Schema(description = "ID điểm trả snapshot")
    private long dropoffStopId;

    @Schema(description = "Thứ tự đón")
    private int pickupOrder;

    @Schema(description = "Thứ tự trả")
    private int dropoffOrder;

    @Schema(description = "Tên điểm đón Việt")
    private String pickupNameVi;

    @Schema(description = "Tên điểm đón Anh")
    private String pickupNameEn;

    @Schema(description = "Tên điểm trả Việt")
    private String dropoffNameVi;

    @Schema(description = "Tên điểm trả Anh")
    private String dropoffNameEn;

    @Schema(description = "Dự kiến đón UTC")
    private Instant pickupAt;

    @Schema(description = "Dự kiến trả UTC")
    private Instant dropoffAt;

    @Schema(description = "Số chỗ")
    private int seats;

    @Schema(description = "Giá một chỗ chốt lúc gửi")
    private BigDecimal unitPrice;

    @Schema(description = "Tổng giá chốt")
    private BigDecimal totalPrice;

    @Schema(description = "Người liên hệ")
    private String contactName;

    @Schema(description = "Điện thoại liên hệ")
    private String contactPhone;

    @Schema(description = "Trạng thái đơn")
    private BookingStatus status;

    @Schema(description = "Đã thu thủ công")
    private BigDecimal paidAmount;

    @Schema(description = "Đã hoàn thủ công")
    private BigDecimal refundedAmount;

    @Schema(description = "Có tiền cần xử lý hoàn; không tự coi đã hoàn")
    private boolean refundDue;

    @Schema(description = "Version")
    private long version;

    @Schema(description = "Có quyền sửa theo scope/owner; backend còn kiểm trạng thái khi ghi")
    private boolean canUpdate;

    @Schema(description = "Thời gian tạo UTC")
    private Instant createdAt;

    @Schema(description = "Lịch sử khi đọc chi tiết, tối đa 100 mới nhất")
    private List<BookingEventResponse> events;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Tên tài xế đang phân công; chỉ chi tiết đơn CONFIRMED của chủ đơn trên chuyến còn hoạt động")
    private String driverName;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Điện thoại tài xế để chủ đơn đã xác nhận liên hệ đón trả")
    private String driverPhone;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "Biển số theo phân công chuyến hiện hành; cùng điều kiện công bố liên hệ tài xế")
    private String plateNumber;
}
