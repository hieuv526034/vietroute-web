package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Payload theo tài nguyên; điều kiện bắt buộc riêng của từng endpoint được kiểm ở service và mô tả trong Operation. */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "Payload RoleRequest; không nhận actor/audit hoặc hash")
public class RoleRequest {

    @Schema(description = "Tên tiếng Việt")
    @Size(max = 120)
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    @Size(max = 120)
    private String nameEn;

    @Schema(description = "Trạng thái sử dụng")
    private Boolean active;

    @Schema(description = "Version bắt buộc khi sửa")
    @Min(0)
    private Long version;

    @Schema(description = "Tập chức năng khi gán; rỗng thu hồi hết")
    @Size(max = 512)
    private Set<String> functionCodes;

    @Schema(description = "Tập role khi gán người dùng")
    @Size(max = 32)
    private Set<String> roles;

    @Schema(description = "Phạm vi nhà xe; 0 là toàn cục")
    @Min(0)
    private Long scopeId;

    @Schema(description = "Cho phép dữ liệu nhiều phạm vi trong các endpoint của chính role; cần trần tương ứng")
    private Boolean allScopes;
}
