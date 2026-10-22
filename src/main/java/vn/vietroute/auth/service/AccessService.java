package vn.vietroute.auth.service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.RolePermissionsRequest;
import vn.vietroute.auth.dto.request.UserRolesRequest;
import vn.vietroute.auth.dto.response.PermissionResponse;
import vn.vietroute.auth.dto.response.RolePermissionsResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
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

    private final MessageSource messages;

    /** Registry quyền được dịch theo locale; không truy vấn hay tiết lộ dữ liệu tài khoản. */
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public List<PermissionResponse> permissions(Locale locale) {
        return Arrays.stream(Permission.values())
            .map(permission ->
                new PermissionResponse(
                    permission,
                    permission.resource(),
                    permission.actions(),
                    permission.messageKey(),
                    messages.getMessage(permission.messageKey(), null, locale)
                )
            )
            .toList();
    }

    /** Đọc cấu hình role hiện hành để UI lấy version trước khi sửa. */
    @PreAuthorize("hasAuthority('ACCESS_READ')")
    public List<RolePermissionsResponse> roles() {
        return Arrays.stream(Role.values()).map(access::role).toList();
    }

    /** ADMIN là role cứu hộ cố định; không cho API cấu hình làm mất khả năng quản trị hệ thống. */
    @PreAuthorize("hasAnyAuthority('ACCESS_ASSIGN','ACCESS_WRITE')")
    public RolePermissionsResponse replace(Role role, RolePermissionsRequest request, UserPrincipal actor) {
        if (role == Role.ADMIN) {
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
            access.replacePermissions(connection, role, request.getPermissions(), request.getVersion(), actor.id());
            return access.role(connection, role);
        });
    }

    /** Gán role, tăng tokenVersion trong cùng transaction để thu hồi mọi token cũ của user đích. */
    @PreAuthorize("hasAnyAuthority('ACCESS_ASSIGN','ACCESS_WRITE')")
    public UserResponse assign(long id, UserRolesRequest request, UserPrincipal actor) {
        if (actor.id() == id) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        // Gán role cấp quyền gián tiếp; giới hạn cho ADMIN để người có quyền gán mapping không tự nâng vai trò.
        if (!actor.roles().contains(Role.ADMIN)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var target = users.findById(connection, id, true);
            if (target == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND);
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
