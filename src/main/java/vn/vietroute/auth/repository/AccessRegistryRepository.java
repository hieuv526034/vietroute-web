package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.request.AccessPolicyRequest;
import vn.vietroute.auth.dto.request.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.AccessRegistryResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Registry chỉ chọn tên bảng từ allowlist; mọi dữ liệu client được bind, không nối vào SQL. */
@Repository
public class AccessRegistryRepository extends BaseRepository {

    public AccessRegistryRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    public List<AccessRegistryResponse> registry(String kind) {
        return read(connection -> registry(connection, kind));
    }

    public List<AccessRegistryResponse> registry(Connection connection, String kind) throws SQLException {
        String table = table(kind);
        List<AccessRegistryResponse> result = new ArrayList<>();
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT code,name_vi,name_en,active,registry_version,updated_at,updated_by FROM " +
                    table +
                    ("roles".equals(kind) ? " WHERE NOT deleted" : "") +
                    " ORDER BY code LIMIT 513"
            );
            ResultSet rows = query.executeQuery()
        ) {
            while (rows.next()) {
                String code = rows.getString(1);
                result.add(
                    new AccessRegistryResponse(
                        code,
                        rows.getString(2),
                        rows.getString(3),
                        rows.getBoolean(4),
                        builtIn(kind, code),
                        rows.getLong(5),
                        rows.getTimestamp(6).toInstant(),
                        rows.getLong(7)
                    )
                );
            }
        }
        return List.copyOf(result);
    }

    /** Khóa cùng advisory lock ở service để quota và cập nhật registry nhất quán giữa nhiều node. */
    public void save(
        Connection connection,
        String kind,
        String code,
        AccessRegistryRequest body,
        long actor,
        boolean create
    ) throws SQLException {
        String table = table(kind);
        String sql = create
            ? "INSERT INTO " +
              table +
              "(code,name_vi,name_en,active,created_by,updated_by) SELECT ?,?,?,?,?,? WHERE (SELECT count(*) FROM " +
              table +
              ") < 512 ON CONFLICT(code) DO NOTHING"
            : "UPDATE " +
              table +
              " SET name_vi=?,name_en=?,active=?,registry_version=registry_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND registry_version=?" +
              ("roles".equals(kind) ? " AND NOT deleted" : "");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            if (create) {
                query.setString(1, code);
                query.setString(2, body.nameVi().strip());
                query.setString(3, body.nameEn().strip());
                query.setBoolean(4, body.active());
                query.setLong(5, actor);
                query.setLong(6, actor);
            } else {
                query.setString(1, body.nameVi().strip());
                query.setString(2, body.nameEn().strip());
                query.setBoolean(3, body.active());
                query.setLong(4, actor);
                query.setString(5, code);
                query.setLong(6, body.version());
            }
            if (query.executeUpdate() != 1) {
                throw new BusinessException(create ? ErrorCode.CONFLICT : ErrorCode.STALE_VERSION);
            }
        }
        if (create && "permissions".equals(kind)) {
            try (
                PreparedStatement grant = connection.prepareStatement(
                    "INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by) VALUES ('ADMIN',?,?,?) ON CONFLICT DO NOTHING"
                )
            ) {
                grant.setString(1, code);
                grant.setLong(2, actor);
                grant.setLong(3, actor);
                grant.executeUpdate();
            }
        }
    }

    /** Chặn tham chiếu user/policy; chỉ dọn mapping của role sau khi kiểm version trong cùng transaction. */
    public void deleteRole(Connection connection, String code, long version, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM auth_user_roles WHERE role_code=?) OR EXISTS(SELECT 1 FROM auth_access_policies WHERE ?=ANY(string_to_array(allowed_roles,',')))"
            )
        ) {
            query.setString(1, code);
            query.setString(2, code);
            try (var row = query.executeQuery()) {
                row.next();
                if (row.getBoolean(1)) throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
        }
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_roles SET deleted=true,active=false,registry_version=registry_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND code<>'ADMIN' AND NOT deleted AND registry_version=?"
            )
        ) {
            query.setLong(1, actor);
            query.setString(2, code);
            query.setLong(3, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        try (var query = connection.prepareStatement("DELETE FROM auth_role_permissions WHERE role_code=?")) {
            query.setString(1, code);
            query.executeUpdate();
        }
    }

    public List<AccessPolicyResponse> policies() {
        return read(this::policies);
    }

    public List<AccessPolicyResponse> policies(Connection connection) throws SQLException {
        List<AccessPolicyResponse> result = new ArrayList<>();
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT id,kind,target,name_vi,name_en,enabled,required_permissions,allowed_roles,version,updated_at,updated_by FROM auth_access_policies ORDER BY kind,target LIMIT 257"
            );
            ResultSet rows = query.executeQuery()
        ) {
            while (rows.next()) {
                result.add(
                    new AccessPolicyResponse(
                        rows.getLong(1),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getString(5),
                        rows.getBoolean(6),
                        split(rows.getString(7)),
                        split(rows.getString(8)),
                        rows.getLong(9),
                        rows.getTimestamp(10).toInstant(),
                        rows.getLong(11)
                    )
                );
            }
        }
        return List.copyOf(result);
    }

    public void savePolicy(Connection connection, long id, AccessPolicyRequest body, long actor) throws SQLException {
        String sql =
            id == 0
                ? "INSERT INTO auth_access_policies(kind,target,name_vi,name_en,enabled,required_permissions,allowed_roles,created_by,updated_by) SELECT ?,?,?,?,?,?,?,?,? WHERE (SELECT count(*) FROM auth_access_policies)<256 ON CONFLICT(kind,target) DO NOTHING"
                : "UPDATE auth_access_policies SET kind=?,target=?,name_vi=?,name_en=?,enabled=?,required_permissions=?,allowed_roles=?,updated_by=?,version=version+1,updated_at=CURRENT_TIMESTAMP WHERE id=? AND version=?";
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            query.setString(1, body.kind());
            query.setString(2, body.target());
            query.setString(3, body.nameVi().strip());
            query.setString(4, body.nameEn().strip());
            query.setBoolean(5, body.enabled());
            query.setString(
                6,
                body.permissions().stream().map(Permission::name).sorted().collect(Collectors.joining(","))
            );
            query.setString(7, body.roles().stream().map(Role::name).sorted().collect(Collectors.joining(",")));
            query.setLong(8, actor);
            if (id == 0) {
                query.setLong(9, actor);
            } else {
                query.setLong(9, id);
                query.setLong(10, body.version());
            }
            if (query.executeUpdate() != 1) {
                throw new BusinessException(id == 0 ? ErrorCode.CONFLICT : ErrorCode.STALE_VERSION);
            }
        }
    }

    public void deletePolicy(Connection connection, long id, long version) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "DELETE FROM auth_access_policies WHERE id=? AND version=?"
            )
        ) {
            query.setLong(1, id);
            query.setLong(2, version);
            if (query.executeUpdate() != 1) {
                throw new BusinessException(ErrorCode.STALE_VERSION);
            }
        }
    }

    public static boolean builtIn(String kind, String code) {
        return "roles".equals(kind)
            ? Role.ADMIN.name().equals(code)
            : Arrays.stream(Permission.values()).anyMatch(value -> value.name().equals(code));
    }

    private static String table(String kind) {
        return switch (kind) {
            case "roles" -> "auth_roles";
            case "permissions" -> "auth_permissions";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    private static Set<String> split(String value) {
        return value.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(value.split(",")));
    }
}
