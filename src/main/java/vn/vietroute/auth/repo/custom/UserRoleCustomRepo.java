package vn.vietroute.auth.repo.custom;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.repo.UserRoleRepo;

import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Set;

/**
 * Ghi dữ liệu theo lô trên connection của giao dịch nghiệp vụ.
 */
@Repository
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class UserRoleCustomRepo {

    private final UserRoleRepo mappings;

    public void replaceRoles(Connection connection, long id, Set<String> roles, long version, long actor)
        throws SQLException {
        mappings.prepareRoles(id, roles, version, actor);
        Array codes = connection.createArrayOf(
            "text",
            roles.toArray(String[]::new)
        );
        try {
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                        UPDATE auth_user_roles ur
                        SET version = version + 1, updated_at = CURRENT_TIMESTAMP, updated_by = ?
                        WHERE ur.user_id = ? AND ur.scope_id > 0
                            AND EXISTS (
                                SELECT 1
                                FROM auth_user_roles affected
                                WHERE affected.user_id = ur.user_id AND affected.scope_id = ur.scope_id
                                    AND NOT (affected.role_code = ANY(?))
                            )
                        """
                )
            ) {
                query.setLong(1, actor);
                query.setLong(2, id);
                query.setArray(3, codes);
                query.executeUpdate();
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    "DELETE FROM auth_user_roles WHERE user_id=? AND scope_id=0 AND NOT(role_code=ANY(?))"
                )
            ) {
                query.setLong(1, id);
                query.setArray(2, codes);
                query.executeUpdate();
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                        INSERT INTO auth_user_roles(user_id, role_code, created_by, updated_by)
                        SELECT ?, code, ?, ?
                        FROM UNNEST(?::TEXT []) code
                        ON CONFLICT DO NOTHING
                        """
                )
            ) {
                query.setLong(1, id);
                query.setLong(2, actor);
                query.setLong(3, actor);
                query.setArray(4, codes);
                query.executeUpdate();
            }
        } finally {
            codes.free();
        }
    }

    public void assign(Connection connection, long scope, long user, UserRequest body, long actor) throws SQLException {
        long version = mappings.prepareMembership(scope, user, body.getVersion(), actor);
        Array codes = connection.createArrayOf(
            "text",
            body
                .getRoles().toArray(String[]::new)
        );
        try {
            try (
                PreparedStatement query = connection.prepareStatement(
                    "DELETE FROM auth_user_roles WHERE user_id=? AND scope_id=?"
                )
            ) {
                query.setLong(1, user);
                query.setLong(2, scope);
                query.executeUpdate();
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                        INSERT INTO auth_user_roles(user_id, role_code, scope_id, active, version, created_by,
                            updated_by)
                        SELECT ?, code, ?, ?, ?, ?, ?
                        FROM UNNEST(?::TEXT []) code
                        """
                )
            ) {
                query.setLong(1, user);
                query.setLong(2, scope);
                query.setBoolean(3, Boolean.TRUE.equals(body.getActive()));
                query.setLong(4, version + 1);
                query.setLong(5, actor);
                query.setLong(6, actor);
                query.setArray(7, codes);
                query.executeUpdate();
            }
        } finally {
            codes.free();
        }
    }
}
