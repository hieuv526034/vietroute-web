package vn.vietroute.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;

/** Whitelist thay đổi: không nhận raw request, credential, email hoặc entity tài khoản để serialize. */
@Schema(description = "Giá trị trường thay đổi; khi delta=true, danh sách trước là mã bị bỏ, sau là mã được thêm")
public record AccessChangeStateResponse(
    @Schema(description = "Role bị bỏ/thêm, không phải credential") Set<String> roles,
    @Schema(description = "Quyền bị bỏ/thêm") Set<String> permissions,
    @Schema(description = "Tổng mapping thực tế trước/sau thay đổi") int mappingCount,
    @Schema(description = "Danh sách chỉ chứa phần chênh lệch") boolean delta,
    @Schema(description = "Trạng thái bật nếu có") Boolean active,
    @Schema(description = "Đánh dấu xóa mềm nếu có") Boolean deleted,
    @Schema(description = "Policy cho phép nếu có") Boolean enabled,
    @Schema(description = "Tên cấu hình tiếng Việt, không có dữ liệu mật khẩu") String nameVi,
    @Schema(description = "Tên cấu hình tiếng Anh") String nameEn,
    @Schema(description = "Loại policy nếu có") String policyKind,
    @Schema(description = "Template policy nếu có, không chứa URL/query người dùng") String policyTarget,
    @Schema(description = "Version cấu hình/mapping tại thời điểm ghi") long version,
    @Schema(description = "Target menu/API bị bỏ hoặc thêm; chỉ route template server") Set<String> resources
) {
    public AccessChangeStateResponse {
        roles = roles == null ? Set.of() : Set.copyOf(roles);
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
        resources = resources == null ? Set.of() : Set.copyOf(resources);
    }

    public AccessChangeStateResponse(
        Set<String> roles,
        Set<String> permissions,
        int mappingCount,
        boolean delta,
        Boolean active,
        Boolean deleted,
        Boolean enabled,
        String nameVi,
        String nameEn,
        String policyKind,
        String policyTarget,
        long version
    ) {
        this(
            roles,
            permissions,
            mappingCount,
            delta,
            active,
            deleted,
            enabled,
            nameVi,
            nameEn,
            policyKind,
            policyTarget,
            version,
            Set.of()
        );
    }

    public static AccessChangeStateResponse resources(Set<String> changed, int count, long version) {
        return new AccessChangeStateResponse(
            Set.of(),
            Set.of(),
            count,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            version,
            changed
        );
    }

    /** Trạng thái chưa tồn tại hoặc đã xóa policy, không dựng dữ liệu giả để lấp lịch sử. */
    public static AccessChangeStateResponse empty() {
        return new AccessChangeStateResponse(Set.of(), Set.of(), 0, false, null, null, null, null, null, null, null, 0);
    }

    /** Chỉ ghi mã chênh lệch để không nhân đôi mọi quyền không thay đổi trên Neon Free. */
    public static AccessChangeStateResponse mapping(Set<String> changed, int count, long version, boolean roles) {
        return new AccessChangeStateResponse(
            roles ? changed : Set.of(),
            roles ? Set.of() : changed,
            count,
            true,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            version
        );
    }

    /** Metadata registry không mang credential hoặc thông tin liên hệ của tài khoản. */
    public static AccessChangeStateResponse registry(AccessRegistryResponse value) {
        return registry(value, Set.of());
    }

    /** Trước khi xóa role phải giữ cả mapping sắp bị dọn, không chỉ trạng thái registry. */
    public static AccessChangeStateResponse registry(AccessRegistryResponse value, Set<String> permissions) {
        return value == null
            ? empty()
            : new AccessChangeStateResponse(
                  Set.of(),
                  permissions,
                  permissions.size(),
                  false,
                  value.active(),
                  false,
                  null,
                  value.nameVi(),
                  value.nameEn(),
                  null,
                  null,
                  value.version()
              );
    }

    /** Giữ cấu hình và tập mapping trước khi policy bị thay hoặc xóa. */
    public static AccessChangeStateResponse policy(AccessPolicyResponse value) {
        return value == null
            ? empty()
            : new AccessChangeStateResponse(
                  value.roles(),
                  value.permissions(),
                  value.permissions().size(),
                  false,
                  null,
                  null,
                  value.enabled(),
                  value.nameVi(),
                  value.nameEn(),
                  value.kind(),
                  value.target(),
                  value.version()
              );
    }

    /** Trạng thái khóa/mở đã commit; không ghi password hoặc challenge bị thu hồi. */
    public static AccessChangeStateResponse status(boolean active, long version) {
        return new AccessChangeStateResponse(
            Set.of(),
            Set.of(),
            0,
            false,
            active,
            null,
            null,
            null,
            null,
            null,
            null,
            version
        );
    }

    /** Membership giữ tập role tối đa 32 và trạng thái/version trước–sau, không ghi danh tính liên hệ. */
    public static AccessChangeStateResponse membership(Set<String> roles, boolean active, long version) {
        return new AccessChangeStateResponse(
            roles,
            Set.of(),
            roles.size(),
            false,
            active,
            null,
            null,
            null,
            null,
            null,
            null,
            version
        );
    }

    /** Tombstone giữ mã và tên, version registry tăng một lần cùng thao tác xóa. */
    public static AccessChangeStateResponse deleted(AccessRegistryResponse value) {
        return new AccessChangeStateResponse(
            Set.of(),
            Set.of(),
            0,
            false,
            false,
            true,
            null,
            value.nameVi(),
            value.nameEn(),
            null,
            null,
            value.version() + 1
        );
    }
}
