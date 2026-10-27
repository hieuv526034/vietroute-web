package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.RoleResourcesRequest;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.RoleResourcesResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.RoleResourceRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessAuthorization;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Một luồng gán duy nhất: tài nguyên cụ thể là nguồn, action chỉ còn projection nội bộ cho service/scope. */
@Service
@RequiredArgsConstructor
public class RoleResourceService {

    private final RoleResourceRepository resources;
    private final AccessResourceService definitions;
    private final AccessRepository roles;
    private final JdbcTransactions transactions;
    private final AccessChangeService changes;

    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public RoleResourcesResponse get(Role role) {
        return transactions.execute(connection -> {
            var current = roles.role(connection, role);
            return new RoleResourcesResponse(role.name(), targets(connection, role), current.version());
        });
    }

    /** CAS mapping + projection + lịch sử cùng transaction, lỗi bất kỳ rollback toàn bộ. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.ASSIGN)
    public RoleResourcesResponse replace(Role role, RoleResourcesRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        if (role.equals(Role.ADMIN)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            roles.lockRoleAssignments(connection);
            roles.requireExistingRole(connection, role);
            var before = roles.role(connection, role);
            var previous = targets(connection, role);
            var selected = select(body.targets());
            requireDelegable(previous, actor);
            requireDelegable(body.targets(), actor);
            Set<Permission> projection = projection(selected, previous, before.permissions());
            if (!actor.roles().contains(Role.ADMIN)) {
                var held = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getAuthorities()
                    .stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
                if (
                    projection.stream().anyMatch(permission -> !held.contains(permission.name()))
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            roles.validateMappings(connection, Set.of(), projection);
            roles.replacePermissions(connection, role, projection, body.version(), actor.id());
            resources.replace(connection, role, selected, actor.id());
            changes.resources(
                connection,
                Event.ROLE_RESOURCES,
                Target.ROLE,
                role.name(),
                actor.id(),
                previous,
                body.targets(),
                before.version(),
                before.version() + 1,
                before.permissions().stream().map(Permission::name).collect(Collectors.toSet()),
                projection.stream().map(Permission::name).collect(Collectors.toSet())
            );
            return new RoleResourcesResponse(role.name(), Set.copyOf(body.targets()), before.version() + 1);
        });
    }

    /** Chuyển API tương thích cũ thành tập route hiện tại; những route khai báo về sau không được tự gán. */
    public void fromPermissions(Connection connection, Role role, Set<Permission> permissions, UserPrincipal actor)
        throws SQLException {
        Set<String> codes = permissions.stream().map(Permission::name).collect(Collectors.toSet());
        Set<AccessResourceResponse> selected = definitions
            .inventory()
            .stream()
            .filter(RoleResourceService::assignable)
            .filter(item ->
                item.match().equals("ALL")
                    ? codes.containsAll(item.declaredPermissions())
                    : item.declaredPermissions().stream().anyMatch(codes::contains)
            )
            .collect(Collectors.toSet());
        requireDelegable(selected.stream().map(AccessResourceResponse::target).collect(Collectors.toSet()), actor);
        resources.replace(connection, role, selected, actor.id());
    }

    /** Dùng cả khi gán/tái kích hoạt role để không nâng quyền qua API khác có cùng projection action. */
    public void requireDelegable(Connection connection, Role role, UserPrincipal actor) throws SQLException {
        requireDelegable(targets(connection, role), actor);
    }

    /** Kiểm toàn bộ role bị ảnh hưởng bằng một SELECT; không union quyền role khác để vượt trần target/permission của actor. */
    public void requireDelegableRoles(Connection connection, Set<Role> assigned, UserPrincipal actor)
        throws SQLException {
        if (actor.roles().contains(Role.ADMIN)) return;
        var held = SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
        var required = new LinkedHashSet<String>();
        for (var grant : resources.grants(connection, assigned)) {
            Set<String> codes = grant.permissions().stream().map(Permission::name).collect(Collectors.toSet());
            if (!held.containsAll(codes)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            if (grant.initialized()) required.addAll(grant.targets());
            else definitions
                .inventory()
                .stream()
                .filter(RoleResourceService::assignable)
                .filter(item ->
                    item.match().equals("ALL")
                        ? codes.containsAll(item.declaredPermissions())
                        : item.declaredPermissions().stream().anyMatch(codes::contains)
                )
                .map(AccessResourceResponse::target)
                .forEach(required::add);
        }
        requireDelegable(required, actor);
    }

    private void requireDelegable(Set<String> targets, UserPrincipal actor) {
        if (actor.roles().contains(Role.ADMIN)) return;
        var auth = SecurityContextHolder.getContext().getAuthentication();
        for (var item : select(targets)) {
            if (!AccessAuthorization.allows(item, auth)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Set<AccessResourceResponse> select(Set<String> targets) {
        var selected = definitions
            .inventory()
            .stream()
            .filter(RoleResourceService::assignable)
            .filter(item -> targets.contains(item.target()))
            .collect(Collectors.toSet());
        if (
            !selected.stream().map(AccessResourceResponse::target).collect(Collectors.toSet()).equals(targets)
        ) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        return selected;
    }

    /** Snapshot trong transaction quản trị của caller, kể cả target cũ cần thu hồi/lưu lịch sử. */
    public Set<String> targets(Connection connection, Role role) throws SQLException {
        if (resources.initialized(connection, role)) return resources.targets(connection, role);
        Set<String> codes = roles
            .role(connection, role)
            .permissions()
            .stream()
            .map(Permission::name)
            .collect(Collectors.toSet());
        return definitions
            .inventory()
            .stream()
            .filter(RoleResourceService::assignable)
            .filter(item ->
                item.match().equals("ALL")
                    ? codes.containsAll(item.declaredPermissions())
                    : item.declaredPermissions().stream().anyMatch(codes::contains)
            )
            .map(AccessResourceResponse::target)
            .collect(Collectors.toSet());
    }

    public static boolean assignable(AccessResourceResponse item) {
        return !item.publicRoute() && !item.declaredPermissions().isEmpty();
    }

    private static Set<Permission> projection(
        Set<AccessResourceResponse> selected,
        Set<String> previous,
        Set<Permission> oldPermissions
    ) {
        Set<Permission> result = new LinkedHashSet<>();
        for (var item : selected) {
            List<String> codes = item.declaredPermissions();
            // Migration giữ ANY/ALL phối hợp nhiều role: lưu lại target không được âm thầm lấp quyền còn thiếu.
            // Muốn cấp trọn API này phải bỏ/lưu rồi cấp mới tường minh, vẫn kiểm giới hạn ủy quyền actor.
            if (
                item.match().equals("ALL") &&
                previous.contains(item.target()) &&
                !codes.stream().map(Permission::valueOf).allMatch(oldPermissions::contains)
            ) {
                codes.stream().map(Permission::valueOf).filter(oldPermissions::contains).forEach(result::add);
                continue;
            }
            // ANY có alias WRITE: chọn mã cụ thể để không mở rộng service khác qua alias cũ.
            var canonical = codes
                .stream()
                .filter(code -> !code.endsWith("_WRITE"))
                .toList();
            (item.match().equals("ALL") || canonical.isEmpty() ? codes : canonical)
                .stream()
                .map(Permission::valueOf)
                .forEach(result::add);
        }
        return Set.copyOf(result);
    }
}
