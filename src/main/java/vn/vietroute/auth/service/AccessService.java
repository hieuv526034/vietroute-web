package vn.vietroute.auth.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.AccessUserSearchRequest;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.UserRolesRequest;
import vn.vietroute.auth.dto.response.AccessUserResponse;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.dto.response.RolePermissionsResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Method security bảo vệ ngay tại service, nên gọi nội bộ qua bean vẫn phải có quyền chức năng. */
@Service
@RequiredArgsConstructor
public class AccessService {

    private final AccessRepository access;

    private final UserRepository users;

    private final JdbcTransactions transactions;

    /** Registry quyền được dịch theo locale; không truy vấn hay tiết lộ dữ liệu tài khoản. */
    @PreAuthorize(AccessRules.ACCESS_READ)
    public List<PermissionResponse> permissions(Locale locale) {
        return access.permissions(locale);
    }

    /** Đọc role chưa xóa, gồm cả role tắt để chuẩn bị mapping; UI không gợi role tắt cho gán mới. */
    @PreAuthorize(AccessRules.ACCESS_READ)
    public List<RolePermissionsResponse> roles() {
        return access.roleSummaries();
    }

    /** Danh sách quản trị cần ACCESS_READ; role được ủy quyền cũng đọc được, không trả credential. */
    @PreAuthorize(AccessRules.ACCESS_READ)
    public PageResponse<AccessUserResponse> users(AccessUserSearchRequest page) {
        return users.pageForAccess(page);
    }

    /** ADMIN là role cứu hộ cố định; không cho API cấu hình làm mất khả năng quản trị hệ thống. */
    @PreAuthorize(AccessRules.ASSIGN)
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
            if (!actor.roles().contains(Role.ADMIN)) {
                Set<String> granted = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
                if (
                    access
                        .role(connection, role)
                        .permissions()
                        .stream()
                        .anyMatch(permission -> !granted.contains(permission.name()))
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            access.requireExistingRole(connection, role);
            access.validateMappings(connection, Set.of(), request.getPermissions());
            access.replacePermissions(connection, role, request.getPermissions(), request.getVersion(), actor.id());
            return access.role(connection, role);
        });
    }

    /** Gán role, tăng tokenVersion trong cùng transaction để thu hồi mọi token cũ của user đích. */
    @PreAuthorize(AccessRules.ASSIGN)
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
            // Người được ủy quyền không đụng user ADMIN và chỉ gán role có tập quyền nằm trong quyền của chính họ.
            if (!actor.roles().contains(Role.ADMIN)) {
                if (
                    target.getRoles().contains(Role.ADMIN) || request.getRoles().contains(Role.ADMIN)
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                Set<String> granted = SecurityContextHolder.getContext()
                    .getAuthentication()
                    .getAuthorities()
                    .stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
                Set<Role> affected = new LinkedHashSet<>(target.getRoles());
                affected.addAll(request.getRoles());
                for (Role assigned : affected)
                    if (
                        access
                            .role(connection, assigned)
                            .permissions()
                            .stream()
                            .anyMatch(permission -> !granted.contains(permission.name()))
                    ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            if (
                target.isActive() &&
                target.getRoles().contains(Role.ADMIN) &&
                !request.getRoles().contains(Role.ADMIN) &&
                access.onlyActiveAdmin(connection, id)
            ) {
                throw new BusinessException(ErrorCode.CONFLICT);
            }
            access.replaceRoles(connection, id, request.getRoles(), request.getVersion(), actor.id());
            return UserResponse.from(users.findById(connection, id, false));
        });
    }
}
