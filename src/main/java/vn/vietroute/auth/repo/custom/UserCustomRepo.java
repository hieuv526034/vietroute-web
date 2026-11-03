package vn.vietroute.auth.repo.custom;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.entity.User;

/** SQL ghi batch theo tập; caller sở hữu transaction/connection và quyền nghiệp vụ. */
@Repository
public class UserCustomRepo {

    public void insertDefaults(Connection connection, List<User> accounts) throws SQLException {
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_users(username, display_name, email, password_hash, email_verified_at, active,
                    activation_pending, must_change_password, bootstrap_scope_pending)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, TRUE, FALSE, FALSE, TRUE)
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (User account : accounts) {
                insert.setString(1, account.getUsername());
                insert.setString(2, account.getDisplayName());
                insert.setString(3, account.getEmail());
                insert.setString(4, account.getPasswordHash());
                insert.addBatch();
            }
            insert.executeBatch();
        }
        try (
            PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id, role_code)
                SELECT id, ?
                FROM auth_users
                WHERE username = ? AND bootstrap_scope_pending
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            for (User account : accounts) {
                insert.setString(1, account.getRoles().iterator().next());
                insert.setString(2, account.getUsername());
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    /** Chỉ account vừa init mới nhận scope mặc định một lần; không tự khôi phục mapping đã bị quản trị thu hồi. */
    public void assignInitialScopes(Connection connection, JsonNode accounts) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id, role_code, scope_id)
                SELECT u.id, ur.role_code, c.scope_id
                FROM JSONB_TO_RECORDSET(?::JSONB) x(username TEXT, role TEXT, "carrierCode" TEXT, "driverCode" TEXT)
                JOIN auth_users u ON u.username = x.username
                JOIN auth_user_roles ur ON ur.user_id = u.id AND ur.scope_id = 0
                    AND ur.role_code = x.role
                JOIN carriers c ON c.code = x."carrierCode"
                WHERE u.bootstrap_scope_pending AND (x."driverCode" IS NULL OR EXISTS (
                    SELECT 1 FROM carrier_drivers d WHERE d.carrier_id=c.id AND d.code=x."driverCode"
                        AND (d.user_id IS NULL OR d.user_id=u.id)
                ))
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            query.setString(1, accounts.toString());
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE carrier_drivers d
                SET user_id = u.id, updated_at = CURRENT_TIMESTAMP, updated_by = 0, version = d.version + 1
                FROM JSONB_TO_RECORDSET(?::JSONB) x(username TEXT, "carrierCode" TEXT, "driverCode" TEXT)
                JOIN auth_users u ON u.username = x.username
                JOIN carriers c ON c.code = x."carrierCode"
                WHERE u.bootstrap_scope_pending
                    AND d.carrier_id = c.id
                    AND d.code = x."driverCode"
                    AND d.user_id IS NULL
                """
            )
        ) {
            query.setString(1, accounts.toString());
            query.executeUpdate();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                """
                UPDATE auth_users
                SET bootstrap_scope_pending = FALSE
                WHERE bootstrap_scope_pending
                    AND (username IN (
                        SELECT x.username FROM JSONB_TO_RECORDSET(?::JSONB)
                            x(username TEXT, "carrierCode" TEXT)
                        WHERE x."carrierCode" IS NULL
                    ) OR EXISTS (
                        SELECT 1
                        FROM auth_user_roles ur
                        WHERE ur.user_id = auth_users.id AND ur.scope_id > 0
                    ))
                """
            )
        ) {
            query.setString(1, accounts.toString());
            query.executeUpdate();
        }
    }
}
