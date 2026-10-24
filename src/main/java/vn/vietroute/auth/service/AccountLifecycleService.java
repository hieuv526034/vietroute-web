package vn.vietroute.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.config.AccountSettings;
import vn.vietroute.auth.dto.request.ActivationRequest;
import vn.vietroute.auth.dto.request.BindEmailRequest;
import vn.vietroute.auth.dto.request.RegisterRequest;
import vn.vietroute.auth.dto.request.ResetPasswordRequest;
import vn.vietroute.auth.dto.response.AccountOptionsResponse;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.AccountRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccountSecrets;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.config.integration.JobSettings;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.repository.JobRepository;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.validation.BusinessRules;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Vòng đời email sở hữu transaction; HTTP không nhận user ID/role và email chỉ gửi sau outbox commit. */
@Service
@RequiredArgsConstructor
public class AccountLifecycleService {

    private final BootstrapAdminInitializer bootstrap;

    private final AccountSettings settings;

    private final MailSettings mail;

    private final JobSettings worker;

    private final AccountRepository accounts;

    private final AccessRepository access;

    private final UserRepository users;

    private final JdbcTransactions transactions;

    private final PasswordEncoder passwords;

    private final AccountSecrets secrets;

    private final JobRepository jobs;

    private final ObjectMapper json;

    private final MessageSource messages;

    private final Clock clock;

    /** Khả năng cấu hình, không hứa mail đã tới inbox hoặc kết nối provider đã được nghiệm thu. */
    public AccountOptionsResponse options() {
        return new AccountOptionsResponse(settings.isEnabled() && mail.isEnabled() && worker.isEnabled(), 5, 15, 2);
    }

    /** Duplicate và vượt cap đều trả cùng kết quả chung; chỉ tài khoản mới được gán CUSTOMER từ server. */
    public void register(RegisterRequest body, Locale locale) {
        requireAvailable();
        BusinessRules.require(StrongPasswordValidator.valid(body.getPassword()), ErrorCode.VALIDATION_FAILED);
        // Trả cùng phản hồi chung, không cho đăng ký chiếm username bootstrap nếu dữ liệu quản trị đang bị mất.
        if (bootstrap.username().equals(body.getUsername().toLowerCase(Locale.ROOT))) return;
        String email = email(body.getEmail());
        String hash = passwords.encode(body.getPassword());
        transactions.execute(connection -> {
            // Cùng khóa với gán/xóa role: kiểm tham chiếu và tạo CUSTOMER không được chạy lọt nhau giữa các node.
            // Thứ tự access → registration → user; BCrypt đã chạy ngoài transaction để không giữ khóa lâu.
            access.lockRoleAssignments(connection);
            accounts.lockRegistration(connection);
            Long id = accounts.register(
                connection,
                body.getUsername().toLowerCase(Locale.ROOT),
                body.getDisplayName().strip(),
                email,
                hash,
                settings.getRegistrationsPerDay()
            );
            if (id != null) issue(connection, users.findById(connection, id, true), "ACTIVATE", locale);
            return null;
        });
    }

    /** Phản hồi không phân biệt email tồn tại, tài khoản khóa hay chưa xác minh; có cooldown DB theo user. */
    public void requestCode(String rawEmail, boolean reset, Locale locale) {
        requireAvailable();
        transactions.execute(connection -> {
            String address = email(rawEmail);
            Long id = accounts.findByEmail(connection, address);
            if (id == null) return null;
            UserAccount user = users.findById(connection, id, true);
            if (!address.equals(user.getEmail())) return null;
            if (
                reset
                    ? user.isActive() && user.getEmailVerifiedAt() != null
                    : user.getEmailVerifiedAt() == null && (user.isActive() || user.isActivationPending())
            ) {
                issue(connection, user, reset ? "RESET" : "ACTIVATE", locale);
            }
            return null;
        });
    }

    /** Xác minh một lần; không kích hoạt lại tài khoản đã bị quản trị viên vô hiệu hóa. */
    public void activate(ActivationRequest body) {
        requireAvailable();
        transactions.execute(connection -> {
            UserAccount user = lockedUser(connection, body.getEmail());
            BusinessRules.require(
                user.getEmailVerifiedAt() == null && (user.isActive() || user.isActivationPending()),
                ErrorCode.ACCOUNT_CODE_INVALID
            );
            consume(connection, user, "ACTIVATE", body.getToken());
            accounts.activate(connection, user.getId());
            accounts.invalidate(connection, user.getId());
            return null;
        });
    }

    /** Reset không tự login; mã và lịch sử/password/tokenVersion cập nhật atomically, lỗi rollback cả việc tiêu thụ mã. */
    public void reset(ResetPasswordRequest body) {
        requireAvailable();
        transactions.execute(connection -> {
            UserAccount user = lockedUser(connection, body.getEmail());
            BusinessRules.require(user.isActive() && user.getEmailVerifiedAt() != null, ErrorCode.ACCOUNT_CODE_INVALID);
            consume(connection, user, "RESET", body.getToken());
            users.requireNewPassword(connection, user, body.getNewPassword(), passwords);
            users.changePassword(connection, user.getId(), passwords.encode(body.getNewPassword()), SystemActor.ID);
            accounts.invalidate(connection, user.getId());
            return null;
        });
    }

    /** Admin cũ/tài khoản đăng nhập được có thể gắn email; cần JWT còn hạn và mật khẩu hiện tại, email mới phải xác minh. */
    @AccessOperation(authenticated = true)
    public void bindEmail(UserPrincipal principal, BindEmailRequest body, Locale locale) {
        requireAvailable();
        transactions.execute(connection -> {
            // Thứ tự khóa registration trước user thống nhất với đăng ký để tránh deadlock.
            accounts.lockRegistration(connection);
            UserAccount user = BusinessRules.requireFound(users.findById(connection, principal.id(), true));
            BusinessRules.require(
                user.isActive() && user.getTokenVersion() == principal.tokenVersion(),
                ErrorCode.TOKEN_INVALID
            );
            BusinessRules.require(
                body.getCurrentPassword().getBytes(StandardCharsets.UTF_8).length <= 72 &&
                    passwords.matches(body.getCurrentPassword(), user.getPasswordHash()),
                ErrorCode.PASSWORD_INCORRECT
            );
            String address = email(body.getEmail());
            Long owner = accounts.findByEmail(connection, address);
            BusinessRules.require(owner == null || owner.equals(user.getId()), ErrorCode.CONFLICT);
            if (address.equals(user.getEmail()) && user.getEmailVerifiedAt() != null) return null;
            if (address.equals(user.getEmail()) && user.getEmailVerifiedAt() == null) {
                issue(connection, user, "ACTIVATE", locale);
                return null;
            }
            accounts.bindEmail(connection, user.getId(), address);
            issue(connection, users.findById(connection, user.getId(), false), "ACTIVATE", locale);
            return null;
        });
    }

    private UserAccount lockedUser(Connection connection, String rawEmail) throws SQLException {
        String address = email(rawEmail);
        Long id = accounts.findByEmail(connection, address);
        BusinessRules.require(id != null, ErrorCode.ACCOUNT_CODE_INVALID);
        UserAccount user = users.findById(connection, id, true);
        BusinessRules.require(address.equals(user.getEmail()), ErrorCode.ACCOUNT_CODE_INVALID);
        return user;
    }

    private void consume(Connection connection, UserAccount user, String purpose, String token) throws SQLException {
        BusinessRules.require(
            accounts.consume(connection, user.getId(), purpose, secrets.hash(token), user.getTokenVersion()),
            ErrorCode.ACCOUNT_CODE_INVALID
        );
    }

    /** Ghi challenge và outbox mã hóa trong cùng transaction; không gọi Gmail/SMTP dưới khóa DB. */
    private void issue(Connection connection, UserAccount user, String purpose, Locale locale) throws SQLException {
        Instant expires = Instant.now(clock).plus(
            purpose.equals("RESET") ? Duration.ofMinutes(15) : Duration.ofHours(24)
        );
        String token = secrets.token();
        if (
            !accounts.challenge(connection, user.getId(), purpose, secrets.hash(token), user.getTokenVersion(), expires)
        ) return;
        String key = purpose.equals("RESET") ? "mail.account.reset" : "mail.account.activate";
        MailPayload payload = new MailPayload(
            user.getEmail(),
            messages.getMessage(key + ".subject", null, locale),
            messages.getMessage(key + ".text", new Object[] { token }, locale)
        );
        try {
            AccountMailPayload protectedPayload = new AccountMailPayload(
                secrets.encrypt(json.writeValueAsString(payload)),
                expires
            );
            jobs.enqueue(
                connection,
                AccountMailPayload.TYPE,
                "account-mail:" + user.getId() + ":" + UUID.randomUUID(),
                json.writeValueAsString(protectedPayload),
                SystemActor.ID
            );
        } catch (JsonProcessingException error) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR);
        }
    }

    private void requireAvailable() {
        BusinessRules.require(options().emailFlowsEnabled(), ErrorCode.ACCOUNT_FLOW_DISABLED);
    }

    private static String email(String value) {
        String result = value.strip().toLowerCase(Locale.ROOT);
        BusinessRules.require(MailSettings.validAddress(result), ErrorCode.INVALID_REQUEST);
        return result;
    }
}
