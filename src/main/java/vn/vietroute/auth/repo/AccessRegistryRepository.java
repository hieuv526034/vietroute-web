package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.config.AuthSettings;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.registry.AccessRegistryRequest;
import vn.vietroute.auth.dto.response.policy.AccessPolicyResponse;
import vn.vietroute.auth.dto.response.registry.AccessRegistryPageResponse;
import vn.vietroute.auth.dto.response.registry.AccessRegistryResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.AccessPermissionDefinitions;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Limits;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Registry chỉ chọn tên bảng từ allowlist; mọi dữ liệu client được bind, không nối vào SQL. */
@Repository
public class AccessRegistryRepository extends BaseRepository {

    private final AuthSettings settings;

    private final AccessPermissionDefinitions permissions;

    public AccessRegistryRepository(
        JdbcConnectionFactory connections,
        AuthSettings settings,
        AccessPermissionDefinitions permissions
    ) {
        super(connections);
        this.settings = settings;
        this.permissions = permissions;
    }

    /** Quota chỉ tính mã chưa xóa; service giữ khóa gán toàn cục trước khi gọi để chống vượt hạn mức chéo node. */
    private void requireCapacity(Connection connection, String table, String filter, int capacity) throws SQLException {
        try (
            var query = connection.prepareStatement("SELECT count(*) FROM " + table + filter);
            var rows = query.executeQuery()
        ) {
            rows.next();
            if (rows.getLong(1) >= capacity) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, capacity);
        }
    }

    /** Đọc đúng mã trong transaction, không tải toàn bộ registry để tìm một bản ghi khi sửa/audit. */
    public AccessRegistryResponse find(Connection connection, String kind, String code) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT code,name_vi,name_en,active,registry_version,updated_at,updated_by FROM " +
                    table(kind) +
                    " WHERE code=?" +
                    ("roles".equals(kind) ? " AND NOT deleted" : " AND NOT deleted AND code=ANY(operation_codes)")
            )
        ) {
            query.setString(1, code);
            try (var rows = query.executeQuery()) {
                return rows.next() ? row(kind, rows) : null;
            }
        }
    }

    /** Count/filter/page cùng chạy ở DB; wildcard được escape, không nối dữ liệu tìm kiếm vào SQL. */
    public AccessRegistryPageResponse page(String kind, AccessRegistrySearchRequest search) {
        return read(connection -> {
            String from =
                " FROM " +
                table(kind) +
                ("roles".equals(kind) ? " WHERE NOT deleted" : " WHERE NOT deleted AND code=ANY(operation_codes)");
            String matches =
                " (?::boolean IS NULL OR active=?) AND (code ILIKE ? ESCAPE '!' OR name_vi ILIKE ? ESCAPE '!' OR name_en ILIKE ? ESCAPE '!') ";
            long total, allocated;
            try (
                var query = connection.prepareStatement(
                    "SELECT count(*) FILTER (WHERE " + matches + "),count(*)" + from
                )
            ) {
                bindSearch(query, search);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    total = rows.getLong(1);
                    allocated = rows.getLong(2);
                }
            }
            List<AccessRegistryResponse> values = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT code,name_vi,name_en,active,registry_version,updated_at,updated_by" +
                        from +
                        " AND " +
                        matches +
                        " ORDER BY code LIMIT ? OFFSET ?"
                )
            ) {
                bindSearch(query, search);
                query.setInt(6, search.getSize());
                query.setLong(7, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) values.add(row(kind, rows));
                }
            }
            return new AccessRegistryPageResponse(
                PageResponse.of(values, search, total),
                settings.getRegistryCapacity(),
                allocated
            );
        });
    }

    private static void bindSearch(PreparedStatement query, AccessRegistrySearchRequest search) throws SQLException {
        query.setObject(1, search.getActive(), Types.BOOLEAN);
        query.setObject(2, search.getActive(), Types.BOOLEAN);
        String value = "%" + search.getQuery().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        for (int index = 3; index <= 5; index++) query.setString(index, value);
    }

    private AccessRegistryResponse row(String kind, ResultSet rows) throws SQLException {
        String code = rows.getString(1);
        return new AccessRegistryResponse(
            code,
            rows.getString(2),
            rows.getString(3),
            rows.getBoolean(4),
            builtIn(kind, code),
            rows.getLong(5),
            rows.getTimestamp(6).toInstant(),
            rows.getLong(7)
        );
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
                    ("roles".equals(kind) ? " WHERE NOT deleted" : " WHERE NOT deleted AND code=ANY(operation_codes)") +
                    " ORDER BY code LIMIT " +
                    (Limits.REGISTRY_MAX + 1)
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
        if (result.size() > Limits.REGISTRY_MAX) throw new BusinessException(
            ErrorCode.ACCESS_CAPACITY_REACHED,
            Limits.REGISTRY_MAX
        );
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
        if (create) requireCapacity(
            connection,
            table,
            "roles".equals(kind) ? " WHERE NOT deleted" : " WHERE NOT deleted AND code=ANY(operation_codes)",
            settings.getRegistryCapacity()
        );
        String sql = create
            ? "INSERT INTO " +
              table +
              ("roles".equals(kind)
                  ? "(code,name_vi,name_en,active,created_by,updated_by) VALUES (?,?,?,?,?,?)"
                  : "(code,name_vi,name_en,active,created_by,updated_by,operation_codes) VALUES (?,?,?,?,?,?,ARRAY[?])") +
              " ON CONFLICT(code) DO NOTHING"
            : "UPDATE " +
              table +
              " SET name_vi=?,name_en=?,active=?,registry_version=registry_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND registry_version=?" +
              ("roles".equals(kind) ? " AND NOT deleted" : " AND NOT deleted AND code=ANY(operation_codes)");
        try (PreparedStatement query = connection.prepareStatement(sql)) {
            if (create) {
                query.setString(1, code);
                query.setString(2, body.nameVi().strip());
                query.setString(3, body.nameEn().strip());
                query.setBoolean(4, body.active());
                query.setLong(5, actor);
                query.setLong(6, actor);
                if ("permissions".equals(kind)) query.setString(7, code);
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
                    "INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_by,updated_by) SELECT 'ADMIN',code,ARRAY[code],?,? FROM auth_functions WHERE code=? ON CONFLICT DO NOTHING"
                )
            ) {
                grant.setLong(1, actor);
                grant.setLong(2, actor);
                grant.setString(3, code);
                grant.executeUpdate();
            }
        }
        if (create && "roles".equals(kind)) RoleResourceRepository.mark(connection, code, actor);
    }

    /** Chặn tham chiếu user/policy; chỉ dọn mapping của role sau khi kiểm version trong cùng transaction. */
    public void deleteRole(Connection connection, String code, long version, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement("SELECT EXISTS(SELECT 1 FROM auth_user_roles WHERE role_code=?)")
        ) {
            query.setString(1, code);
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
        try (var query = connection.prepareStatement("DELETE FROM auth_role_functions WHERE role_code=?")) {
            query.setString(1, code);
            query.executeUpdate();
        }
    }

    public List<AccessPolicyResponse> policies() {
        return List.of();
    }

    public List<AccessPolicyResponse> policies(Connection connection) throws SQLException {
        return List.of();
    }

    public boolean builtIn(String kind, String code) {
        return "roles".equals(kind) ? Role.ADMIN.name().equals(code) : permissions.contains(code);
    }

    private static String table(String kind) {
        return switch (kind) {
            case "roles" -> "auth_roles";
            case "permissions" -> "auth_functions";
            default -> throw new BusinessException(ErrorCode.INVALID_REQUEST);
        };
    }

    private static Set<String> split(String value) {
        return value.isEmpty() ? Set.of() : Set.copyOf(Arrays.asList(value.split(",")));
    }
}
