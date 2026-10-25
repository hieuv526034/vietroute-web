package vn.vietroute.business.report.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Snapshot tổng hợp không PII; tiền là số hiện hành của đơn thuộc nhóm chuyến, không phải cashflow theo ngày thu. */
public record ReportSummaryResponse(
    @Schema(description = "Ngày đầu tại Việt Nam") LocalDate fromDate,
    @Schema(description = "Ngày cuối tại Việt Nam") LocalDate toDate,
    @Schema(description = "Nhà xe đã chọn, null nghĩa là toàn phạm vi được phép") Long carrierId,
    @Schema(description = "Thời điểm snapshot PostgreSQL UTC") Instant asOf,
    @Schema(description = "Tổng số chuyến khởi hành trong khoảng") long tripCount,
    @Schema(description = "Số chuyến theo trạng thái, gồm DRAFT và CANCELLED") List<ReportStatusResponse> trips,
    @Schema(description = "Tổng yêu cầu WEB/PHONE của các chuyến trong khoảng") long bookingCount,
    @Schema(description = "Số đơn theo trạng thái") List<ReportStatusResponse> bookings,
    @Schema(description = "Lượt chỗ của đơn CONFIRMED/COMPLETED, không phải ghế vật lý duy nhất") long confirmedSeats,
    @Schema(description = "Tổng giá chốt của đơn CONFIRMED/COMPLETED, không phải tiền đã thu")
    BigDecimal confirmedValue,
    @Schema(description = "Tổng tiền đã ghi nhận thu của mọi trạng thái đơn trong nhóm chuyến") BigDecimal paidAmount,
    @Schema(description = "Tổng tiền đã ghi nhận hoàn của mọi trạng thái đơn trong nhóm chuyến")
    BigDecimal refundedAmount,
    @Schema(description = "Tiền thu trừ tiền hoàn của nhóm chuyến") BigDecimal netAmount,
    @Schema(description = "Tiền đã thu chưa hoàn của đơn CANCELLED, cần xử lý riêng") BigDecimal refundOutstanding
) {}
