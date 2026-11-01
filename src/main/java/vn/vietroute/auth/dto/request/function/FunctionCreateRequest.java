package vn.vietroute.auth.dto.request.function;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Tạo chức năng và chọn Menu/API từ danh bạ server */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tạo chức năng và chọn Menu/API từ danh bạ server")
public class FunctionCreateRequest {

    @Schema(description = "Mã bất biến, ví dụ BOOKING_MANAGEMENT")
    @NotBlank(message = "{validation.required}")
    @Pattern(regexp = Regex.FUNCTION_CODE, message = "{validation.invalid}")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    @NotBlank(message = "{validation.required}")
    @Size(max = 500, message = "{validation.invalid}")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    @NotBlank(message = "{validation.required}")
    @Size(max = 500, message = "{validation.invalid}")
    private String nameEn;

    @Schema(description = "Cho phép sử dụng chức năng")
    private boolean active;

    @Schema(description = "Tập HTTP method + template Menu/API, không nhận URL ngoài hoặc wildcard")
    @NotNull(message = "{validation.required}")
    @Size(max = 512, message = "{validation.invalid}")
    private Set<@NotBlank(message = "{validation.required}") String> targets;
}
