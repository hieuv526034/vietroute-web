package vn.vietroute.auth.service;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.ManagedAccountCreateRequest;
import vn.vietroute.auth.dto.request.ManagedAccountSearchRequest;
import vn.vietroute.auth.dto.request.ManagedAccountStateRequest;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.dto.response.ManagedAccountAccessResponse;
import vn.vietroute.auth.dto.response.ManagedAccountResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.AccountRepository;
import vn.vietroute.auth.repository.ManagedAccountRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessCapabilities;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Admin cấp danh tính độc lập; role DRIVER không tự tạo hồ sơ/membership nhà xe hoặc cấp quyền nghiệp vụ. */
@Service
@RequiredArgsConstructor
public class ManagedAccountService {

    private final ManagedAccountRepository accounts;

    private final UserRepository users;

    private final AccessRepository access;

    private final AccountRepository lifecycle;

    private final PasswordEncoder passwords;

    private final JdbcTransactions transactions;

    private final AccessManagementService policies;

    /** Danh sách chỉ ADMIN có ACCOUNT_READ; không cho OPERATOR tra danh tính ngoài nhà xe. */
    @PreAuthorize(AccessRules.ACCOUNT_READ)
    public PageResponse<ManagedAccountResponse> page(ManagedAccountSearchRequest page) {
        return accounts.page(page);
    }

    /** Hash BCrypt trước transaction để không giữ connection/khóa trong lúc tính toán mật khẩu. */
    @PreAuthorize(AccessRules.ACCOUNT_CREATE)
    public ManagedAccountResponse create(ManagedAccountCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
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
            return ManagedAccountResponse.from(
                users.findById(connection, id, false),
                accounts.assignedRoles(connection, id)
            );
        });
    }

    /** Không xóa lịch sử tài khoản. Khóa user và thu hồi JWT/challenge trong cùng transaction. */
    @PreAuthorize(AccessRules.ACCOUNT_UPDATE)
    public ManagedAccountResponse state(long id, ManagedAccountStateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id != SystemActor.ID && id != actor.id(), ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var target = BusinessRules.requireFound(users.findById(connection, id, true));
            BusinessRules.require(!target.isActivationPending(), ErrorCode.CONFLICT);
            // ADMIN registry và tài khoản cứu hộ được bảo vệ; không khóa ADMIN khác bằng màn danh tính.
            BusinessRules.require(!target.getRoles().contains(Role.ADMIN), ErrorCode.ACCESS_DENIED);
            accounts.state(connection, id, body.active(), body.version(), actor.id());
            lifecycle.invalidate(connection, id);
            return ManagedAccountResponse.from(
                users.findById(connection, id, false),
                accounts.assignedRoles(connection, id)
            );
        });
    }

    /** Tính preview từ tài khoản DB thật và policy, không phát token/giả mạo principal hiện tại. */
    @PreAuthorize(AccessRules.ACCOUNT_READ)
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
        boolean usable = user.isActive() && !user.isActivationPending() && !user.isMustChangePassword();
        return new ManagedAccountAccessResponse(
            ManagedAccountResponse.from(user, accounts.assignedRoles(id)),
            AccessCapabilities.compute(inventory, usable ? auth : null, policies.requestPolicies(auth))
        );
    }
}
