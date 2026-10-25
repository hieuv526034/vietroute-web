package vn.vietroute.auth.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Guard dữ liệu gọi trong DAO/transaction nghiệp vụ, bổ sung cho AccessOperation chứ không thay guard chức năng. */
@Component
public class DataScopeGuard {

    /** Thao tác khóa/mở nhà xe chỉ ADMIN hiện hành trên chính connection nghiệp vụ, không tin role snapshot cũ. */
    public static void requireAdmin(Connection connection, UserPrincipal actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT 1 FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL AND r.code='ADMIN'"
            )
        ) {
            query.setLong(1, actor.id());
            query.setLong(2, actor.tokenVersion());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Predicate cho phân trang SQL: owner là alias nhà xe từ DB, luôn kiểm lại account và scope/role hiện hành. */
    public static String visibleSql() {
        return """
        EXISTS (
            SELECT 1 FROM auth_users su JOIN auth_data_scopes ds ON ds.id=owner.scope_id AND ds.active
            WHERE su.id=? AND su.token_version=? AND su.active AND NOT su.activation_pending
              AND NOT su.must_change_password AND su.email_verified_at IS NOT NULL
              AND (
                EXISTS (SELECT 1 FROM auth_user_roles ar JOIN auth_roles rr ON rr.code=ar.role_code
                        WHERE ar.user_id=su.id AND rr.code='ADMIN' AND rr.active AND NOT rr.deleted)
                OR EXISTS (
                    SELECT 1 FROM auth_scope_memberships sm
                    JOIN auth_scope_roles sr ON sr.scope_id=sm.scope_id AND sr.user_id=sm.user_id
                    JOIN auth_user_roles ur ON ur.user_id=sr.user_id AND ur.role_code=sr.role_code
                    JOIN auth_roles r ON r.code=sr.role_code AND r.active AND NOT r.deleted
                    JOIN auth_role_permissions rp ON rp.role_code=r.code
                    JOIN auth_permissions p ON p.code=rp.permission_code AND p.active
                    WHERE sm.scope_id=ds.id AND sm.user_id=su.id AND sm.active AND p.code=?
                )
              )
        )
        """;
    }

    /** Bind từ principal server, không nhận actor/scope/permission từ HTTP; kết quả trả vị trí parameter tiếp theo. */
    public static int bindVisible(PreparedStatement query, int start, String resource, PermissionAction action)
        throws SQLException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            !(authentication.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id() == null ||
            actor.id() == SystemActor.ID
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        query.setLong(start++, actor.id());
        query.setLong(start++, actor.tokenVersion());
        query.setString(start++, Permission.valueOf(resource + "_" + action.name()).name());
        return start;
    }

    /**
     * Kiểm account/tokenVersion/scope/membership và đúng quyền của role trong scope bằng một snapshot DB.
     * Không union quyền OPERATOR ở A với DRIVER ở B; không tin authority hoặc scope cookie/header/client.
     * DAO vẫn phải có WHERE scope_id và kiểm quan hệ cha/con; kiểm này không tự viết lại SQL của caller.
     */
    public void require(Connection connection, long scopeId, String resource, PermissionAction action)
        throws SQLException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            !(authentication.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id() == null ||
            actor.id() == SystemActor.ID ||
            scopeId < 1
        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        String permission = Permission.valueOf(resource + "_" + action.name()).name();
        try (
            var query = connection.prepareStatement(
                """
                SELECT EXISTS (
                    SELECT 1 FROM auth_users u JOIN auth_data_scopes s ON s.id=? AND s.active
                    WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
                      AND (
                        EXISTS (SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code
                                WHERE ur.user_id=u.id AND r.code='ADMIN' AND r.active AND NOT r.deleted)
                        OR EXISTS (
                            SELECT 1 FROM auth_scope_memberships m
                            JOIN auth_scope_roles sr ON sr.scope_id=m.scope_id AND sr.user_id=m.user_id
                            JOIN auth_user_roles ur ON ur.user_id=sr.user_id AND ur.role_code=sr.role_code
                            JOIN auth_roles r ON r.code=sr.role_code AND r.active AND NOT r.deleted
                            JOIN auth_role_permissions rp ON rp.role_code=r.code
                            JOIN auth_permissions p ON p.code=rp.permission_code AND p.active
                            WHERE m.scope_id=s.id AND m.user_id=u.id AND m.active AND p.code=?
                        )
                      )
                )
                """
            )
        ) {
            query.setLong(1, scopeId);
            query.setLong(2, actor.id());
            query.setLong(3, actor.tokenVersion());
            query.setString(4, permission);
            try (var row = query.executeQuery()) {
                row.next();
                if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Chỉ dùng ID scope đã đọc từ DB của cha/con, không so hai trường do client cùng tự điền. */
    public static void requireSameScope(long parentScope, long childScope) {
        if (parentScope < 1 || parentScope != childScope) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
