package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.UserAccount;
import vn.vietroute.auth.service.JwtService;
import vn.vietroute.common.config.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.constant.ValidationCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.util.CodeGenerator;
import vn.vietroute.common.validation.StrongPasswordValidator;

/** Các ràng buộc có ý nghĩa với bảo mật và hợp đồng dịch, không dùng Neon production. */
class FoundationRulesTest {

    @Test
    void bothLocalesCoverEveryPublicKey() {
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        assertEquals(vi.keySet(), en.keySet());
        for (ErrorCode code : ErrorCode.values()) {
            assertTrue(vi.containsKey(code.getMessageKey()));
        }
        for (SuccessCode code : SuccessCode.values()) {
            assertTrue(vi.containsKey(code.getMessageKey()));
        }
        for (ValidationCode code : ValidationCode.values()) {
            assertTrue(vi.containsKey(code.getMessageKey()));
        }
        for (Permission permission : Permission.values()) {
            assertTrue(vi.containsKey(permission.messageKey()));
        }
        for (PermissionAction action : PermissionAction.values()) {
            assertTrue(vi.containsKey(action.messageKey()));
        }
    }

    @Test
    void legacyWriteCannotImplicitlyGrantDeleteReadOrAssign() {
        assertEquals(Set.of(PermissionAction.CREATE, PermissionAction.UPDATE), Permission.CATALOG_WRITE.actions());
        assertEquals(Set.of(PermissionAction.DELETE), Permission.CATALOG_DELETE.actions());
        assertEquals("CATALOG", Permission.CATALOG_DELETE.resource());
        assertEquals(Set.of(PermissionAction.ASSIGN), Permission.ACCESS_ASSIGN.actions());
    }

    @Test
    void codeAndPasswordRulesPreserveVietnameseTextAndBcryptByteLimit() {
        for (int i = 0; i < 50; i++) {
            assertTrue(CodeGenerator.generate("CAT").matches(Regex.BUSINESS_CODE));
        }
        assertThrows(BusinessException.class, () -> CodeGenerator.generate("bad!"));
        assertTrue(StrongPasswordValidator.valid("Valid-Password-2026"));
        assertFalse(StrongPasswordValidator.valid("weak"));
        assertFalse(StrongPasswordValidator.valid("Ab1!" + "ệ".repeat(30)));
    }

    @Test
    void jwtRejectsWrongKeyAndExpiredTokens() {
        var first = new AppSettings(Map.of("VIETROUTE_JWT_SECRET", "A".repeat(48))::get);
        var second = new AppSettings(Map.of("VIETROUTE_JWT_SECRET", "B".repeat(48))::get);
        var current = new JwtService(first, Clock.systemUTC());
        var user = UserAccount.builder()
            .id(1L)
            .username("admin")
            .displayName("Admin")
            .roles(Set.of(Role.ADMIN))
            .active(true)
            .tokenVersion(2)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
        String token = current.issue(user).accessToken();
        assertEquals("1", current.decode(token).getSubject());
        assertThrows(JwtException.class, () -> new JwtService(second, Clock.systemUTC()).decode(token));
        String expired = new JwtService(first, Clock.fixed(Instant.now().minus(Duration.ofDays(1)), ZoneOffset.UTC))
            .issue(user)
            .accessToken();
        assertThrows(JwtException.class, () -> current.decode(expired));
    }

    @Test
    void productionCannotStartWithMissingSecret() {
        assertThrows(IllegalStateException.class, () -> new AppSettings(Map.of("VIETROUTE_ENV", "production")::get));
    }
}
