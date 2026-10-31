package vn.vietroute.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.recovery.AdminRecoveryRequest;
import vn.vietroute.auth.dto.record.recovery.AdminRecoveryResponse;
import vn.vietroute.auth.repository.AccessChangeRepository;
import vn.vietroute.auth.repository.AdminRecoveryRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Đường cứu hộ riêng của tài khoản bootstrap: không phụ thuộc ADMIN/ACCESS_* đang cần sửa. */
@Service
@RequiredArgsConstructor
public class AdminRecoveryService {

    private final BootstrapAdminInitializer bootstrap;

    private final AdminRecoveryRepository recovery;

    private final AccessResourceService resources;

    private final AccessChangeRepository history;

    private final UserRepository users;

    private final JdbcTransactions transactions;

    private final PasswordEncoder passwords;

    private final Clock clock;

    /** Chỉ gợi UI cho username bootstrap; mutation vẫn xác minh ID/username/tokenVersion từ DB dưới khóa. */
    public boolean visible(UserPrincipal actor) {
        return actor != null && actor.id() != null && actor.id() > 0 && bootstrap.username().equals(actor.username());
    }

    /**
     * Khóa mapping rồi account theo thứ tự chung; kiểm mật khẩu, sửa registry/mapping và ghi lịch sử cùng transaction.
     * Sai mật khẩu trả outcome để commit bộ đếm 5 lần/15 phút trước khi báo lỗi; lỗi lịch sử rollback toàn bộ sửa quyền.
     */
    @AccessOperation(authenticated = true)
    public AdminRecoveryResponse recover(AdminRecoveryRequest request, UserPrincipal actor, String traceId) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(visible(actor), ErrorCode.ACCESS_DENIED);
        var outcome = transactions.execute(connection -> {
            recovery.lock(connection);
            var user = BusinessRules.requireFound(users.findById(connection, actor.id(), true));
            BusinessRules.require(
                bootstrap.username().equals(user.getUsername()) &&
                    user.isActive() &&
                    !user.isActivationPending() &&
                    user.getTokenVersion() == actor.tokenVersion(),
                ErrorCode.ACCESS_DENIED
            );
            BusinessRules.require(!user.isMustChangePassword(), ErrorCode.PASSWORD_CHANGE_REQUIRED);
            Instant now = Instant.now(clock);
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(now)) return new Outcome(
                null,
                ErrorCode.ACCOUNT_LOCKED
            );
            if (
                request.currentPassword().getBytes(StandardCharsets.UTF_8).length > 72 ||
                !passwords.matches(request.currentPassword(), user.getPasswordHash())
            ) {
                int failures = user.getLockedUntil() != null ? 1 : user.getFailedLoginAttempts() + 1;
                users.recordFailure(
                    connection,
                    user.getId(),
                    failures,
                    failures >= 5 ? now.plus(Duration.ofMinutes(15)) : null
                );
                return new Outcome(null, failures >= 5 ? ErrorCode.ACCOUNT_LOCKED : ErrorCode.PASSWORD_INCORRECT);
            }
            var before = recovery.state(connection, actor.id());
            resources.restore(connection, actor.id());
            var after = recovery.state(connection, actor.id());
            history.append(
                connection,
                Event.ADMIN_RECOVERED,
                Target.USER,
                String.valueOf(actor.id()),
                actor.id(),
                traceId,
                before,
                after
            );
            users.resetFailures(connection, actor.id());
            // Thu hồi mọi phiên sau thao tác cứu hộ; không trả JWT/hash hoặc phát token mạo danh.
            users.revokeTokens(connection, actor.id());
            return new Outcome(new AdminRecoveryResponse(actor.id(), after.permissions().size(), true), null);
        });
        if (outcome.error() != null) throw new BusinessException(outcome.error());
        return outcome.response();
    }

    private record Outcome(AdminRecoveryResponse response, ErrorCode error) {}
}
