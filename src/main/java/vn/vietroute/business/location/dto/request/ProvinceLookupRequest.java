package vn.vietroute.business.location.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

@Getter
@Setter
@Schema(description = "Tìm danh mục địa lý bằng từ khóa nguyên văn, phân trang tại PostgreSQL")
public class ProvinceLookupRequest extends PageRequest {

    @NotNull(message = "{validation.required}")
    @Size(max = 160, message = "{validation.resourceSearch}")
    @Pattern(regexp = Regex.ACCESS_RESOURCE_SEARCH, message = "{validation.resourceSearch}")
    @Schema(description = "Từ khóa trong mã/tên vi/en, không diễn giải wildcard", maxLength = 160, defaultValue = "")
    private String query = "";
}
