package vn.vietroute.auth.service;

import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nội dung email/token được mã hóa trong DB; expiry cho worker bỏ mã hết hạn thay vì gửi thư trễ. */
@Getter
@NoArgsConstructor
@Setter
public class AccountMailPayload {

    private String encrypted;

    private Instant expiresAt;

    public AccountMailPayload(String encrypted, Instant expiresAt) {
        this.encrypted = encrypted;
        this.expiresAt = expiresAt;
    }

    public String encrypted() {
        return encrypted;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public static final String TYPE = "AUTH_ACCOUNT_MAIL";
}
