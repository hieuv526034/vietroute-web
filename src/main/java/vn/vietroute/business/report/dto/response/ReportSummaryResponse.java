package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Snapshot tổng hợp không PII; tiền là số hiện hành của đơn thuộc nhóm chuyến, không phải cashflow theo ngày thu. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ReportSummaryResponse {

    @Schema(description = "Ngày đầu tại Việt Nam")
    private LocalDate fromDate;

    @Schema(description = "Ngày cuối tại Việt Nam")
    private LocalDate toDate;

    @Schema(description = "Nhà xe đã chọn, null nghĩa là toàn phạm vi được phép")
    private Long carrierId;

    @Schema(description = "Thời điểm snapshot PostgreSQL UTC")
    private Instant asOf;

    @Schema(description = "Tổng số chuyến khởi hành trong khoảng")
    private long tripCount;

    @Schema(description = "Số chuyến theo trạng thái, gồm DRAFT và CANCELLED")
    private List<ReportStatusResponse> trips;

    @Schema(description = "Tổng yêu cầu WEB/PHONE của các chuyến trong khoảng")
    private long bookingCount;

    @Schema(description = "Số đơn theo trạng thái")
    private List<ReportStatusResponse> bookings;

    @Schema(description = "Lượt chỗ của đơn CONFIRMED/COMPLETED, không phải ghế vật lý duy nhất")
    private long confirmedSeats;

    @Schema(description = "Tổng giá chốt của đơn CONFIRMED/COMPLETED, không phải tiền đã thu")
    private BigDecimal confirmedValue;

    @Schema(description = "Tổng tiền đã ghi nhận thu của mọi trạng thái đơn trong nhóm chuyến")
    private BigDecimal paidAmount;

    @Schema(description = "Tổng tiền đã ghi nhận hoàn của mọi trạng thái đơn trong nhóm chuyến")
    private BigDecimal refundedAmount;

    @Schema(description = "Tiền thu trừ tiền hoàn của nhóm chuyến")
    private BigDecimal netAmount;

    @Schema(description = "Tiền đã thu chưa hoàn của đơn CANCELLED, cần xử lý riêng")
    private BigDecimal refundOutstanding;
}
