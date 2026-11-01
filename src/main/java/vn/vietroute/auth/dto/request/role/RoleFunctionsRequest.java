package vn.vietroute.auth.dto.request.role;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Thay tập chức năng của role; rỗng là thu hồi toàn bộ */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Thay tập chức năng của role; rỗng là thu hồi toàn bộ")
public class RoleFunctionsRequest {

    @Schema(description = "Mã chức năng được chọn")
    @NotNull(message = "{validation.required}")
    @Size(max = 512, message = "{validation.invalid}")
    private Set<@Pattern(regexp = Regex.FUNCTION_CODE, message = "{validation.invalid}") String> functionCodes;

    @Schema(description = "Version mapping role đọc gần nhất")
    @Min(value = 0, message = "{validation.invalid}")
    @NotNull(message = "{validation.required}")
    private Long version;
}
