package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Dùng file tạm, không mở .env của chủ dự án hoặc thử kết nối Neon. */
class LocalEnvironmentTest {

    @TempDir
    Path directory;

    @Test
    void explicitLocalFileUsesProcessOverridesAndNeverExecutesValues() throws Exception {
        Path file = directory.resolve("local.env");
        Files.writeString(
            file,
            "# comment\nVIETROUTE_ENV=production\nVIETROUTE_DB_USER='local'\nVIETROUTE_DB_PASSWORD=\"literal$()value\"\n"
        );
        var source = new ConfigurationEnvironment(Map.of(Env.DB_USER.key(), "process")::get, file.toString());
        assertEquals("process", source.apply(Env.DB_USER.key()));
        assertEquals("literal$()value", source.apply(Env.DB_PASSWORD.key()));
        assertEquals("development", source.apply(Env.ENV.key()));
        assertEquals("false", source.apply(Env.COOKIE_SECURE.key()));
        assertThrows(IllegalStateException.class, () ->
            new ConfigurationEnvironment(Map.of(Env.ENV.key(), "production")::get, file.toString())
        );
    }

    @Test
    void warAndTestsDoNotImplicitlyReadAnyFile() {
        var source = new ConfigurationEnvironment(key -> null, null);
        assertNull(source.apply(Env.DB_PASSWORD.key()));
    }

    @Test
    void invalidLinesFailWithoutLeakingValues() throws Exception {
        Path file = directory.resolve("invalid.env");
        Files.writeString(file, "password unsafe");
        assertThrows(IllegalStateException.class, () -> new ConfigurationEnvironment(key -> null, file.toString()));
    }
}
