package vn.vietroute.auth.dto.request.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Tên hiển thị và trạng thái role/quyền, mã ổn định nằm trên URL")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessRegistryRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @NotBlank(message = "{validation.required}")
    @Size(max = 120, message = "{validation.invalid}")
    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Cho phép gán và sử dụng")
    private boolean active;

    @Min(value = 0, message = "{validation.invalid}")
    @Schema(description = "Version registry đọc gần nhất, tạo mới dùng 0")
    private long version;

    public AccessRegistryRequest(String nameVi, String nameEn, boolean active, long version) {
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.active = active;
        this.version = version;
    }

    public String nameVi() {
        return nameVi;
    }

    public String nameEn() {
        return nameEn;
    }

    public boolean active() {
        return active;
    }

    public long version() {
        return version;
    }
}
