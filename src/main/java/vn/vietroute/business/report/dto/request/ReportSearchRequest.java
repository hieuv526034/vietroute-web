package vn.vietroute.business.report.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import vn.vietroute.common.constant.ApplicationConstants.Business;

/** Khoảng ngày khởi hành tại Việt Nam; không nhận scope hoặc actor từ client. */
@Getter
@Setter
@Schema(description = "Báo cáo theo nhóm chuyến khởi hành trong khoảng ngày Việt Nam, tối đa 366 ngày")
public class ReportSearchRequest {

    @NotNull(message = "{validation.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Ngày đầu bao gồm cận, mặc định 29 ngày trước hôm nay", example = "2026-10-01")
    private LocalDate fromDate = LocalDate.now(ZoneId.of(Business.TIME_ZONE)).minusDays(29);

    @NotNull(message = "{validation.required}")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Schema(description = "Ngày cuối bao gồm cận, mặc định hôm nay", example = "2026-10-31")
    private LocalDate toDate = LocalDate.now(ZoneId.of(Business.TIME_ZONE));

    @Min(value = 1, message = "{validation.invalid}")
    @Schema(description = "Nhà xe trong quyền REPORT_READ scoped; bỏ trống để tổng hợp toàn phạm vi được phép")
    private Long carrierId;

    @AssertTrue(message = "{validation.reportDateRange}")
    @Schema(hidden = true)
    public boolean isDateRangeValid() {
        return (
            fromDate != null &&
            toDate != null &&
            !toDate.isBefore(fromDate) &&
            ChronoUnit.DAYS.between(fromDate, toDate) < 366 &&
            fromDate.getYear() > 0 &&
            toDate.getYear() < 9999
        );
    }
}
