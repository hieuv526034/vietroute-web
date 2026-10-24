import java.sql.DriverManager;
import java.util.Map;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import vn.vietroute.auth.repository.AdminRecoveryRepository;
import vn.vietroute.auth.service.BootstrapAdminInitializer;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Chỉ chạy trong container CI cố định; mọi dữ liệu phục hồi thử nghiệm đều rollback. */
public class AdminRecoveryInspector {

    public static void main(String[] args) throws Exception {
        String url = System.getenv("CI_GUARD_DB_URL");
        if (!"jdbc:postgresql://vietroute-db-ci:5432/vietroute_ci".equals(url)) throw new AssertionError("Chỉ DB CI");
        var passwords = new BCryptPasswordEncoder(4);
        var config = Map.of(
            Env.BOOTSTRAP_ADMIN_USERNAME.key(),
            "recoveryseedci",
            Env.BOOTSTRAP_ADMIN_EMAIL.key(),
            "bootstrap-recovery@example.invalid",
            Env.BOOTSTRAP_ADMIN_PASSWORD.key(),
            "Fixture-Seed-Only-2026!"
        );
        var initializer = new BootstrapAdminInitializer(
            new AppSettings(config::get),
            passwords,
            new AdminRecoveryRepository()
        );
        try (
            var connection = DriverManager.getConnection(
                url,
                System.getenv("CI_GUARD_DB_USER"),
                System.getenv("CI_GUARD_DB_PASSWORD")
            )
        ) {
            connection.setAutoCommit(false);
            try {
                try (var path = connection.createStatement()) {
                    path.execute("SET LOCAL search_path TO vietroute,pg_temp");
                }
                long id = initializer.initialize(connection, 0);
                String changed = passwords.encode("Fixture-Changed-Only-2026!");
                try (
                    var query = connection.prepareStatement(
                        "SELECT password_hash,must_change_password FROM auth_users WHERE id=?"
                    )
                ) {
                    query.setLong(1, id);
                    try (var row = query.executeQuery()) {
                        row.next();
                        if (
                            !passwords.matches("Fixture-Seed-Only-2026!", row.getString(1)) || row.getBoolean(2)
                        ) throw new AssertionError("Tạo mới sai");
                    }
                }
                try (
                    var update = connection.prepareStatement(
                        "UPDATE auth_users SET password_hash=?,must_change_password=true,active=false,activation_pending=true,email=NULL,email_verified_at=NULL,failed_login_attempts=3,locked_until=CURRENT_TIMESTAMP+INTERVAL '15 minutes' WHERE id=?"
                    )
                ) {
                    update.setString(1, changed);
                    update.setLong(2, id);
                    update.executeUpdate();
                }
                try (var query = connection.prepareStatement("DELETE FROM auth_user_roles WHERE user_id=?")) {
                    query.setLong(1, id);
                    query.executeUpdate();
                }
                if (
                    initializer.initialize(connection, 0) != id || initializer.initialize(connection, 0) != id
                ) throw new AssertionError("Không idempotent");
                try (
                    var query = connection.prepareStatement(
                        "SELECT password_hash,must_change_password,(SELECT count(*) FROM auth_user_roles WHERE user_id=u.id AND role_code='ADMIN'),email,email_verified_at IS NOT NULL,active,activation_pending,failed_login_attempts,locked_until IS NOT NULL,token_version,version FROM auth_users u WHERE id=?"
                    )
                ) {
                    query.setLong(1, id);
                    try (var row = query.executeQuery()) {
                        row.next();
                        if (
                            !changed.equals(row.getString(1)) ||
                            row.getBoolean(2) ||
                            row.getInt(3) != 1 ||
                            !"bootstrap-recovery@example.invalid".equals(row.getString(4)) ||
                            !row.getBoolean(5) ||
                            !row.getBoolean(6) ||
                            row.getBoolean(7) ||
                            row.getInt(8) != 3 ||
                            !row.getBoolean(9)
                        ) throw new AssertionError("Khôi phục ghi đè mật khẩu/trùng mapping");
                    }
                }
                // Role ADMIN không biến user thường thành bootstrap: trạng thái xác minh không được sửa lây.
                long other;
                try (
                    var insert = connection.prepareStatement(
                        "INSERT INTO auth_users(username,display_name,password_hash,must_change_password) VALUES ('hieuboyci','Ordinary admin',?,true) RETURNING id"
                    )
                ) {
                    insert.setString(1, changed);
                    try (var row = insert.executeQuery()) {
                        row.next();
                        other = row.getLong(1);
                    }
                }
                try (
                    var role = connection.prepareStatement(
                        "INSERT INTO auth_user_roles(user_id,role_code) VALUES (?,'ADMIN')"
                    )
                ) {
                    role.setLong(1, other);
                    role.executeUpdate();
                }
                long version;
                try (
                    var row = connection
                        .createStatement()
                        .executeQuery("SELECT token_version FROM auth_users WHERE id=" + id)
                ) {
                    row.next();
                    version = row.getLong(1);
                }
                initializer.initialize(connection, 0);
                try (
                    var query = connection.prepareStatement(
                        "SELECT token_version=? AND email_verified_at IS NOT NULL AND NOT must_change_password FROM auth_users WHERE id=?"
                    )
                ) {
                    query.setLong(1, version);
                    query.setLong(2, id);
                    try (var row = query.executeQuery()) {
                        row.next();
                        if (!row.getBoolean(1)) throw new AssertionError("Init lặp phải giữ version/trạng thái");
                    }
                }
                try (
                    var query = connection.prepareStatement(
                        "SELECT must_change_password AND email IS NULL AND email_verified_at IS NULL FROM auth_users WHERE id=?"
                    )
                ) {
                    query.setLong(1, other);
                    try (var row = query.executeQuery()) {
                        row.next();
                        if (!row.getBoolean(1)) throw new AssertionError("Không được miễn xác thực cho ADMIN thường");
                    }
                }
            } finally {
                connection.rollback();
            }
        }
        System.out.println(
            "Configured bootstrap creation, missing mapping and repeat initialization passed without resetting password"
        );
    }
}
