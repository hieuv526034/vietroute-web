package vn.vietroute.common.monitoring.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Bộ lọc trên snapshot RAM, không nhận biểu thức truy vấn tùy ý. */
@Getter
@Setter
public class ApiJournalSearchRequest extends PageRequest {

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = Regex.JOURNAL_TRACE, message = "{validation.invalid}")
    @Schema(description = "traceId chính xác, rỗng để xem tất cả")
    private String traceId = "";

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = Regex.JOURNAL_ENDPOINT, message = "{validation.invalid}")
    @Schema(description = "Lọc chuỗi route template, tối đa 180 ký tự")
    private String endpoint = "";

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = "|" + Regex.API_METHOD, message = "{validation.invalid}")
    @Schema(description = "GET/POST/PUT/DELETE/PATCH/HEAD/OPTIONS hoặc rỗng")
    private String method = "";

    @Min(value = 0, message = "{validation.invalid}")
    @Max(value = 599, message = "{validation.invalid}")
    @Schema(description = "HTTP status chính xác; 0 để bỏ lọc")
    private int status;

    @Min(value = 0, message = "{validation.invalid}")
    @Max(value = 600000, message = "{validation.invalid}")
    @Schema(description = "Thời gian tối thiểu, ms")
    private long minDurationMs;

    @NotNull(message = "{validation.required}")
    @Pattern(regexp = Regex.JOURNAL_CODE, message = "{validation.invalid}")
    @Schema(description = "Mã kết quả chính xác, ví dụ COM007")
    private String code = "";

    @Schema(description = "Chỉ lỗi HTTP 4xx/5xx")
    private boolean errorsOnly;
}
