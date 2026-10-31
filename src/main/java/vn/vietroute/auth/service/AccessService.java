package vn.vietroute.auth.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.account.AccessUserResponse;
import vn.vietroute.auth.dto.record.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.record.registry.PermissionResponse;
import vn.vietroute.auth.dto.record.role.RolePermissionsResponse;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipResponse;
import vn.vietroute.auth.dto.request.access.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.account.AccessUserSearchRequest;
import vn.vietroute.auth.dto.request.account.UserRolesRequest;
import vn.vietroute.auth.dto.response.account.UserResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessChangeRepository.Entry;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.DataScopeRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Method security bảo vệ ngay tại service, nên gọi nội bộ qua bean vẫn phải có quyền chức năng. */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final BootstrapAdminInitializer bootstrap;

    private final AccessRepository access;

    private final UserRepository users;

    private final JdbcTransactions transactions;

    private final AccessChangeService changes;

    private final RoleResourceService resourceGrants;

    /** Registry quyền được dịch theo locale; không truy vấn hay tiết lộ dữ liệu tài khoản. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public List<PermissionResponse> permissions(Locale locale) {
        return access.permissions(locale);
    }

    /** Đọc role chưa xóa, gồm cả role tắt để chuẩn bị mapping; UI không gợi role tắt cho gán mới. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public List<RolePermissionsResponse> roles() {
        return access.roleSummaries();
    }

    /** Danh sách quản trị cần ACCESS_READ; role được ủy quyền cũng đọc được, không trả credential. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public PageResponse<AccessUserResponse> users(AccessUserSearchRequest page) {
        return users.pageForAccess(page);
    }

    /** ADMIN là role cứu hộ cố định; không cho API cấu hình làm mất khả năng quản trị hệ thống. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.ASSIGN, aliases = { Permissions.ACCESS_WRITE })
    public RolePermissionsResponse replace(Role role, RolePermissionsRequest request, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        if (Role.ADMIN.equals(role)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        // Người được ủy quyền chỉ được gán các quyền chính họ đang có, không tự tạo đường nâng quyền.
        if (!actor.roles().contains(Role.ADMIN)) {
            Set<String> granted = SecurityContextHolder.getContext()
                .getAuthentication()
                .getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
            if (
                request
                    .getPermissions()
                    .stream()
                    .anyMatch(permission -> !granted.contains(permission.name()))
            ) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var before = access.role(connection, role);
            if (!actor.roles().contains(Role.ADMIN)) {
                Set<String> granted = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
                if (
                    before
                        .permissions()
                        .stream()
                        .anyMatch(permission -> !granted.contains(permission.name()))
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            access.requireExistingRole(connection, role);
            var beforeTargets = resourceGrants.targets(connection, role);
            access.validateMappings(connection, Set.of(), request.getPermissions());
            resourceGrants.requireDelegable(connection, role, actor);
            access.replacePermissions(connection, role, request.getPermissions(), request.getVersion(), actor.id());
            resourceGrants.fromPermissions(connection, role, request.getPermissions(), actor);
            var after = access.role(connection, role);
            var afterTargets = resourceGrants.targets(connection, role);
            if (!beforeTargets.equals(afterTargets)) changes.resources(
                connection,
                Event.ROLE_RESOURCES,
                Target.ROLE,
                role.name(),
                actor.id(),
                beforeTargets,
                afterTargets,
                before.version(),
                after.version(),
                before.permissions().stream().map(Permission::name).collect(Collectors.toSet()),
                after.permissions().stream().map(Permission::name).collect(Collectors.toSet())
            );
            changes.mappings(
                connection,
                Event.ROLE_PERMISSIONS,
                Target.ROLE,
                role.name(),
                actor.id(),
                before.permissions().stream().map(Permission::name).collect(Collectors.toSet()),
                after.permissions().stream().map(Permission::name).collect(Collectors.toSet()),
                before.version(),
                after.version()
            );
            return after;
        });
    }

    /** Gán role, tăng tokenVersion trong cùng transaction để thu hồi mọi token cũ của user đích. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.ASSIGN, aliases = { Permissions.ACCESS_WRITE })
    public UserResponse assign(long id, UserRolesRequest request, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        // Actor kỹ thuật chỉ tham chiếu audit, không được biến thành tài khoản có quyền.
        if (actor.id() == id || id == SystemActor.ID) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            access.validateMappings(connection, request.getRoles(), Set.of());
            var target = users.findById(connection, id, true);
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
            }
            var beforeRoles = access.assignedRoles(connection, id);
            // Tài khoản bootstrap luôn giữ ADMIN, dù vẫn còn admin khác trong hệ thống.
            if (
                bootstrap.username().equals(target.getUsername()) && !request.getRoles().contains(Role.ADMIN)
            ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            // Người được ủy quyền không đụng user ADMIN và chỉ gán role có tập quyền nằm trong quyền của chính họ.
            if (!actor.roles().contains(Role.ADMIN)) {
                if (
                    target.getRoles().contains(Role.ADMIN) || request.getRoles().contains(Role.ADMIN)
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                Set<Role> affected = new LinkedHashSet<>(target.getRoles());
                affected.addAll(request.getRoles());
                resourceGrants.requireDelegableRoles(connection, affected, actor);
            }
            if (
                target.isActive() &&
                target.getRoles().contains(Role.ADMIN) &&
                !request.getRoles().contains(Role.ADMIN) &&
                access.onlyActiveAdmin(connection, id)
            ) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            var scopedBefore = request.getRoles().containsAll(beforeRoles)
                ? List.<ScopeMembershipResponse>of()
                : DataScopeRepository.memberships(connection, id);
            access.replaceRoles(connection, id, request.getRoles(), request.getVersion(), actor.id());
            if (!scopedBefore.isEmpty()) {
                var scopedAfter = DataScopeRepository.memberships(connection, id)
                    .stream()
                    .collect(Collectors.toMap(ScopeMembershipResponse::scopeId, Function.identity()));
                var membershipChanges = new ArrayList<Entry>();
                for (var previous : scopedBefore) {
                    var current = scopedAfter.get(previous.scopeId());
                    if (current == null) throw new IllegalStateException(
                        "Membership bị thiếu trong cùng transaction gán role"
                    );
                    if (!previous.roles().equals(current.roles())) membershipChanges.add(
                        new Entry(
                            Event.SCOPE_MEMBERSHIP,
                            Target.MEMBERSHIP,
                            previous.scopeId() + ":" + id,
                            AccessChangeStateResponse.membership(
                                previous.roles().stream().map(Role::name).collect(Collectors.toSet()),
                                previous.active(),
                                previous.version()
                            ),
                            AccessChangeStateResponse.membership(
                                current.roles().stream().map(Role::name).collect(Collectors.toSet()),
                                current.active(),
                                current.version()
                            )
                        )
                    );
                }
                changes.recordAll(connection, membershipChanges, actor.id());
            }
            var after = users.findById(connection, id, false);
            changes.mappings(
                connection,
                Event.USER_ROLES,
                Target.USER,
                Long.toString(id),
                actor.id(),
                beforeRoles.stream().map(Role::name).collect(Collectors.toSet()),
                request.getRoles().stream().map(Role::name).collect(Collectors.toSet()),
                target.getVersion(),
                after.getVersion()
            );
            return UserResponse.from(after);
        });
    }
}
