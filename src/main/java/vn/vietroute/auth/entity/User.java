package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import vn.vietroute.common.base.BaseEntity;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Tài khoản nội bộ; hash, lần đăng nhập sai và tokenVersion không được map ra API. */
@Entity
@Table(name = "auth_users")
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@JdbcTable(
    name = "auth_users",
    primaryKey = { "id" },
    indexes = {
        @JdbcIndex(name = "auth_users_pkey", columns = { "id" }, unique = true),
        @JdbcIndex(name = "auth_users_username_key", columns = { "username" }, unique = true),
        @JdbcIndex(name = "uq_auth_users_email", columns = { "email" }, unique = true),
        @JdbcIndex(name = "idx_auth_users_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_auth_users_updated_by", columns = { "updated_by" }),
    }
)
public class User extends BaseEntity {

    // Tên đăng nhập duy nhất; không đăng nhập bằng email.
    @Column(name = "username", length = 32, nullable = false)
    private String username;

    // Hash BCrypt của mật khẩu; không trả qua API hoặc log.
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    // Tên người dùng hiển thị trên giao diện.
    @Column(name = "display_name", length = 120, nullable = false)
    private String displayName;

    // Email liên hệ, không dùng làm định danh đăng nhập.
    @Column(name = "email", length = 254)
    private String email;

    // Thời điểm tài khoản được xác minh email.
    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    // Tài khoản đang chờ kích hoạt, chưa được đăng nhập.
    @Column(name = "activation_pending")
    private boolean activationPending;

    // Trạng thái cho phép sử dụng bản ghi.
    @Column(name = "active")
    private boolean active;

    // Buộc đổi mật khẩu trước khi dùng các chức năng khác.
    @Column(name = "must_change_password")
    private boolean mustChangePassword;

    // Số lần nhập sai liên tiếp để áp dụng khóa tạm thời.
    @Column(name = "failed_login_attempts")
    private int failedLoginAttempts;

    // Hạn khóa đăng nhập; null khi không bị khóa.
    @Column(name = "locked_until")
    private Instant lockedUntil;

    // Phiên bản phiên đăng nhập; tăng để vô hiệu hóa JWT đã cấp.
    @Column(name = "token_version")
    private long tokenVersion;

    // Mã vai trò được query tổng hợp; không phải cột DB.
    @Builder.Default
    @Transient
    private Set<String> roles = Set.of();

    // Tập đích truy cập được query tổng hợp cho người dùng.
    @Builder.Default
    @Transient
    private Set<String> targets = Set.of();

    // Đích truy cập toàn phạm vi từ chính vai trò cấp quyền.
    @Builder.Default
    @Transient
    private Set<String> globalTargets = Set.of();

    // Hash mật khẩu trước gần nhất để chống sử dụng lại.
    @Column(name = "previous_password_hash_1", length = 100)
    private String previousPasswordHash1;

    // Hash mật khẩu cũ thứ hai để chống sử dụng lại.
    @Column(name = "previous_password_hash_2", length = 100)
    private String previousPasswordHash2;

    // Cờ khởi tạo phạm vi bootstrap chưa hoàn tất.
    @Column(name = "bootstrap_scope_pending")
    private boolean bootstrapScopePending;

    // Đánh dấu xóa mềm, không xóa lịch sử liên quan.
    @Column(name = "deleted")
    private boolean deleted;

    // Phiên bản gán quyền theo phạm vi, khóa cập nhật thành viên đồng thời.
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "scope_membership_versions", columnDefinition = "jsonb")
    private Map<String, Long> scopeMembershipVersions = new LinkedHashMap<>();
}
