package vn.vietroute.common.job.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;

@Getter
@Setter
@Schema(description = "Chạy lại job DEAD với version để chống thao tác lặp")
public class RetryJobRequest extends BaseRequest {

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version job hiện tại", minimum = "0")
    private long version;
}
