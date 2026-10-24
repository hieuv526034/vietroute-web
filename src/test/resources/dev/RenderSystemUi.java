import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarFile;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessResourceSynchronizer;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.config.web.WebConfig;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.support.AccessTestConfig;
import vn.vietroute.support.JournalTestConfig;

/** Xuất HTML thật bằng hai context WAR + DAO fixture; chỉ ghi target, không đọc .env hoặc dùng provider/DB thật. */
public final class RenderSystemUi {

    private RenderSystemUi() {}

    public static void main(String[] args) throws Exception {
        try (
            var root = new AnnotationConfigWebApplicationContext();
            var child = new AnnotationConfigWebApplicationContext()
        ) {
            var web = new MockServletContext("src/main/webapp", new FileSystemResourceLoader());
            root.setServletContext(web);
            root.register(RootConfig.class, AccessTestConfig.class, JournalTestConfig.class, FixtureEnvironment.class);
            root.refresh();
            child.setParent(root);
            child.setServletContext(web);
            child.register(WebConfig.class);
            child.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(child).apply(springSecurity()).build();
            Set<String> codes = new LinkedHashSet<>();
            Arrays.stream(Permission.values()).map(Permission::name).forEach(codes::add);
            child
                .getBean(AccessResourceSynchronizer.class)
                .inventory()
                .forEach(item -> codes.addAll(item.declaredPermissions()));
            codes.add("ROLE_ADMIN");
            var actor = UsernamePasswordAuthenticationToken.authenticated(
                new UserPrincipal(1L, "previewadmin", Set.of(Role.ADMIN), 0, "Quản trị viên thử nghiệm", null),
                null,
                codes.stream().map(SimpleGrantedAuthority::new).toList()
            );
            var output = Path.of("target/ui-preview");
            Files.createDirectories(output);
            String[][] pages = {
                { "locations", "/operations/locations" },
                { "accounts", "/operations/accounts" },
                { "access", "/operations/access" },
                { "admin", "/admin" },
                { "configuration", "/operations/configuration" },
                { "media", "/media" },
                { "monitoring", "/monitoring" },
                { "jobs", "/operations/jobs" },
                { "api-journal", "/operations/api-journal" },
                { "home", "/" },
                { "login", "/login" },
                { "account", "/account" },
                { "docs", "/docs" },
                { "lifecycle", "/account-access" },
                { "guest", "/" },
            };
            for (var page : pages)
                for (var language : new String[] { "vi", "en" }) {
                    var request = get(page[1]).param("lang", language);
                    if (!Set.of("login", "lifecycle", "guest").contains(page[0])) request.with(authentication(actor));
                    var response = mvc.perform(request).andReturn().getResponse();
                    if (response.getStatus() != 200) throw new IllegalStateException(
                        "Không render được fixture: " + page[0]
                    );
                    String html = response.getContentAsString(StandardCharsets.UTF_8);
                    if (html.contains("??ui.")) throw new IllegalStateException("Thiếu locale fixture: " + page[0]);
                    Files.writeString(output.resolve(page[0] + (language.equals("vi") ? "" : "-en") + ".html"), html);
                }
            for (var language : new String[] { "vi", "en" }) {
                var response = mvc
                    .perform(get("/api/v1/admin/access/tree").param("lang", language).with(authentication(actor)))
                    .andReturn()
                    .getResponse();
                if (response.getStatus() != 200) throw new IllegalStateException("Không xuất được cây fixture");
                Files.writeString(output.resolve("tree-" + language + ".json"), response.getContentAsString());
            }
            for (var item : new String[][] {
                { "configuration.json", "/api/v1/admin/system/configuration" },
                { "capabilities.json", "/api/v1/auth/capabilities" },
                { "openapi.json", "/v3/api-docs" },
            }) {
                var response = mvc
                    .perform(get(item[1]).with(authentication(actor)))
                    .andReturn()
                    .getResponse();
                if (response.getStatus() != 200) throw new IllegalStateException("Không xuất được metadata fixture");
                Files.writeString(output.resolve(item[0]), response.getContentAsString());
            }
        }
        System.out.println("Đã xuất HTML/metadata fixture cách ly vào target/ui-preview.");
        exportWebjars();
    }

    /** Xuất asset công khai từ dependency đã có; chỉ ghi trong target, không tải vendor hoặc đọc env. */
    private static void exportWebjars() throws Exception {
        var output = Path.of("target/ui-preview/webjars").toAbsolutePath().normalize();
        String prefix = "META-INF/resources/webjars/";
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            if (!entry.endsWith(".jar")) continue;
            try (var jar = new JarFile(entry)) {
                var resources = jar.entries();
                while (resources.hasMoreElements()) {
                    var resource = resources.nextElement();
                    if (resource.isDirectory() || !resource.getName().startsWith(prefix)) continue;
                    var target = output.resolve(resource.getName().substring(prefix.length())).normalize();
                    if (!target.startsWith(output)) throw new IllegalStateException(
                        "Webjar fixture vượt thư mục target"
                    );
                    Files.createDirectories(target.getParent());
                    try (var input = jar.getInputStream(resource)) {
                        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    /** Ghi đè bean root test: không dùng env OS, system property hoặc file local. */
    @Configuration
    static class FixtureEnvironment {

        @Bean
        ConfigurationEnvironment configurationEnvironment() {
            var values = new HashMap<String, String>();
            values.put(Env.ENV.key(), "development");
            values.put(Env.DB_URL.key(), "jdbc:postgresql://127.0.0.1:1/fixture_only");
            values.put(Env.DB_USER.key(), "fixture_only");
            values.put(Env.DB_PASSWORD.key(), "fixture_only");
            for (var key : Set.of(
                Env.DB_INIT_ENABLED,
                Env.ACCESS_SYNC_ENABLED,
                Env.WORKER_ENABLED,
                Env.MAIL_ENABLED,
                Env.MEDIA_ENABLED,
                Env.MONITORING_DB_ENABLED,
                Env.ACTIVITY_ENABLED,
                Env.API_JOURNAL_ENABLED,
                Env.AUTH_SELF_SERVICE_ENABLED
            ))
                values.put(key.key(), "false");
            return new ConfigurationEnvironment(values::get, null);
        }
    }
}
