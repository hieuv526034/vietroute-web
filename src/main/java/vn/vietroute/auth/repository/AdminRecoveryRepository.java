package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.record.history.AccessChangeStateResponse;
import vn.vietroute.auth.entity.Permission;

/** Khôi phục dữ liệu bootstrap trong transaction của caller; không có quyền bỏ qua DB tại runtime. */
@Repository
public class AdminRecoveryRepository {

    /** Cùng khóa với mọi thao tác gán quyền; nhiều node không tạo admin hoặc mapping trùng nhau. */
    public void lock(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            query.execute();
        }
    }

    /** Chỉ sửa role lõi và quyền khai báo bởi source; giữ trạng thái/mapping của các role thường. */
    public long initialize(
        Connection connection,
        String username,
        String email,
        Supplier<String> passwordHash,
        long actor
    ) throws SQLException {
        lock(connection);
        try (
            var role = connection.prepareStatement(
                """
                INSERT INTO auth_roles(code,name_vi,name_en,active,deleted,permissions_initialized,created_by,updated_by)
                VALUES ('ADMIN','Quản trị viên','Administrator',true,false,true,?,?)
                ON CONFLICT(code) DO UPDATE SET active=true,deleted=false,permissions_initialized=true,
                  updated_at=CURRENT_TIMESTAMP,updated_by=EXCLUDED.updated_by,registry_version=auth_roles.registry_version+1
                WHERE NOT auth_roles.active OR auth_roles.deleted OR NOT auth_roles.permissions_initialized
                """
            )
        ) {
            role.setLong(1, actor);
            role.setLong(2, actor);
            role.executeUpdate();
        }
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        try (
            var permission = connection.prepareStatement(
                """
                INSERT INTO auth_permissions(code,name_vi,name_en,created_by,updated_by) VALUES (?,?,?,?,?)
                ON CONFLICT(code) DO UPDATE SET active=true,updated_at=CURRENT_TIMESTAMP,
                  updated_by=EXCLUDED.updated_by,registry_version=auth_permissions.registry_version+1 WHERE NOT auth_permissions.active
                """
            )
        ) {
            for (var value : Permission.values()) {
                permission.setString(1, value.name());
                permission.setString(2, vi.getString(value.messageKey()));
                permission.setString(3, en.getString(value.messageKey()));
                permission.setLong(4, actor);
                permission.setLong(5, actor);
                permission.addBatch();
            }
            permission.executeBatch();
        }
        Long id = null;
        try (var query = connection.prepareStatement("SELECT id FROM auth_users WHERE username=? FOR UPDATE")) {
            query.setString(1, username);
            try (var row = query.executeQuery()) {
                if (row.next()) id = row.getLong(1);
            }
        }
        // Chỉ mã hóa khi tạo mới. Email do chủ hệ thống cấp sẵn, không suy luận đặc quyền từ role ADMIN.
        boolean created = id == null;
        if (created) try (
            var insert = connection.prepareStatement(
                """
                INSERT INTO auth_users(username,password_hash,display_name,created_by,updated_by,email,
                  email_verified_at,active,activation_pending,must_change_password)
                VALUES (?,?,'Quản trị viên',?,?,?,CURRENT_TIMESTAMP,true,false,false) RETURNING id
                """
            )
        ) {
            insert.setString(1, username);
            insert.setString(2, passwordHash.get());
            insert.setLong(3, actor);
            insert.setLong(4, actor);
            insert.setString(5, email);
            try (var row = insert.executeQuery()) {
                row.next();
                id = row.getLong(1);
            }
        }
        // Chỉ danh tính bootstrap được sửa về trạng thái sẵn sàng. Không xóa failed_attempts/locked_until,
        // không đổi hash/lịch sử mật khẩu, không cập nhật account khác dù account đó có role ADMIN.
        if (!created) try (
            var update = connection.prepareStatement(
                """
                UPDATE auth_users SET email=?,email_verified_at=CASE WHEN email IS DISTINCT FROM ?
                  OR email_verified_at IS NULL THEN CURRENT_TIMESTAMP ELSE email_verified_at END,
                  active=true,deleted=false,activation_pending=false,must_change_password=false,
                  token_version=token_version+1,version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=?
                WHERE id=? AND (email IS DISTINCT FROM ? OR email_verified_at IS NULL
                  OR NOT active OR deleted OR activation_pending OR must_change_password)
                """
            )
        ) {
            update.setString(1, email);
            update.setString(2, email);
            update.setLong(3, actor);
            update.setLong(4, id);
            update.setString(5, email);
            if (update.executeUpdate() > 0) try (
                var invalidate = connection.prepareStatement("DELETE FROM auth_account_challenges WHERE user_id=?")
            ) {
                // Mã reset/kích hoạt cũ không được tiếp tục dùng sau khi sửa email/trạng thái gốc.
                invalidate.setLong(1, id);
                invalidate.executeUpdate();
            }
        }
        try (
            var insert = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) VALUES (?,'ADMIN',?,?)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            insert.setLong(1, id);
            insert.setLong(2, actor);
            insert.setLong(3, actor);
            if (insert.executeUpdate() > 0 && !created) try (
                var update = connection.prepareStatement(
                    """
                    UPDATE auth_users SET token_version=token_version+1,version=version+1,
                      updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?
                    """
                )
            ) {
                update.setLong(1, actor);
                update.setLong(2, id);
                update.executeUpdate();
            }
        }
        grantAll(connection, actor);
        return id;
    }

    /** ADMIN nhận mọi permission active đã đăng ký, kể cả mã mới; không tự bật custom permission đã bị tắt. */
    public void grantAll(Connection connection, long actor) throws SQLException {
        RoleResourceRepository.mark(connection, "ADMIN", actor);
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO auth_role_resources(role_code,kind,target,created_by,updated_by)
                SELECT 'ADMIN',kind,target,?,? FROM auth_access_resources
                WHERE NOT public_route AND declared_permissions<>'' ON CONFLICT DO NOTHING
                """
            )
        ) {
            query.setLong(1, actor);
            query.setLong(2, actor);
            query.executeUpdate();
        }
        try (
            var insert = connection.prepareStatement(
                """
                INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by)
                SELECT 'ADMIN',code,?,? FROM auth_permissions WHERE active ON CONFLICT DO NOTHING
                """
            )
        ) {
            insert.setLong(1, actor);
            insert.setLong(2, actor);
            if (insert.executeUpdate() > 0) try (
                var update = connection.prepareStatement(
                    "UPDATE auth_roles SET permission_version=permission_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code='ADMIN'"
                )
            ) {
                update.setLong(1, actor);
                update.executeUpdate();
            }
        }
    }

    /** Snapshot whitelist thực tế để ghi lịch sử, tuyệt đối không chứa hash/password/email. */
    public AccessChangeStateResponse state(Connection connection, long id) throws SQLException {
        Set<String> roles = new LinkedHashSet<>();
        Set<String> permissions = new LinkedHashSet<>();
        Set<String> resources = new LinkedHashSet<>();
        try (
            var query = connection.prepareStatement(
                "SELECT target FROM auth_role_resources WHERE role_code='ADMIN' ORDER BY target"
            );
            var rows = query.executeQuery()
        ) {
            while (rows.next()) resources.add(rows.getString(1));
        }
        try (
            var query = connection.prepareStatement(
                "SELECT role_code FROM auth_user_roles WHERE user_id=? ORDER BY role_code"
            )
        ) {
            query.setLong(1, id);
            try (var rows = query.executeQuery()) {
                while (rows.next()) roles.add(rows.getString(1));
            }
        }
        try (
            var query = connection.prepareStatement(
                "SELECT permission_code FROM auth_role_permissions WHERE role_code='ADMIN' ORDER BY permission_code"
            );
            var rows = query.executeQuery()
        ) {
            while (rows.next()) permissions.add(rows.getString(1));
        }
        boolean active = false;
        boolean deleted = false;
        long version = 0;
        try (
            var query = connection.prepareStatement(
                "SELECT active,deleted,permission_version FROM auth_roles WHERE code='ADMIN'"
            );
            var row = query.executeQuery()
        ) {
            if (row.next()) {
                active = row.getBoolean(1);
                deleted = row.getBoolean(2);
                version = row.getLong(3);
            }
        }
        return new AccessChangeStateResponse(
            roles,
            permissions,
            roles.size() + permissions.size() + resources.size(),
            false,
            active,
            deleted,
            null,
            null,
            null,
            null,
            null,
            version,
            resources
        );
    }
}
