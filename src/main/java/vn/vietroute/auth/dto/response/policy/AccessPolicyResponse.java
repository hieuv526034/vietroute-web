package vn.vietroute.auth.dto.response.policy;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Chính sách bổ sung, không thay thế các kiểm tra bắt buộc trong code")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessPolicyResponse {

    @Schema(description = "ID chính sách")
    private long id;

    @Schema(description = "MENU hoặc ENDPOINT")
    private String kind;

    @Schema(description = "Phương thức và template route")
    private String target;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Cho phép truy cập khi đáp ứng điều kiện")
    private boolean enabled;

    @Schema(description = "Tất cả quyền bắt buộc")
    private Set<String> permissions;

    @Schema(description = "Một trong các role, rỗng là mọi role")
    private Set<String> roles;

    @Schema(description = "Version chống ghi đè")
    private long version;

    @Schema(description = "Lần cập nhật UTC")
    private Instant updatedAt;

    @Schema(description = "Người cập nhật")
    private long updatedBy;

    public AccessPolicyResponse(
        long id,
        String kind,
        String target,
        String nameVi,
        String nameEn,
        boolean enabled,
        Set<String> permissions,
        Set<String> roles,
        long version,
        Instant updatedAt,
        long updatedBy
    ) {
        this.id = id;
        this.kind = kind;
        this.target = target;
        this.nameVi = nameVi;
        this.nameEn = nameEn;
        this.enabled = enabled;
        this.permissions = permissions;
        this.roles = roles;
        this.version = version;
        this.updatedAt = updatedAt;
        this.updatedBy = updatedBy;
    }

    public long id() {
        return id;
    }

    public String kind() {
        return kind;
    }

    public String target() {
        return target;
    }

    public String nameVi() {
        return nameVi;
    }

    public String nameEn() {
        return nameEn;
    }

    public boolean enabled() {
        return enabled;
    }

    public Set<String> permissions() {
        return permissions;
    }

    public Set<String> roles() {
        return roles;
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
