package vn.vietroute.auth.service;

import java.time.Instant;

/** Nội dung email/token được mã hóa trong DB; expiry cho worker bỏ mã hết hạn thay vì gửi thư trễ. */
public record AccountMailPayload(String encrypted, Instant expiresAt) {
    public static final String TYPE = "AUTH_ACCOUNT_MAIL";
}
