package vn.vietroute.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Chỉ fixture cho regression nghiệp vụ trước AUTHDB03. Mã test cũ chọn một tập endpoint cố định trong JSON,
 * không dùng lại guard/action/enum production. Các test phân quyền mới luôn gán trực tiếp endpoint.
 */
public final class LegacyBusinessGrants {

    private static final JsonNode DEFINITIONS;

    static {
        try (var input = LegacyBusinessGrants.class.getResourceAsStream("/auth/legacy-business-endpoints.json")) {
            DEFINITIONS = new ObjectMapper().readTree(input);
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private LegacyBusinessGrants() {}

    /** Chỉ chuyển cú pháp fixture test cũ sang function dữ liệu, không dùng trong migration/runtime. */
    public static String sql(String text) {
        var removal = Pattern.compile(
            "UPDATE auth_role_functions SET operation_codes=array_remove\\(operation_codes,'([A-Z_]+)'\\) WHERE role_code='([A-Z_]+)'[^;]*"
        ).matcher(text);
        StringBuffer result = new StringBuffer();
        while (removal.find()) {
            String operation = removal.group(1),
                role = removal.group(2);
            String replacement =
                "DELETE FROM auth_role_functions WHERE function_code='" +
                operation +
                "' AND role_code='" +
                role +
                "'; DELETE FROM auth_function_resources WHERE function_code LIKE 'DEFAULT_%' AND function_code IN(SELECT function_code FROM auth_role_functions WHERE role_code='" +
                role +
                "') AND target IN(SELECT target FROM auth_function_resources WHERE function_code='" +
                operation +
                "')";
            removal.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        removal.appendTail(result);
        text = result.toString();
        text = text.replaceAll(
            "UPDATE auth_role_functions SET operation_codes=array_append\\(operation_codes,'([A-Z_]+)'\\) WHERE role_code='([A-Z_]+)'[^;]*",
            "INSERT INTO auth_role_functions(role_code,function_code) VALUES('$2','$1') ON CONFLICT DO NOTHING"
        );
        text = text.replace(",operation_codes", "").replaceAll(",ARRAY\\[[^\\]]*\\]", "");
        text = text.replace("permission_version", "version").replace("registry_version", "version");
        text = text.replace(
            "SELECT count(*) FROM (SELECT DISTINCT rf.role_code,op AS permission_code FROM auth_role_functions rf CROSS JOIN LATERAL unnest(rf.operation_codes) op)",
            "SELECT count(*) FROM (SELECT role_code,function_code AS permission_code FROM auth_role_functions) grants"
        );
        return text;
    }

    public static Set<String> targets(Set<String> codes) {
        Set<String> result = new LinkedHashSet<>();
        codes
            .stream()
            .filter(code -> code.contains(" /"))
            .forEach(result::add);
        for (var item : DEFINITIONS) {
            Set<String> required = new LinkedHashSet<>();
            item.path("codes").forEach(code -> required.add(code.asText()));
            boolean allowed = item.path("match").asText().equals("ALL")
                ? codes.containsAll(required)
                : required.stream().anyMatch(codes::contains);
            if (allowed) result.add(item.path("target").asText().replace("/registry/{kind}", "/registry/roles"));
        }
        return Set.copyOf(result);
    }

    /** Batch dữ liệu test thuần; mỗi function test có đúng tập endpoint cố định, không có operation_codes. */
    public static void initialize(Connection connection, Set<String> available) throws SQLException {
        Set<String> codes = new LinkedHashSet<>();
        DEFINITIONS.forEach(item -> item.path("codes").forEach(code -> codes.add(code.asText())));
        try (
            var insert = connection.prepareStatement(
                "INSERT INTO auth_functions(code,name_vi,name_en,resources_initialized) VALUES(?,?,?,true) ON CONFLICT DO NOTHING"
            )
        ) {
            for (String code : codes) {
                insert.setString(1, code);
                insert.setString(2, code);
                insert.setString(3, code);
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (
            var insert = connection.prepareStatement(
                "INSERT INTO auth_function_resources(function_code,kind,target,http_method,path_template,handler,description,name_vi,name_en) VALUES(?,?,?,?,?,'TestFixture',?,?,?) ON CONFLICT DO NOTHING"
            )
        ) {
            for (String code : codes)
                for (String target : targets(Set.of(code))) {
                    if (!available.contains(target)) continue;
                    var source = Arrays.stream(target.split(" ", 2)).toList();
                    String kind = "ENDPOINT";
                    for (var definition : DEFINITIONS)
                        if (definition.path("target").asText().equals(target)) kind = definition.path("kind").asText();
                    insert.setString(1, code);
                    insert.setString(2, kind);
                    insert.setString(3, target);
                    insert.setString(4, source.get(0));
                    insert.setString(5, source.get(1));
                    insert.setString(6, code);
                    insert.setString(7, code);
                    insert.setString(8, code);
                    insert.addBatch();
                }
            insert.executeBatch();
        }
    }
}
