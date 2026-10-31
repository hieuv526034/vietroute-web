package vn.vietroute.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.account.ManagedAccountAccessResponse;
import vn.vietroute.auth.dto.record.account.ManagedAccountResponse;
import vn.vietroute.auth.dto.record.account.ManagedAccountStateRequest;
import vn.vietroute.auth.dto.record.account.ManagedAccountUpdateRequest;
import vn.vietroute.auth.dto.record.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.dto.request.account.ManagedAccountCreateRequest;
import vn.vietroute.auth.dto.request.account.ManagedAccountSearchRequest;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessChangeRepository.Entry;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.AccountRepository;
import vn.vietroute.auth.repository.DataScopeRepository;
import vn.vietroute.auth.repository.ManagedAccountRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessCapabilities;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Admin cấp danh tính độc lập; role DRIVER không tự tạo hồ sơ/membership nhà xe hoặc cấp quyền nghiệp vụ. */
@Service
@RequiredArgsConstructor
public class ManagedAccountService {

    private final BootstrapAdminInitializer bootstrap;

    private final ManagedAccountRepository accounts;

    private final UserRepository users;

    private final AccessRepository access;

    private final AccountRepository lifecycle;

    private final PasswordEncoder passwords;

    private final JdbcTransactions transactions;

    private final AccessManagementService policies;

    private final AccessChangeService changes;

    private final DatabaseAccessGuard guard;

    /** Sửa tên không thay credential hay trạng thái xác minh; tài khoản gốc/bản thân/ADMIN có luồng riêng. */
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.UPDATE, adminOnly = true)
    public ManagedAccountResponse update(long id, ManagedAccountUpdateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id != SystemActor.ID && id != actor.id(), ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var target = BusinessRules.requireFound(users.findById(connection, id, true));
            BusinessRules.require(
                !bootstrap.username().equals(target.getUsername()) && !target.getRoles().contains(Role.ADMIN),
                ErrorCode.ACCESS_DENIED
            );
            accounts.update(connection, id, body.displayName().strip(), body.version(), actor.id());
            changes.record(
                connection,
                Event.ACCOUNT_UPDATED,
                Target.USER,
                Long.toString(id),
                actor.id(),
                AccessChangeStateResponse.status(target.isActive(), target.getVersion()),
                AccessChangeStateResponse.status(target.isActive(), body.version() + 1)
            );
            return ManagedAccountResponse.from(
                users.findById(connection, id, false),
                accounts.assignedRoles(connection, id)
            );
        });
    }

    /** Xóa mềm và thu hồi quyền hiện hành; lịch sử/FK nghiệp vụ vẫn trỏ đúng ID cũ. */
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.DELETE, adminOnly = true)
    public void delete(long id, long version, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id != SystemActor.ID && id != actor.id(), ErrorCode.ACCESS_DENIED);
        transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var target = BusinessRules.requireFound(users.findById(connection, id, true));
            BusinessRules.require(
                !bootstrap.username().equals(target.getUsername()) && !target.getRoles().contains(Role.ADMIN),
                ErrorCode.ACCESS_DENIED
            );
            var assigned = accounts.assignedRoles(connection, id).stream().map(Role::name).collect(Collectors.toSet());
            var memberships = DataScopeRepository.memberships(connection, id);
            accounts.delete(connection, id, version, actor.id());
            lifecycle.invalidate(connection, id);
            var membershipChanges = memberships
                .stream()
                .map(membership ->
                    new Entry(
                        Event.SCOPE_MEMBERSHIP,
                        Target.MEMBERSHIP,
                        membership.scopeId() + ":" + id,
                        AccessChangeStateResponse.membership(
                            membership.roles().stream().map(Role::name).collect(Collectors.toSet()),
                            membership.active(),
                            membership.version()
                        ),
                        AccessChangeStateResponse.empty()
                    )
                )
                .toList();
            changes.recordAll(connection, membershipChanges, actor.id());
            changes.record(
                connection,
                Event.ACCOUNT_DELETED,
                Target.USER,
                Long.toString(id),
                actor.id(),
                AccessChangeStateResponse.membership(assigned, target.isActive(), target.getVersion()),
                new AccessChangeStateResponse(
                    Set.of(),
                    Set.of(),
                    0,
                    false,
                    false,
                    true,
                    null,
                    null,
                    null,
                    null,
                    null,
                    version + 1
                )
            );
            return null;
        });
    }

    /** Danh sách chỉ ADMIN có ACCOUNT_READ; không cho OPERATOR tra danh tính ngoài nhà xe. */
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.READ, adminOnly = true)
    public PageResponse<ManagedAccountResponse> page(ManagedAccountSearchRequest page) {
        return accounts.page(page);
    }

    /** Hash BCrypt trước transaction để không giữ connection/khóa trong lúc tính toán mật khẩu. */
    @AccessOperation(
        resource = Groups.ACCOUNT,
        action = PermissionAction.CREATE,
        required = { Permissions.ACCESS_ASSIGN },
        adminOnly = true
    )
    public ManagedAccountResponse create(ManagedAccountCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(
            !bootstrap.username().equals(body.getUsername().toLowerCase(Locale.ROOT)),
            ErrorCode.CONFLICT
        );
        BusinessRules.require(StrongPasswordValidator.valid(body.getPassword()), ErrorCode.VALIDATION_FAILED);
        String hash = passwords.encode(body.getPassword());
        return transactions.execute(connection -> {
            // Cùng khóa với gán role để registry không bị tắt/xóa giữa kiểm tra và INSERT mapping.
            access.lockRoleAssignments(connection);
            access.validateMappings(connection, body.getRoles(), Set.of());
            long id = accounts.create(
                connection,
                body.getUsername().toLowerCase(Locale.ROOT),
                body.getDisplayName().strip(),
                hash,
                actor.id()
            );
            access.replaceRoles(connection, id, body.getRoles(), 0, actor.id());
            changes.record(
                connection,
                Event.ACCOUNT_CREATED,
                Target.USER,
                Long.toString(id),
                actor.id(),
                AccessChangeStateResponse.empty(),
                new AccessChangeStateResponse(
                    body.getRoles().stream().map(Role::name).collect(Collectors.toSet()),
                    Set.of(),
                    body.getRoles().size(),
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
            return ManagedAccountResponse.from(
                users.findById(connection, id, false),
                accounts.assignedRoles(connection, id)
            );
        });
    }

    /** Không xóa lịch sử tài khoản. Khóa user và thu hồi JWT/challenge trong cùng transaction. */
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.UPDATE, adminOnly = true)
    public ManagedAccountResponse state(long id, ManagedAccountStateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id != SystemActor.ID && id != actor.id(), ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var target = BusinessRules.requireFound(users.findById(connection, id, true));
            BusinessRules.require(!bootstrap.username().equals(target.getUsername()), ErrorCode.ACCESS_DENIED);
            BusinessRules.require(!target.isActivationPending(), ErrorCode.CONFLICT);
            // ADMIN registry và tài khoản cứu hộ được bảo vệ; không khóa ADMIN khác bằng màn danh tính.
            BusinessRules.require(!target.getRoles().contains(Role.ADMIN), ErrorCode.ACCESS_DENIED);
            accounts.state(connection, id, body.active(), body.version(), actor.id());
            lifecycle.invalidate(connection, id);
            changes.record(
                connection,
                Event.ACCOUNT_STATUS,
                Target.USER,
                Long.toString(id),
                actor.id(),
                AccessChangeStateResponse.status(target.isActive(), target.getVersion()),
                AccessChangeStateResponse.status(body.active(), body.version() + 1)
            );
            return ManagedAccountResponse.from(
                users.findById(connection, id, false),
                accounts.assignedRoles(connection, id)
            );
        });
    }

    /** Tính preview từ tài khoản DB thật và policy, không phát token/giả mạo principal hiện tại. */
    @AccessOperation(resource = Groups.ACCOUNT, action = PermissionAction.READ, adminOnly = true)
    public ManagedAccountAccessResponse effective(long id, List<AccessResourceResponse> inventory) {
        BusinessRules.require(id != SystemActor.ID, ErrorCode.NOT_FOUND);
        var user = BusinessRules.requireFound(users.findById(id));
        var authorities = Stream.concat(
            user
                .getRoles()
                .stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())),
            user
                .getPermissions()
                .stream()
                .map(permission -> new SimpleGrantedAuthority(permission.name()))
        ).toList();
        var auth = UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(id, user.getUsername(), user.getRoles(), user.getTokenVersion()),
            null,
            authorities
        );
        boolean usable =
            user.isActive() &&
            !user.isActivationPending() &&
            !user.isMustChangePassword() &&
            user.getEmailVerifiedAt() != null;
        return new ManagedAccountAccessResponse(
            ManagedAccountResponse.from(user, accounts.assignedRoles(id)),
            AccessCapabilities.compute(inventory, usable ? guard.refresh(auth) : null, policies.requestPolicies(auth))
        );
    }
}
