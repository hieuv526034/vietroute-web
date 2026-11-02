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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "purpose", length = 16)
    private String purpose;

    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "token_version")
    private long tokenVersion;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "created_by")
    private long createdBy;

    @Column(name = "updated_by")
    private long updatedBy;
}
