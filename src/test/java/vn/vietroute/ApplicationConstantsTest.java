package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Kiểm hợp đồng cấu hình với mẫu triển khai và các ranh giới public/protected sau quy hoạch package. */
class ApplicationConstantsTest {

    @Test
    void environmentRegistryIsUniqueAndMatchesExampleWithoutReadingLocalSecrets() throws Exception {
        Set<String> keys = Arrays.stream(Env.values()).map(Env::key).collect(Collectors.toSet());
        assertEquals(Env.values().length, keys.size());
        Set<String> example = Files.readAllLines(Path.of(".env.example"))
            .stream()
            .filter(line -> line.startsWith("VIETROUTE_"))
            .map(line -> line.substring(0, line.indexOf('=')))
            .collect(Collectors.toSet());
        // LOCAL_ENV_FILE chọn file trước khi đọc .env, chỉ cấu hình ở OS/IDE; không bắt file tự trỏ đến nó.
        Set<String> fileKeys = keys
            .stream()
            .filter(key -> !key.equals(Env.LOCAL_ENV_FILE.key()))
            .collect(Collectors.toSet());
        assertEquals(fileKeys, example, "Key ứng dụng cần có trong registry và file mẫu; launcher chọn file từ OS/IDE");
        // Không đọc .env thật. Run configuration và Blueprint chỉ tham chiếu key đã đăng ký.
        for (Path file : new Path[] { Path.of("render.yaml"), Path.of(".run/VietRoute Local.run.xml") }) {
            var matcher = Pattern.compile("VIETROUTE_[A-Z_]+").matcher(Files.readString(file));
            while (matcher.find()) {
                assertTrue(keys.contains(matcher.group()), matcher.group());
            }
        }
    }

    @Test
    void typedConfigPreservesDefaultsAndNeverEchoesRejectedSecretValues() {
        var values = new ConfigValues(
            Map.of(
                "VIETROUTE_DB_POOL_SIZE",
                "private-value-never-echo",
                "VIETROUTE_API_JOURNAL_ENABLED",
                "invalid-private-value"
            )::get
        );
        assertEquals("default", values.text(Env.HTTP_PORT, "default"));
        var numberError = assertThrows(IllegalStateException.class, () -> values.number(Env.DB_POOL_SIZE, 2, 1, 8));
        assertTrue(numberError.getMessage().contains(Env.DB_POOL_SIZE.key()));
        assertFalse(numberError.getMessage().contains("private-value"));
        var flagError = assertThrows(IllegalStateException.class, () -> values.flag(Env.API_JOURNAL_ENABLED, false));
        assertFalse(flagError.getMessage().contains("invalid-private-value"));
    }

    @Test
    void catalogPublicPolicyNeverMakesWritesOrAdminRoutesAnonymous() {
        assertTrue(WebRoutePolicy.publicApi("/api/v1/catalogs/VEHICLE_TYPE", "GET"));
        assertFalse(WebRoutePolicy.publicApi("/api/v1/catalogs/VEHICLE_TYPE", "POST"));
        assertFalse(WebRoutePolicy.publicApi("/api/v1/admin/catalogs", "GET"));
        assertFalse(WebRoutePolicy.publicApi("/api/v1/media", "GET"));
        assertFalse(WebRoutePolicy.skipsJwt("/api/v1/admin/jobs"));
        assertFalse(WebRoutePolicy.skipsTrafficGuard("/api/v1/media"));
        assertFalse(WebRoutePolicy.recoversInvalidCookie("/api/v1/media", "GET"));
        assertTrue(WebRoutePolicy.recoversInvalidCookie("/login", "GET"));
        assertTrue(WebRoutePolicy.publicMatchers().contains(Routes.LOGIN));
        assertFalse(WebRoutePolicy.publicMatchers().contains("/api/v1/media"));
    }
}
