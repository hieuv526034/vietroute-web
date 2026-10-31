package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.dto.record.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipRequest;
import vn.vietroute.auth.dto.request.account.DriverAccountCreateRequest;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.DataScopeRepository;
import vn.vietroute.auth.repository.ManagedAccountRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Callback auth nội bộ cùng transaction hồ sơ; không mở quyền quản trị account/role cho OPERATOR. */
@Component
@RequiredArgsConstructor
public class DriverAccountProvisioner {

    private final PasswordEncoder passwords;
    private final BootstrapAdminInitializer bootstrap;
    private final ManagedAccountRepository accounts;
    private final AccessRepository access;
    private final DataScopeRepository scopes;
    private final DataScopeGuard guard;
    private final AccessChangeService changes;

    /** BCrypt trước khi lấy connection/khóa nghiệp vụ; giá trị trả chỉ truyền nội bộ, không thành DTO. */
    public String passwordHash(String password) {
        BusinessRules.require(StrongPasswordValidator.valid(password), ErrorCode.VALIDATION_FAILED);
        return passwords.encode(password);
    }

    /** Caller giữ khóa nhà xe/hồ sơ; auth kiểm lại actor/scope, chỉ tạo DRIVER hạn chế và ghi history nguyên tử. */
    public long create(
        Connection connection,
        long scope,
        String displayName,
        DriverAccountCreateRequest body,
        String hash,
        UserPrincipal actor
    ) throws SQLException {
        AccessActors.requireCurrent(actor);
        guard.require(connection, scope, Groups.DRIVER, PermissionAction.ASSIGN);
        String username = body.getUsername().toLowerCase(Locale.ROOT);
        BusinessRules.require(!bootstrap.username().equals(username), ErrorCode.CONFLICT);
        access.lockRoleAssignments(connection);
        access.validateMappings(connection, Set.of(Role.DRIVER), Set.of());
        // Quy trình nghiệp vụ chỉ được cấp quyền chuyến được giao. DRIVER bị gắn thêm quyền khác phải qua ADMIN,
        // kể cả mapping đang tắt, tránh kích hoạt lại quyền mạnh hơn sau khi tài khoản đã được cấp.
        Set<Permission> ceiling = Stream.of(PermissionAction.VIEW, PermissionAction.READ, PermissionAction.UPDATE)
            .map(action -> Permission.valueOf(Groups.DRIVER_TRIP + "_" + action.name()))
            .collect(Collectors.toUnmodifiableSet());
        BusinessRules.require(
            ceiling.containsAll(access.role(connection, Role.DRIVER).permissions()),
            ErrorCode.DRIVER_ACCOUNT_ROLE
        );
        long user = accounts.create(
            connection,
            username,
            displayName.strip(),
            hash,
            body.getEmail().strip().toLowerCase(Locale.ROOT),
            actor.id()
        );
        access.replaceRoles(connection, user, Set.of(Role.DRIVER), 0, actor.id());
        scopes.assign(connection, scope, user, new ScopeMembershipRequest(Set.of(Role.DRIVER), true, 0), actor.id());
        changes.record(
            connection,
            Event.ACCOUNT_CREATED,
            Target.USER,
            Long.toString(user),
            actor.id(),
            AccessChangeStateResponse.empty(),
            new AccessChangeStateResponse(
                Set.of(Role.DRIVER.name()),
                Set.of(),
                1,
                false,
                true,
                null,
                null,
                null,
                null,
                null,
                null,
                1
            )
        );
        changes.record(
            connection,
            Event.SCOPE_MEMBERSHIP,
            Target.MEMBERSHIP,
            scope + ":" + user,
            actor.id(),
            AccessChangeStateResponse.empty(),
            AccessChangeStateResponse.membership(Set.of(Role.DRIVER.name()), true, 1)
        );
        return user;
    }
}
