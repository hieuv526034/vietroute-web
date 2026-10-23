package vn.vietroute.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.ChangePasswordRequest;
import vn.vietroute.auth.dto.request.LoginRequest;
import vn.vietroute.auth.dto.response.AuthResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Nghiệp vụ auth sở hữu transaction, không để controller cập nhật hash/phiên bản token. */
@Service
public class AuthService {

    private final UserRepository users;

    private final JdbcTransactions transactions;

    private final PasswordEncoder passwords;

    private final JwtService jwt;

    private final Clock clock;

    private final String dummyHash;

    public AuthService(
        UserRepository users,
        JdbcTransactions transactions,
        PasswordEncoder passwords,
        JwtService jwt,
        Clock clock
    ) {
        this.users = users;
        this.transactions = transactions;
        this.passwords = passwords;
        this.jwt = jwt;
        this.clock = clock;
        dummyHash = passwords.encode("VietRoute-Dummy-Password-2026!");
    }

    /** Commit số lần sai trước khi ném BusinessException; nếu ném trong transaction sẽ bị rollback bộ đếm. */
    public AuthResponse login(LoginRequest request) {
        LoginOutcome outcome = transactions.execute(connection -> {
            // Khóa dòng user để các lần đăng nhập đồng thời không làm mất bộ đếm sai.
            UserAccount user = users.findByUsername(connection, request.getUsername().toLowerCase(Locale.ROOT), true);
            Instant now = Instant.now(clock);
            if (user == null || user.getId() == SystemActor.ID) {
                // Vẫn kiểm BCrypt để giảm khác biệt thời gian giữa username không tồn tại và mật khẩu sai.
                passwords.matches(
                    request.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72
                        ? request.getPassword()
                        : "invalid",
                    dummyHash
                );
                return new LoginOutcome(null, ErrorCode.INVALID_CREDENTIALS);
            }
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) {
                return new LoginOutcome(null, ErrorCode.ACCOUNT_LOCKED);
            }
            boolean valid =
                request.getPassword().getBytes(StandardCharsets.UTF_8).length <= 72 &&
                passwords.matches(request.getPassword(), user.getPasswordHash());
            if (!valid || !user.isActive()) {
                // Hết khóa thì bắt đầu đếm lại; trả outcome để transaction commit trước khi báo lỗi HTTP.
                int attempts = user.getLockedUntil() != null ? 1 : user.getFailedLoginAttempts() + 1;
                users.recordFailure(
                    connection,
                    user.getId(),
                    attempts,
                    attempts >= 5 ? now.plus(Duration.ofMinutes(15)) : null
                );
                return new LoginOutcome(null, attempts >= 5 ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.INVALID_CREDENTIALS);
            }
            users.resetFailures(connection, user.getId());
            return new LoginOutcome(users.findById(connection, user.getId(), false), null);
        });
        if (outcome.error() != null) {
            throw new BusinessException(outcome.error());
        }
        return jwt.issue(outcome.user());
    }

    /** Đọc thông tin mới nhất và map sang response an toàn, không trả hash/tokenVersion của entity. */
    @PreAuthorize("isAuthenticated()")
    public UserResponse me(UserPrincipal principal) {
        return UserResponse.from(BusinessRules.requireFound(users.findById(principal.id())));
    }

    /** Khóa dòng tài khoản để hai yêu cầu đổi mật khẩu không cùng dùng mật khẩu cũ thành công. */
    @PreAuthorize("isAuthenticated()")
    public AuthResponse changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        BusinessRules.require(StrongPasswordValidator.valid(request.getNewPassword()), ErrorCode.VALIDATION_FAILED);
        UserAccount changed = transactions.execute(connection -> {
            // So khớp tokenVersion dưới khóa trước khi cập nhật, tránh dùng token vừa bị thu hồi.
            UserAccount user = BusinessRules.requireFound(users.findById(connection, principal.id(), true));
            BusinessRules.require(user.getTokenVersion() == principal.tokenVersion(), ErrorCode.TOKEN_INVALID);
            BusinessRules.require(
                request.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length <= 72 &&
                    passwords.matches(request.getCurrentPassword(), user.getPasswordHash()),
                ErrorCode.PASSWORD_INCORRECT
            );
            BusinessRules.require(
                !passwords.matches(request.getNewPassword(), user.getPasswordHash()),
                ErrorCode.PASSWORD_REUSED
            );
            users.changePassword(connection, user.getId(), passwords.encode(request.getNewPassword()));
            return users.findById(connection, user.getId(), false);
        });
        return jwt.issue(changed);
    }

    /** Logout thu hồi mọi JWT cũ của tài khoản; gọi lại khi anonymous vẫn xóa cookie được. */
    public void logout(UserPrincipal principal) {
        if (principal != null) {
            transactions.execute(connection -> {
                users.revokeTokens(connection, principal.id());
                return null;
            });
        }
    }

    private record LoginOutcome(UserAccount user, ErrorCode error) {}
}
