package vn.vietroute.auth.service;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.AccountChallengeRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.repo.custom.AccessChangeCustomRepo;
import vn.vietroute.auth.repo.custom.UserRoleCustomRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Luồng tài khoản tập trung: đăng nhập/khóa, đổi mật khẩu, profile và CRUD; không còn email/outbox/recovery phụ. */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepo users;
    private final UserRoleRepo mappings;
    private final UserRoleCustomRepo roleBatch;
    private final AccountChallengeRepo challenges;
    private final AccessChangeRepo changes;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final JdbcTransactions transactions;
    private final AppSettings settings;
    private final AuthInitializer initializer;
    private final Clock clock;
    private String dummyHash;

    private final AccessChangeCustomRepo changeBatch;
    private final PlatformTransactionManager transactionManager;

    @PostConstruct
    public void initializePasswordTiming() {
        dummyHash = passwords.encode("VietRoute-Dummy-Password-2026!");
    }

    /** Kết quả lỗi được ném sau commit để 5 lần sai vẫn lưu đủ và khóa 15 phút. */
    public UserDTO login(UserRequest request) {
        BusinessRules.require(
            request.getUsername() != null && request.getPassword() != null,
            ErrorCode.VALIDATION_FAILED
        );
        ErrorCode[] failure = new ErrorCode[1];
        UserDTO result = new TransactionTemplate(transactionManager).execute(status -> {
            User user = users.findByUsername(request.getUsername().toLowerCase(Locale.ROOT), true);
            Instant now = Instant.now(clock);
            if (user == null || user.getId() <= 0) {
                passwords.matches(request.getPassword(), dummyHash);
                failure[0] = ErrorCode.INVALID_CREDENTIALS;
                return null;
            }
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                failure[0] = ErrorCode.ACCOUNT_LOCKED;
                return null;
            }
            if (
                request.getPassword().getBytes(StandardCharsets.UTF_8).length > 72 ||
                !passwords.matches(request.getPassword(), user.getPasswordHash())
            ) {
                int count = user.getLockedUntil() != null ? 1 : user.getFailedLoginAttempts() + 1;
                users.recordFailure(user.getId(), count, count >= 5 ? now.plus(Duration.ofMinutes(15)) : null);
                failure[0] = count >= 5 ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.INVALID_CREDENTIALS;
                return null;
            }
            if (!user.isActive() || user.isActivationPending()) {
                failure[0] = ErrorCode.INVALID_CREDENTIALS;
                return null;
            }
            return jwt.issue(users.completeLogin(user.getId()));
        });
        if (failure[0] != null) throw new BusinessException(failure[0]);
        return result;
    }

    public UserDTO me(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return UserDTO.from(requireSession(users.load(actor.id()), actor));
    }

    public PageResponse<UserDTO> search(UserSearch search) {
        UserPrincipal.current();
        return users.search(search);
    }

    public UserDTO get(long id) {
        UserPrincipal.current();
        BusinessRules.require(id > 0, ErrorCode.NOT_FOUND);
        return UserDTO.from(BusinessRules.requireFound(users.load(id)));
    }

    /** JPA CRUD, mapping/challenge và lịch sử JDBC cùng transaction: lỗi history rollback toàn bộ. */
    public UserDTO create(UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getRoles() != null && !body.getRoles().isEmpty(), ErrorCode.INVALID_REQUEST);
        String hash = passwordHash(body.getPassword());
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments();
            mappings.validateMappings(body.getRoles(), Set.of());
            mappings.requireDelegableRoles(body.getRoles());
            User value = createModel(body, hash, actor.id());
            users.saveAndFlush(value);
            roleBatch.replaceRoles(connection, value.getId(), body.getRoles(), value.getVersion(), actor.id());
            changes.append(
                "ACCOUNT_CREATED",
                "USER",
                value.getId().toString(),
                actor.id(),
                Map.of(),
                Map.of("roles", body.getRoles(), "active", true)
            );
            return UserDTO.from(users.load(value.getId(), false));
        });
    }

    @Transactional
    public UserDTO update(long id, UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        mappings.lockRoleAssignments();
        User value = managed(id, body.getVersion(), actor);
        String name = value.getDisplayName();
        BusinessRules.require(
            body.getDisplayName() != null && !body.getDisplayName().isBlank(),
            ErrorCode.INVALID_REQUEST
        );
        value.setDisplayName(body.getDisplayName().strip());
        audit(value, actor.id());
        users.saveAndFlush(value);
        changes.append(
            "ACCOUNT_UPDATED",
            "USER",
            Long.toString(id),
            actor.id(),
            Map.of("displayName", name),
            Map.of("displayName", value.getDisplayName())
        );
        return UserDTO.from(users.load(id, false));
    }

    @Transactional
    public UserDTO state(long id, UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getActive() != null, ErrorCode.INVALID_REQUEST);
        mappings.lockRoleAssignments();
        User value = managed(id, body.getVersion(), actor);
        boolean before = value.isActive();
        value.setActive(body.getActive());
        value.setTokenVersion(value.getTokenVersion() + 1);
        audit(value, actor.id());
        users.saveAndFlush(value);
        challenges.invalidate(id);
        changes.append(
            "ACCOUNT_STATUS",
            "USER",
            Long.toString(id),
            actor.id(),
            Map.of("active", before),
            Map.of("active", value.isActive())
        );
        return UserDTO.from(users.load(id, false));
    }

    public void delete(long id, long version, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        transactions.execute(connection -> {
            mappings.lockRoleAssignments();
            User value = managed(id, version, actor);
            Set<String> before = mappings.assignedRoles(id);
            List<RoleDTO> scopedBefore = mappings.memberships(id);
            roleBatch.replaceRoles(connection, id, Set.of(), value.getVersion(), actor.id());
            changeBatch.appendMemberships(connection, id, scopedBefore, List.of(), actor.id());
            BusinessRules.require(users.softDelete(id, actor.id()) == 1, ErrorCode.STALE_VERSION);
            challenges.invalidate(id);
            changes.append(
                "ACCOUNT_DELETED",
                "USER",
                Long.toString(id),
                actor.id(),
                Map.of("roles", before),
                Map.of("deleted", true, "roles", Set.of())
            );
            return null;
        });
    }

    /** Mật khẩu hiện tại và hai hash cũ được so BCrypt; giữ hash/history ngoài DTO/log. */
    public void password(UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        String hash = passwordHash(body.getNewPassword());
        ErrorCode[] failure = new ErrorCode[1];
        new TransactionTemplate(transactionManager).execute(status -> {
            User value = BusinessRules.requireFound(users.load(actor.id(), true));
            requireSession(value, actor);
            Instant now = Instant.now(clock);
            if (value.getLockedUntil() != null && value.getLockedUntil().isAfter(now)) {
                failure[0] = ErrorCode.ACCOUNT_LOCKED;
                return null;
            }
            if (
                body.getCurrentPassword() == null ||
                body.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length > 72 ||
                !passwords.matches(body.getCurrentPassword(), value.getPasswordHash())
            ) {
                int count = value.getLockedUntil() != null ? 1 : value.getFailedLoginAttempts() + 1;
                users.recordFailure(actor.id(), count, count >= 5 ? now.plusSeconds(900) : null);
                failure[0] = count >= 5 ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.PASSWORD_INCORRECT;
                return null;
            }
            BusinessRules.require(
                !passwords.matches(body.getNewPassword(), value.getPasswordHash()),
                ErrorCode.PASSWORD_REUSED
            );
            for (String previous : users.previousPasswords(value.getId())) {
                BusinessRules.require(!passwords.matches(body.getNewPassword(), previous), ErrorCode.PASSWORD_REUSED);
            }
            users.changePassword(actor.id(), hash);
            challenges.invalidate(actor.id());
            return null;
        });
        if (failure[0] != null) throw new BusinessException(failure[0]);
    }

    @Transactional
    public void logout(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        users.revokeTokens(actor.id());
        challenges.invalidate(actor.id());
    }

    /** Ảnh khóa trước account, đúng owner và READY; profile không thay role/status/email. */
    @Transactional
    public UserDTO profile(UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        User value = BusinessRules.requireFound(users.lock(actor.id()));
        requireSession(value, actor);
        version(value, body.getVersion());
        BusinessRules.require(
            body.getDisplayName() != null && !body.getDisplayName().isBlank(),
            ErrorCode.INVALID_REQUEST
        );
        value.setDisplayName(body.getDisplayName().strip());
        audit(value, actor.id());
        users.saveAndFlush(value);
        return UserDTO.from(users.load(actor.id(), false));
    }

    public String passwordHash(String value) {
        BusinessRules.require(BusinessRules.validPassword(value), ErrorCode.VALIDATION_FAILED);
        return passwords.encode(value);
    }

    /** Callback nghiệp vụ tạo danh tính tài xế; không tạo membership nhà xe ngoài scope đã kiểm. */
    public long provisionDriver(
        Connection connection,
        long scope,
        String name,
        UserRequest body,
        String hash,
        UserPrincipal actor
    ) throws SQLException {
        UserPrincipal.requireCurrent(actor);
        mappings.require(scope);
        mappings.lockRoleAssignments();
        String role = initializer.driverRole();
        mappings.validateMappings(Set.of(role), Set.of());
        mappings.requireDelegableRoles(Set.of(role));
        body.setDisplayName(name);
        User value = createModel(body, hash, actor.id());
        users.saveAndFlush(value);
        roleBatch.replaceRoles(connection, value.getId(), Set.of(role), 0, actor.id());
        UserRequest membership = new UserRequest();
        membership.setRoles(Set.of(role));
        membership.setActive(true);
        membership.setVersion(0L);
        roleBatch.assign(connection, scope, value.getId(), membership, actor.id());
        changes.append(
            "SCOPE_MEMBERSHIP",
            "MEMBERSHIP",
            scope + ":" + value.getId(),
            actor.id(),
            Map.of("roles", Set.of()),
            Map.of("roles", Set.of(role), "version", 1)
        );
        changes.append(
            "ACCOUNT_CREATED",
            "USER",
            value.getId().toString(),
            actor.id(),
            Map.of(),
            Map.of("roles", Set.of(role), "scopeId", scope)
        );
        return value.getId();
    }

    private User createModel(UserRequest body, String hash, long actor) {
        BusinessRules.require(
            body.getUsername() != null &&
                body.getUsername().matches(Regex.USERNAME) &&
                body.getDisplayName() != null &&
                !body.getDisplayName().isBlank(),
            ErrorCode.INVALID_REQUEST
        );
        String username = body.getUsername().toLowerCase(Locale.ROOT);
        String email =
            body.getEmail() == null || body.getEmail().isBlank()
                ? null
                : body.getEmail().strip().toLowerCase(Locale.ROOT);
        BusinessRules.require(
            !username.equalsIgnoreCase(settings.getBootstrapUsername()) &&
                !users.existsUsername(username) &&
                !users.existsEmail(email),
            ErrorCode.CONFLICT
        );
        User value = new User();
        value.setUsername(username);
        value.setDisplayName(body.getDisplayName().strip());
        value.setEmail(email);
        value.setPasswordHash(hash);
        value.setEmailVerifiedAt(Instant.now(clock));
        value.setActive(true);
        value.setMustChangePassword(true);
        value.setCreatedAt(Instant.now(clock));
        value.setCreatedBy(actor);
        audit(value, actor);
        return value;
    }

    private User managed(long id, Long version, UserPrincipal actor) {
        BusinessRules.require(id > 0 && id != actor.id() && !mappings.protectedUser(id), ErrorCode.ACCESS_DENIED);
        User value = BusinessRules.requireFound(users.lock(id));
        BusinessRules.require(
            !value.isDeleted() && !value.getUsername().equalsIgnoreCase(settings.getBootstrapUsername()),
            ErrorCode.ACCESS_DENIED
        );
        version(value, version);
        return value;
    }

    private static User requireSession(User value, UserPrincipal actor) {
        BusinessRules.require(
            value != null &&
                value.isActive() &&
                !value.isDeleted() &&
                !value.isActivationPending() &&
                value.getTokenVersion() == actor.tokenVersion(),
            ErrorCode.TOKEN_INVALID
        );
        return value;
    }

    private static void version(User value, Long version) {
        BusinessRules.require(version != null && value.getVersion() == version, ErrorCode.STALE_VERSION);
    }

    private void audit(User value, long actor) {
        value.setUpdatedAt(Instant.now(clock));
        value.setUpdatedBy(actor);
    }
}
