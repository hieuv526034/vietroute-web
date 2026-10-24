package vn.vietroute.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
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
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessRules;
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
    @PreAuthorize(AccessRules.ACCESS_READ)
    public List<AccessRegistryResponse> registry(String kind) {
        return registry.registry(kind);
    }

    /** Mã bất biến, tên/trạng thái có version. Không cho tắt quyền lõi hoặc ADMIN để giữ đường cứu hộ. */
    @PreAuthorize(AccessRules.REGISTRY_SAVE)
    public List<AccessRegistryResponse> save(
        String kind,
        String code,
        AccessRegistryRequest body,
        UserPrincipal actor,
        boolean create
    ) {
        AccessActors.requireCurrent(actor);
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
            // Bật lại role/permission cũng là cấp quyền gián tiếp: người được ủy quyền không được kích hoạt tập quyền vượt chính mình.
            if (!create && !actor.roles().contains(Role.ADMIN)) {
                Set<String> granted = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
                boolean outside = "roles".equals(kind)
                    ? access
                          .role(connection, Role.valueOf(code))
                          .permissions()
                          .stream()
                          .anyMatch(permission -> !granted.contains(permission.name()))
                    : !granted.contains(code);
                if (outside) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            registry.save(connection, kind, code, body, actor.id(), create);
            return registry
                .registry(connection, kind)
                .stream()
                .filter(item -> item.code().equals(code))
                .toList();
        });
    }

    /** Xóa mềm role chưa được dùng; advisory lock thống nhất với gán role/policy để không lọt tham chiếu mới. */
    @PreAuthorize(AccessRules.ACCESS_DELETE)
    public List<AccessRegistryResponse> deleteRole(String code, long version, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        try {
            if (Role.ADMIN.equals(Role.valueOf(code))) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.INVALID_CODE);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            registry.deleteRole(connection, code, version, actor.id());
            return List.of();
        });
    }

    /** Chỉ quản trị đọc cấu hình toàn hệ thống; không công bố ma trận cho user thường. */
    @PreAuthorize(AccessRules.ACCESS_READ)
    public List<AccessPolicyResponse> policies() {
        return registry.policies();
    }

    /** Inventory do server tạo; không nhận route tùy ý, matcher wildcard hoặc route cứu hộ từ client. */
    @PreAuthorize(AccessRules.POLICY_SAVE)
    public List<AccessPolicyResponse> savePolicy(
        long id,
        AccessPolicyRequest body,
        UserPrincipal actor,
        List<AccessTargetResponse> targets
    ) {
        AccessActors.requireCurrent(actor);
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
            return registry
                .policies(connection)
                .stream()
                .filter(item -> item.kind().equals(body.kind()) && item.target().equals(body.target()))
                .toList();
        });
    }

    /** Xóa chỉ chính sách bổ sung; annotation/service guard vẫn còn nguyên. */
    @PreAuthorize(AccessRules.ACCESS_DELETE)
    public List<AccessPolicyResponse> deletePolicy(long id, long version) {
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            registry.deletePolicy(connection, id, version);
            return List.of();
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
