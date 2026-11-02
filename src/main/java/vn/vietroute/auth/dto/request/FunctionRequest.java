package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Payload theo tài nguyên; điều kiện bắt buộc riêng của từng endpoint được kiểm ở service và mô tả trong Operation. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload FunctionRequest; không nhận actor/audit hoặc hash")
public class FunctionRequest {

    @Schema(description = "Mã chức năng bất biến")
    @Pattern(regexp = Regex.FUNCTION_CODE, message = "{validation.invalid}")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    @Size(max = 500)
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    @Size(max = 500)
    private String nameEn;

    @Schema(description = "Trạng thái sử dụng")
    private Boolean active;

    @Schema(description = "Tập method + template thực từ danh bạ server")
    @Size(max = 512)
    private Set<String> targets;

    @Schema(description = "Version bắt buộc khi sửa")
    @Min(0)
    private Long version;

    @Schema(description = "Mô tả")
    @Size(max = 1000)
    private String description;

    @Schema(description = "Tên icon an toàn")
    @Pattern(regexp = "[a-z-]{1,50}")
    private String icon;

    @Schema(description = "Thứ tự menu")
    @Min(0)
    @Max(10000)
    private Integer sortOrder;
}
