package vn.vietroute.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.AccessPolicyRequest;
import vn.vietroute.auth.dto.request.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessRegistryResponse;
import vn.vietroute.auth.dto.response.AccessTargetResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRegistryRepository;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Quản trị registry/chính sách. Chính sách chỉ bổ sung điều kiện, không thay quyền bắt buộc của service. */
@Service
@RequiredArgsConstructor
public class AccessManagementService {

    private final AccessRegistryRepository registry;

    private final AccessRepository access;

    private final JdbcTransactions transactions;

    /** Registry tên/trạng thái có version riêng; không suy ra quyền từ hằng Java. */
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_READ')")
    public List<AccessRegistryResponse> registry(String kind) {
        return registry.registry(kind);
    }

    /** Mã bất biến, tên/trạng thái có version. Không cho tắt quyền lõi hoặc ADMIN để giữ đường cứu hộ. */
    @PreAuthorize(
        "hasRole('ADMIN') and ((#create and hasAuthority('ACCESS_CREATE')) or (!#create and hasAuthority('ACCESS_UPDATE')))"
    )
    public List<AccessRegistryResponse> save(
        String kind,
        String code,
        AccessRegistryRequest body,
        UserPrincipal actor,
        boolean create
    ) {
        try {
            if ("roles".equals(kind)) {
                Role.valueOf(code);
            } else if ("permissions".equals(kind)) {
                Permission.valueOf(code);
            } else {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_CODE);
        }
        if (
            ("roles".equals(kind) && "ADMIN".equals(code)) ||
            (AccessRegistryRepository.builtIn(kind, code) && (!body.active() || create))
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            registry.save(connection, kind, code, body, actor.id(), create);
            return registry.registry(connection, kind);
        });
    }

    /** Xóa mềm role chưa được dùng; advisory lock thống nhất với gán role/policy để không lọt tham chiếu mới. */
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_DELETE')")
    public List<AccessRegistryResponse> deleteRole(String code, long version, UserPrincipal actor) {
        try {
            if (Role.ADMIN.equals(Role.valueOf(code))) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.INVALID_CODE);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            registry.deleteRole(connection, code, version, actor.id());
            return registry.registry(connection, "roles");
        });
    }

    /** Chỉ quản trị đọc cấu hình toàn hệ thống; không công bố ma trận cho user thường. */
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_READ')")
    public List<AccessPolicyResponse> policies() {
        return registry.policies();
    }

    /** Inventory do server tạo; không nhận route tùy ý, matcher wildcard hoặc route cứu hộ từ client. */
    @PreAuthorize(
        "hasRole('ADMIN') and ((#id == 0 and hasAuthority('ACCESS_CREATE')) or (#id > 0 and hasAuthority('ACCESS_UPDATE')))"
    )
    public List<AccessPolicyResponse> savePolicy(
        long id,
        AccessPolicyRequest body,
        UserPrincipal actor,
        List<AccessTargetResponse> targets
    ) {
        if (
            targets
                .stream()
                .noneMatch(target -> target.kind().equals(body.kind()) && target.target().equals(body.target()))
        ) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            access.validateMappings(connection, body.roles(), body.permissions());
            registry.savePolicy(connection, id, body, actor.id());
            return registry.policies(connection);
        });
    }

    /** Xóa chỉ chính sách bổ sung; annotation/service guard vẫn còn nguyên. */
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_DELETE')")
    public List<AccessPolicyResponse> deletePolicy(long id, long version) {
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            registry.deletePolicy(connection, id, version);
            return registry.policies(connection);
        });
    }

    /** Mỗi request đọc một snapshot DB, không cache quyền giữa các node; lỗi DB được trả 503, không mở quyền. */
    public List<AccessPolicyResponse> requestPolicies(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal user) || user.roles().contains(Role.ADMIN)) {
            return List.of();
        }
        return registry.policies();
    }

    /** Tất cả quyền phải có, còn tập role chỉ cần một; ADMIN được miễn điều kiện bổ sung. */
    public static boolean allows(AccessPolicyResponse policy, Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof UserPrincipal user)) {
            return false;
        }
        if (user.roles().contains(Role.ADMIN)) {
            return true;
        }
        if (!policy.enabled()) {
            return false;
        }
        Set<String> granted = auth
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        return (
            granted.containsAll(policy.permissions()) &&
            (policy.roles().isEmpty() || user.roles().stream().map(Role::name).anyMatch(policy.roles()::contains))
        );
    }
}
