package vn.vietroute.auth.security;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.repo.ScopeAccessSql;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Guard dữ liệu gọi trong DAO/transaction nghiệp vụ, bổ sung cho AccessOperation chứ không thay guard chức năng. */
@Component
public class DataScopeGuard {

    /** Thao tác khóa/mở nhà xe chỉ ADMIN hiện hành trên chính connection nghiệp vụ, không tin role snapshot cũ. */
    public static void requireAdmin(Connection connection, UserPrincipal actor) throws SQLException {
        try (var query = connection.prepareStatement(ScopeAccessSql.ADMIN)) {
            query.setLong(1, actor.id());
            query.setLong(2, actor.tokenVersion());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Predicate cho phân trang SQL: owner là alias nhà xe từ DB, luôn kiểm lại account và scope/role hiện hành. */
    public static String visibleSql() {
        return ScopeAccessSql.VISIBLE;
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
        String target = ResourceEntitlements.currentTarget(authentication);
        query.setString(start++, target);
        query.setString(start++, target);
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
        try (var query = connection.prepareStatement(ScopeAccessSql.REQUIRE)) {
            query.setLong(1, actor.id());
            query.setLong(2, actor.tokenVersion());
            query.setString(3, permission);
            String target = ResourceEntitlements.currentTarget(authentication);
            query.setString(4, target);
            query.setString(5, target);
            query.setLong(6, scopeId);
            try (var row = query.executeQuery()) {
                if (!row.next() || !row.getBoolean(1)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    /** Chỉ dùng ID scope đã đọc từ DB của cha/con, không so hai trường do client cùng tự điền. */
    public static void requireSameScope(long parentScope, long childScope) {
        if (parentScope < 1 || parentScope != childScope) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }
}
