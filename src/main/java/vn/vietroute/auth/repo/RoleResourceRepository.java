package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.response.resource.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Adapter của API target cũ trên Function; không lưu ma trận quyền hoặc marker thứ hai. */
@Repository
public class RoleResourceRepository {

    @Getter
    @AllArgsConstructor
    public static class Grant {

        private final Role role;
        private final boolean initialized;
        private final Set<Permission> permissions;
        private final Set<String> targets;

        public Role role() {
            return role;
        }

        public boolean initialized() {
            return initialized;
        }

        public Set<Permission> permissions() {
            return permissions;
        }

        public Set<String> targets() {
            return targets;
        }
    }

    /** Một snapshot cho toàn bộ role đích, không query theo mỗi role. */
    public List<Grant> grants(Connection connection, Set<Role> roles) throws SQLException {
        var codes = connection.createArrayOf(
            "text",
            roles
                .stream()
                .map(Role::name)
                .toArray(String[]::new)
        );
        var result = new ArrayList<Grant>();
        try (
            var query = connection.prepareStatement(
                "SELECT r.code,ARRAY(SELECT DISTINCT op FROM auth_role_functions rf CROSS JOIN LATERAL unnest(rf.operation_codes) op WHERE rf.role_code=r.code ORDER BY op) AS permission_codes,ARRAY(SELECT DISTINCT target FROM auth_role_functions rf JOIN auth_function_resources fr ON fr.function_code=rf.function_code WHERE rf.role_code=r.code ORDER BY target) AS targets FROM auth_roles r WHERE r.code=ANY(?) AND NOT r.deleted ORDER BY r.code"
            )
        ) {
            query.setArray(1, codes);
            try (var rows = query.executeQuery()) {
                while (rows.next())
                    result.add(
                        new Grant(
                            Role.valueOf(rows.getString(1)),
                            true,
                            AccountAccessSql.permissions(rows),
                            AccountAccessSql.strings(rows, "targets")
                        )
                    );
            }
            if (result.size() != roles.size()) throw new BusinessException(ErrorCode.NOT_FOUND);
            return List.copyOf(result);
        } finally {
            codes.free();
        }
    }

    /** Mọi role dùng native Function; tập rỗng là deny ngay từ khi tạo. */
    public boolean initialized(Connection connection, Role role) {
        return true;
    }

    public static void initializeLegacy(Connection connection) {}

    public static void mark(Connection connection, String role, long actor) {}

    public Set<String> targets(Connection connection, Role role) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT ARRAY(SELECT target FROM (" +
                    FunctionSql.ROLE_TARGETS +
                    ") t WHERE role_code=? ORDER BY target) AS targets"
            )
        ) {
            query.setString(1, role.name());
            try (var row = query.executeQuery()) {
                row.next();
                return AccountAccessSql.strings(row, "targets");
            }
        }
    }

    /** Caller đã kiểm projection/ủy quyền; thay target bằng set-wise SQL và giữ phần thao tác cũ được phép. */
    public void replace(Connection connection, Role role, Set<AccessResourceResponse> selected, long actor)
        throws SQLException {
        var targets = connection.createArrayOf(
            "text",
            selected
                .stream()
                .map(AccessResourceResponse::target)
                .toArray(String[]::new)
        );
        try {
            try (
                var query = connection.prepareStatement(
                    "DELETE FROM auth_role_functions rf WHERE role_code=? AND EXISTS(SELECT 1 FROM auth_function_resources fr WHERE fr.function_code=rf.function_code)"
                )
            ) {
                query.setString(1, role.name());
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    """
                    INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_by,updated_by)
                    SELECT ?,f.code,ARRAY(SELECT op.permission_code FROM (
                    """ +
                        FunctionSql.ROLE_OPERATIONS +
                        """
                        ) op WHERE op.role_code=? AND op.permission_code=ANY(f.operation_codes)),?,?
                        FROM auth_functions f WHERE f.source_managed AND f.active AND NOT f.deleted
                          AND EXISTS(SELECT 1 FROM auth_function_resources fr WHERE fr.function_code=f.code AND fr.target=ANY(?))
                        ON CONFLICT DO NOTHING
                        """
                )
            ) {
                query.setString(1, role.name());
                query.setString(2, role.name());
                query.setLong(3, actor);
                query.setLong(4, actor);
                query.setArray(5, targets);
                query.executeUpdate();
            }
        } finally {
            targets.free();
        }
    }
}
