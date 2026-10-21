package vn.vietroute.auth.service;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.response.AuthResponse;
import vn.vietroute.auth.dto.response.UserResponse;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.common.config.AppSettings;

/** Ký/xác minh bằng Nimbus của Spring Security; không tự cài đặt thuật toán JWT. */
@Service
public class JwtService {

    private final AppSettings settings;

    private final Clock clock;

    private final JwtEncoder encoder;

    private final NimbusJwtDecoder decoder;

    public JwtService(AppSettings settings, Clock clock) {
        this.settings = settings;
        this.clock = clock;
        encoder = new NimbusJwtEncoder(new ImmutableSecret<>(settings.getJwtKey()));
        decoder = NimbusJwtDecoder.withSecretKey(settings.getJwtKey()).macAlgorithm(MacAlgorithm.HS256).build();
        OAuth2TokenValidator<Jwt> audience = token ->
            token.getAudience().contains(settings.getJwtAudience())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token"));
        decoder.setJwtValidator(
            new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(settings.getJwtIssuer()),
                audience
            )
        );
    }

    /** Vai trò trong JWT phục vụ mô tả; filter vẫn đọc DB để áp dụng khóa/thu hồi quyền ngay. */
    public AuthResponse issue(UserAccount user) {
        Instant now = Instant.now(clock),
            expires = now.plus(settings.getAccessTokenLifetime());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(settings.getJwtIssuer())
            .subject(user.getId().toString())
            .audience(List.of(settings.getJwtAudience()))
            .issuedAt(now)
            .notBefore(now)
            .expiresAt(expires)
            .id(java.util.UUID.randomUUID().toString())
            .claim("username", user.getUsername())
            .claim("roles", user.getRoles().stream().map(Enum::name).sorted().toList())
            .claim("tokenVersion", user.getTokenVersion())
            .build();
        String token = encoder
            .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
            .getTokenValue();
        return new AuthResponse(token, "Bearer", expires, UserResponse.from(user));
    }

    public Jwt decode(String token) {
        if (token.length() > 8192) {
            throw new BadJwtException("Token exceeds the supported size");
        }
        Jwt decoded = decoder.decode(token);
        if (decoded.getExpiresAt() == null || decoded.getIssuedAt() == null || decoded.getId() == null) {
            throw new BadJwtException("Required token claims are missing");
        }
        return decoded;
    }
}
