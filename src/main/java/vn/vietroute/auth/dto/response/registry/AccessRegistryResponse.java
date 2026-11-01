package vn.vietroute.auth.dto.response.registry;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Registry role/quyền cấu hình, tách version khỏi version mapping")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessRegistryResponse {

    @Schema(description = "Mã ổn định")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "Mã lõi không được vô hiệu hóa")
    private boolean builtIn;

    @Schema(description = "Version registry")
    private long version;

    @Schema(description = "Lần cập nhật gần nhất UTC")
    private Instant updatedAt;

    @Schema(description = "ID người cập nhật, system là 0")
    private long updatedBy;

    public AccessRegistryResponse(
        String code,
        String nameVi,
        String nameEn,
        boolean active,
        boolean builtIn,
        long version,
        Instant updatedAt,
        long updatedBy
    ) {
        this.code = code;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.active = active;
        this.builtIn = builtIn;
        this.version = version;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public String code() {
        return code;
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

    public boolean builtIn() {
        return builtIn;
    }

    public long version() {
        return version;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long updatedBy() {
        return updatedBy;
    }
}
