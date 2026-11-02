package vn.vietroute.common.initializer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Một danh mục SQL hiện hành theo vendor/khối; history khóa nội dung bằng checksum byte gốc. */
public final class MigrationCatalog {

    private static final String ROOT = "/db/migration/";

    private static final Map<String, String> RESOURCES = load();

    private MigrationCatalog() {}

    /** Database mới chạy baseline hiện hành theo manifest phụ thuộc; số bước bắt đầu 001 riêng từng cụm. */
    public static List<String> versions() {
        return List.copyOf(RESOURCES.keySet());
    }

    /** Đường dẫn để người phát triển đối chiếu đúng nhóm; không nhận đường dẫn SQL tùy ý từ môi trường/HTTP. */
    public static String resource(String version) {
        String path = RESOURCES.get(version);
        if (path == null) throw new IllegalArgumentException("Unknown migration " + version);
        return ROOT + path;
    }

    /** Byte trả về được dùng trực tiếp tính checksum; không normalize newline/encoding hoặc sửa migration cũ. */
    public static byte[] read(String version) {
        try (InputStream input = MigrationCatalog.class.getResourceAsStream(resource(version))) {
            if (input == null) throw new IllegalStateException("Missing migration " + version);
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read migration " + version, exception);
        }
    }

    /** Fail closed nếu manifest thiếu/trùng/sai thứ tự hoặc trỏ ngoài vendor/nhóm đã duyệt. */
    private static Map<String, String> load() {
        String manifest = ROOT + "postgresql/migrations.list";
        Pattern resourcePattern = Pattern.compile(Regex.MIGRATION_RESOURCE);
        Map<String, Integer> next = new LinkedHashMap<>();
        LinkedHashMap<String, String> resources = new LinkedHashMap<>();
        try (InputStream input = MigrationCatalog.class.getResourceAsStream(manifest)) {
            if (input == null) throw new IllegalStateException("Missing migration manifest");
            for (String line : new String(input.readAllBytes(), StandardCharsets.UTF_8).lines().toList()) {
                String path = line.strip();
                if (path.isEmpty() || path.startsWith("#")) continue;
                Matcher match = resourcePattern.matcher(path);
                if (!match.matches()) throw new IllegalStateException("Invalid migration resource " + path);
                String group = match.group(1);
                String version = group + "/" + match.group(2);
                if (version.length() > 40 || Integer.parseInt(match.group(3)) != next.getOrDefault(group, 1)) {
                    throw new IllegalStateException("Invalid migration order " + version);
                }
                next.put(group, Integer.parseInt(match.group(3)) + 1);
                if (resources.putIfAbsent(version, path) != null) {
                    throw new IllegalStateException("Duplicate migration " + version);
                }
                try (InputStream script = MigrationCatalog.class.getResourceAsStream(ROOT + path)) {
                    if (script == null) throw new IllegalStateException("Missing migration " + version);
                }
            }
            if (resources.isEmpty()) throw new IllegalStateException("Empty migration manifest");
            return Collections.unmodifiableMap(resources);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read migration manifest", exception);
        }
    }
}
