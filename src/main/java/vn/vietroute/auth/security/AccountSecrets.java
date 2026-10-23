package vn.vietroute.auth.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.application.AppSettings;

/** Token ngẫu nhiên 256 bit; outbox auth mã hóa AES-GCM, khóa dẫn xuất tách miền khỏi khóa ký JWT. */
@Component
public final class AccountSecrets {

    private final SecureRandom random = new SecureRandom();

    private final SecretKeySpec key;

    public AccountSecrets(AppSettings settings) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("vietroute-account-mail-v1:".getBytes(StandardCharsets.UTF_8));
            key = new SecretKeySpec(digest.digest(settings.getJwtKey().getEncoded()), "AES");
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Không khởi tạo được mật mã tài khoản.");
        }
    }

    /** Token một lần khác JWT, không ghi log/response hoặc truyền trên query string. */
    public String token() {
        byte[] value = new byte[32];
        random.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    /** Token đủ entropy nên SHA-256 không cần BCrypt; ràng buộc mục đích/user bằng dòng challenge riêng. */
    public String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Không tạo được hash token.");
        }
    }

    /** Nonce ngẫu nhiên mỗi email; auth tag ngăn sửa ciphertext trước khi worker giải mã. */
    public String encrypt(String content) {
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));
            byte[] result = Arrays.copyOf(nonce, nonce.length + encrypted.length);
            System.arraycopy(encrypted, 0, result, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException error) {
            throw new IllegalStateException("Không mã hóa được nội dung email.");
        }
    }

    /** Chỉ worker nội bộ giải mã; đổi JWT secret cũng làm outbox auth đang chờ không giải mã được. */
    public String decrypt(String payload) throws GeneralSecurityException {
        byte[] bytes = Base64.getDecoder().decode(payload);
        if (bytes.length < 29) {
            throw new GeneralSecurityException("Invalid encrypted payload");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, Arrays.copyOf(bytes, 12)));
        return new String(cipher.doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.UTF_8);
    }
}
