package vn.vietroute.common.config.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Nguồn cấu hình thống nhất; chỉ launcher local chủ động bật đọc file, WAR không tự tìm .env. */
public final class ConfigurationEnvironment implements Function<String, String> {

    private final Function<String, String> process;

    private final Map<String, String> local;

    public ConfigurationEnvironment() {
        this(System::getenv, System.getProperty(Env.LOCAL_ENV_FILE.key()));
    }

    /** Biến IDE/OS luôn ưu tiên hơn file; parser không nội suy hoặc thực thi nội dung secret. */
    public ConfigurationEnvironment(Function<String, String> process, String file) {
        this.process = process;
        Map<String, String> values = new HashMap<>();
        if (file != null) {
            if ("production".equalsIgnoreCase(process.apply(Env.ENV.key()))) {
                throw new IllegalStateException("Production phải nhận cấu hình qua biến môi trường.");
            }
            Path path = Path.of(file).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                throw new IllegalStateException(
                    "Tạo file .env riêng hoặc khai báo đường dẫn local hợp lệ; không ghi secret vào Git."
                );
            }
            if (Files.exists(path)) {
                try {
                    for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                        String entry = line.strip();
                        if (entry.isEmpty() || entry.startsWith("#")) {
                            continue;
                        }
                        int equals = entry.indexOf('=');
                        if (equals < 1 || !entry.substring(0, equals).matches(Regex.ENV_KEY)) {
                            throw new IllegalStateException("File cấu hình local có dòng không đúng KEY=value.");
                        }
                        String value = entry.substring(equals + 1).strip();
                        if (
                            value.length() >= 2 &&
                            ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'")))
                        ) {
                            value = value.substring(1, value.length() - 1);
                        }
                        values.put(entry.substring(0, equals), value);
                    }
                } catch (IOException exception) {
                    throw new IllegalStateException("Không đọc được file cấu hình local.");
                }
            }
            // Dùng chung DB không đồng nghĩa chạy local dưới chế độ production/HTTPS cookie.
            values.put(Env.ENV.key(), "development");
            values.put(Env.COOKIE_SECURE.key(), "false");
        }
        local = Map.copyOf(values);
    }

    @Override
    public String apply(String key) {
        String value = process.apply(key);
        return value == null ? local.get(key) : value;
    }
}
