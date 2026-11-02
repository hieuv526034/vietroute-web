package vn.vietroute.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import vn.vietroute.common.jdbc.JdbcTable;

/** Model đầy đủ cột bảng auth_account_challenges; chỉ DTO mới được nhận/trả ở API. */
@Entity
@Table(name = "auth_account_challenges")
@JdbcTable(name = "auth_account_challenges", primaryKey = { "user_id", "purpose" })
@Getter
@Setter
@NoArgsConstructor
public class AccountChallenge {

    // Khóa định danh bản ghi.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // ID tài khoản liên kết trong auth_users.
    @Column(name = "user_id")
    private Long userId;

    // Mục đích challenge nội bộ; hiện không mở API đăng ký/reset.
    @Column(name = "purpose", length = 16)
    private String purpose;

    // Hash của token challenge; không lưu token thô.
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    // Phiên bản tài khoản tại lúc cấp challenge.
    @Column(name = "token_version")
    private long tokenVersion;

    // Thời điểm challenge hết hạn.
    @Column(name = "expires_at")
    private Instant expiresAt;

    // Thời điểm tạo, lưu theo UTC.
    @Column(name = "created_at")
    private Instant createdAt;

    // Thời điểm cập nhật gần nhất, lưu theo UTC.
    @Column(name = "updated_at")
    private Instant updatedAt;

    // ID người tạo; tác vụ hệ thống dùng tài khoản system (0).
    @Column(name = "created_by")
    private long createdBy;

    // ID người sửa gần nhất; tác vụ hệ thống dùng system (0).
    @Column(name = "updated_by")
    private long updatedBy;
}
