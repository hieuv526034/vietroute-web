package vn.vietroute.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.registry.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.response.policy.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.registry.AccessRegistryPageResponse;
import vn.vietroute.auth.dto.response.registry.AccessRegistryResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repo.AccessRegistryRepository;
import vn.vietroute.auth.repo.AccessRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Quản trị registry/chính sách. Chính sách chỉ bổ sung điều kiện, không thay quyền bắt buộc của service. */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final AccessRegistryRepository registry;

    private final AccessRepository access;

    private final JdbcTransactions transactions;

    private final AccessHistoryService changes;

    private final RoleResourceService resourceGrants;

    /** Registry tên/trạng thái có version riêng; không suy ra quyền từ hằng Java. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public List<AccessRegistryResponse> registry(String kind) {
        return registry.registry(kind);
    }

    /** Danh sách quản trị được tìm và đếm tại DB; quota là toàn registry, không phải chỉ kết quả lọc. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public AccessRegistryPageResponse registryPage(String kind, AccessRegistrySearchRequest search) {
        return registry.page(kind, search);
    }

    /** Mã bất biến, tên/trạng thái có version. Không cho tắt quyền lõi hoặc ADMIN để giữ đường cứu hộ. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.UPDATE, createArgument = "create")
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
            (registry.builtIn(kind, code) && (!body.active() || create))
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var before = registry.find(connection, kind, code);
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
                if ("roles".equals(kind)) resourceGrants.requireDelegable(connection, Role.valueOf(code), actor);
            }
            registry.save(connection, kind, code, body, actor.id(), create);
            var result = List.of(registry.find(connection, kind, code));
            boolean role = kind.equals("roles");
            changes.record(
                connection,
                role
                    ? create
                        ? Event.ROLE_CREATED
                        : Event.ROLE_UPDATED
                    : create
                      ? Event.PERMISSION_CREATED
                      : Event.PERMISSION_UPDATED,
                role ? Target.ROLE : Target.PERMISSION,
                code,
                actor.id(),
                AccessChangeStateResponse.registry(before),
                AccessChangeStateResponse.registry(result.get(0))
            );
            return result;
        });
    }

    /** Xóa mềm role chưa được dùng; advisory lock thống nhất với gán role/policy để không lọt tham chiếu mới. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.DELETE)
    public List<AccessRegistryResponse> deleteRole(String code, long version, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        try {
            if (Role.ADMIN.equals(Role.valueOf(code))) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        } catch (IllegalArgumentException error) {
            throw new BusinessException(ErrorCode.INVALID_CODE);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var before = registry.find(connection, "roles", code);
            if (before == null) throw new BusinessException(ErrorCode.STALE_VERSION);
            var permissions = access
                .role(connection, Role.valueOf(code))
                .permissions()
                .stream()
                .map(Permission::name)
                .collect(Collectors.toSet());
            var targets = resourceGrants.targets(connection, Role.valueOf(code));
            registry.deleteRole(connection, code, version, actor.id());
            changes.record(
                connection,
                Event.ROLE_DELETED,
                Target.ROLE,
                code,
                actor.id(),
                AccessChangeStateResponse.registry(before, permissions, targets),
                AccessChangeStateResponse.deleted(before)
            );
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
