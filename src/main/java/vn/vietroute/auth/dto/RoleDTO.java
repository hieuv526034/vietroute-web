package vn.vietroute.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.auth.entity.Role;

/** Role và projection gán role/phạm vi, không có credential hoặc action matrix. */
@Getter
@Setter
@NoArgsConstructor
public class RoleDTO {

    @Schema(description = "Mã role hoặc mã phạm vi khi đọc scope")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Đang hoạt động")
    private boolean active;

    @Schema(description = "Role bootstrap được bảo vệ")
    private boolean protectedRole;

    @Schema(description = "Dữ liệu nhiều phạm vi trong endpoint của chính role")
    private boolean allScopes;

    @Schema(description = "Version CAS")
    private long version;

    @Schema(description = "Function của role")
    private Set<String> functionCodes = Set.of();

    @Schema(description = "Chỉ mapping: ID user")
    private Long userId;

    @Schema(description = "Chỉ mapping: username")
    private String username;

    @Schema(description = "Chỉ mapping: role của user")
    private Set<String> roles = Set.of();

    @Schema(description = "Chỉ scope: ID nhà xe/phạm vi")
    private Long scopeId;

    @Schema(description = "Chỉ scope: ID bản ghi")
    private Long id;

    @Schema(description = "Endpoint hiệu lực tại scope")
    private Set<String> targets = Set.of();

    @Schema(description = "Audit UTC")
    private Instant updatedAt;

    @Schema(description = "Actor cập nhật")
    private Long updatedBy;

    public static RoleDTO from(Role role) {
        RoleDTO dto = new RoleDTO();
        dto.code = role.getCode();
        dto.nameVi = role.getNameVi();
        dto.nameEn = role.getNameEn();
        dto.active = role.isActive();
        dto.protectedRole = role.isProtectedRole();
        dto.allScopes = role.isAllScopes();
        dto.version = role.getVersion();
        dto.updatedAt = role.getUpdatedAt();
        dto.updatedBy = role.getUpdatedBy();
        return dto;
    }
}
