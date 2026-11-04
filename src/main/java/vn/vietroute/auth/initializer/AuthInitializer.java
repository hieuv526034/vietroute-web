package vn.vietroute.auth.initializer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;
import lombok.Getter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.AccountChallengeRepo;
import vn.vietroute.auth.repo.RoleRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.repo.custom.FunctionResourceCustomRepo;
import vn.vietroute.auth.repo.custom.RoleCustomRepo;
import vn.vietroute.auth.repo.custom.UserCustomRepo;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.application.ConfigValues;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Khởi tạo opt-in theo JSON/env; giữ hash/khóa cũ và không tự cấp lại mapping ordinary role. */
@Component
public class AuthInitializer {

    private final UserRepo users;
    private final UserCustomRepo userBatch;
    private final RoleCustomRepo roleBatch;
    private final RoleRepo roles;
    private final AccountChallengeRepo challenges;
    private final FunctionResourceCustomRepo resources;
    private final AppSettings settings;
    private final ConfigurationEnvironment environment;
    private final PasswordEncoder passwords;
    private final JdbcTransactions transactions;

    @Getter
    private final JsonNode data;

    public AuthInitializer(
        UserRepo users,
        RoleRepo roles,
        AppSettings settings,
        ConfigurationEnvironment environment,
        PasswordEncoder passwords,
        JdbcTransactions transactions,
        ObjectMapper mapper,
        UserCustomRepo userBatch,
        RoleCustomRepo roleBatch,
        AccountChallengeRepo challenges,
        FunctionResourceCustomRepo resources
    ) {
        this.users = users;
        this.challenges = challenges;
        this.resources = resources;
        this.userBatch = userBatch;
        this.roleBatch = roleBatch;
        this.roles = roles;
        this.settings = settings;
        this.environment = environment;
        this.passwords = passwords;
        this.transactions = transactions;
        try (InputStream input = getClass().getResourceAsStream("/auth/default-access.json")) {
            if (input == null) throw new IllegalStateException("Thiếu dữ liệu init auth");
            data = mapper.readTree(input);
        } catch (IOException error) {
            throw new IllegalStateException("Dữ liệu init auth không hợp lệ", error);
        }
    }

    /** Caller giữ transaction migration; mật khẩu chỉ encode khi account thật sự chưa tồn tại. */
    public void initialize(Connection connection) throws SQLException {
        roleBatch.initialize(connection, data);
        initializeBootstrap(
            settings.getBootstrapUsername().toLowerCase(Locale.ROOT),
            settings.getBootstrapEmail(),
            () -> passwords.encode(settings.getBootstrapPassword()),
            0,
            data.path("bootstrapRole").asText()
        );
        ConfigValues values = new ConfigValues(environment);
        if (!values.flag(Env.DEFAULT_ACCOUNTS_ENABLED, false)) return;
        Set<String> names = new LinkedHashSet<>();
        data.path("accounts").forEach(item -> names.add(item.path("username").asText()));
        Set<String> existing = users.existingUsernames(names);
        List<User> missing = new ArrayList<>();
        for (JsonNode item : data.path("accounts")) {
            String username = item.path("username").asText();
            if (existing.contains(username)) continue;
            String email = item.hasNonNull("emailEnv")
                ? environment.apply(item.path("emailEnv").asText())
                : item.path("email").asText();
            String password = environment.apply(item.path("passwordEnv").asText());
            if (
                !BusinessRules.validEmail(email) ||
                !BusinessRules.validInitialPassword(password) ||
                email.startsWith("replace_")
            ) throw new IllegalStateException("Cần cấu hình email/mật khẩu tài khoản mặc định trên máy");
            User account = new User();
            account.setUsername(username);
            account.setDisplayName(item.path("displayName").asText());
            account.setEmail(email.toLowerCase(Locale.ROOT));
            account.setPasswordHash(passwords.encode(password));
            account.setRoles(Set.of(item.path("role").asText()));
            missing.add(account);
        }
        userBatch.insertDefaults(connection, missing);
    }

    /** Danh bạ sẵn sàng sau servlet refresh; chỉ chạy khi ACCESS_SYNC_ENABLED được bật. */
    public void synchronize(List<FunctionDTO> endpoints) {
        if (!settings.isAccessSyncEnabled()) return;
        transactions.execute(connection -> {
            users.lockBootstrap();
            roleBatch.synchronizeEndpoints(connection, data, endpoints);
            String role = data.path("bootstrapRole").asText();
            String function = data.path("bootstrapFunction").asText("ADMIN_CONSOLE");
            roles.repairBootstrapRole(role, 0);
            resources.replace(
                connection,
                function,
                endpoints
                    .stream()
                    .filter(item -> !item.isPublicEndpoint() && !item.isSelfService())
                    .toList(),
                0
            );
            roles.repairBootstrapFunction(function, 0);
            roles.assignBootstrapFunction(role, function, 0);
            return null;
        });
    }

    /** Tạo khi thiếu, giữ hash cũ; sửa trạng thái bootstrap chỉ khi cờ init được bật. */
    private long initializeBootstrap(String username, String email, Supplier<String> hash, long actor, String role) {
        users.lockBootstrap();
        Long id = users.bootstrapId(username);
        boolean created = id == null;
        if (created) {
            id = users.insertBootstrap(username, email, hash.get(), actor);
        } else if (users.repairBootstrap(id, email, actor) > 0) {
            challenges.invalidate(id);
        }
        if (users.assignBootstrap(id, role, actor) > 0 && !created) users.revokeBootstrapTokens(id, actor);
        return id;
    }

    public String driverRole() {
        return data.path("driverRole").asText();
    }
}
