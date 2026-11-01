package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.request.function.FunctionCreateRequest;
import vn.vietroute.auth.dto.request.function.FunctionSearchRequest;
import vn.vietroute.auth.dto.response.function.FunctionResponse;
import vn.vietroute.auth.dto.response.resource.AccessResourceResponse;
import vn.vietroute.auth.dto.response.role.RoleFunctionsResponse;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** CRUD Function, binding và role mapping theo tập; toàn bộ SQL ở repo và dùng cùng connection mutation. */
@Repository
public class FunctionRepository extends BaseRepository {

    private static final String COLUMNS =
        "f.code,f.name_vi,f.name_en,f.active,f.source_managed,f.registry_version,f.updated_at,f.updated_by,ARRAY(SELECT DISTINCT target FROM auth_function_resources fr WHERE fr.function_code=f.code ORDER BY target) AS targets";
    private static final String VISIBLE =
        "(NOT f.source_managed OR EXISTS(SELECT 1 FROM auth_function_resources fr WHERE fr.function_code=f.code AND NOT fr.public_route AND fr.declared_permissions<>''))";

    public FunctionRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Hai statement cố định cho count/page, không query DB cho từng function hoặc role. */
    public PageResponse<FunctionResponse> page(FunctionSearchRequest search) {
        return read(connection -> {
            String where =
                " FROM auth_functions f WHERE NOT f.deleted AND NOT f.source_managed AND (?::boolean IS NULL OR f.active=?) AND strpos(lower(concat_ws(' ',f.code,f.name_vi,f.name_en)),lower(?))>0";
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(query, search);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var result = new ArrayList<FunctionResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + COLUMNS + where + " ORDER BY f.name_vi,f.code LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search);
                query.setInt(4, search.getSize());
                query.setLong(5, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.add(row(rows));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    public FunctionResponse find(Connection connection, String code) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM auth_functions f WHERE f.code=? AND NOT f.deleted"
            )
        ) {
            query.setString(1, code);
            try (var row = query.executeQuery()) {
                return row.next() ? row(row) : null;
            }
        }
    }

    /** Kiểm quota dưới advisory lock của service, kể cả function tùy chỉnh đang tắt. */
    public void requireCapacity(Connection connection, int capacity) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT count(*) FROM auth_functions WHERE NOT source_managed AND NOT deleted"
            );
            var row = query.executeQuery()
        ) {
            row.next();
            if (row.getLong(1) >= capacity) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, capacity);
        }
    }

    /** CAS function và batch binding server; phép đổi target cập nhật projection mọi role bị ảnh hưởng nguyên tử. */
    public FunctionResponse save(
        Connection connection,
        FunctionCreateRequest body,
        long version,
        List<AccessResourceResponse> resources,
        long actor,
        boolean create
    ) throws SQLException {
        String operations = resources
            .stream()
            .flatMap(item -> AccessResourceRepository.operations(item).stream())
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));
        String sql = create
            ? "INSERT INTO auth_functions(code,name_vi,name_en,active,operation_codes,created_by,updated_by) VALUES (?,?,?,?,array_remove(string_to_array(?,','),''),?,?) ON CONFLICT DO NOTHING"
            : "UPDATE auth_functions SET name_vi=?,name_en=?,active=?,operation_codes=CASE WHEN source_managed THEN operation_codes ELSE array_remove(string_to_array(?,','),'') END,registry_version=registry_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND registry_version=? AND NOT deleted";
        try (var query = connection.prepareStatement(sql)) {
            int i = 1;
            if (create) query.setString(i++, body.getCode());
            query.setString(i++, body.getNameVi().strip());
            query.setString(i++, body.getNameEn().strip());
            query.setBoolean(i++, body.isActive());
            query.setString(i++, operations);
            query.setLong(i++, actor);
            if (create) query.setLong(i, actor);
            else {
                query.setString(i++, body.getCode());
                query.setLong(i, version);
            }
            if (query.executeUpdate() != 1) throw new BusinessException(
                create ? ErrorCode.CONFLICT : ErrorCode.STALE_VERSION
            );
        }
        var current = find(connection, body.getCode());
        if (!current.isSourceManaged()) {
            try (var query = connection.prepareStatement("DELETE FROM auth_function_resources WHERE function_code=?")) {
                query.setString(1, body.getCode());
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "INSERT INTO auth_function_resources(function_code,kind,target,handler,description,resource_group,feature_code,declared_permissions,permission_match,admin_only,public_route,configurable,created_by,updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,false,?,?,?)"
                )
            ) {
                for (var resource : resources) {
                    query.setString(1, body.getCode());
                    query.setString(2, resource.kind());
                    query.setString(3, resource.target());
                    query.setString(4, resource.handler());
                    query.setString(5, resource.description());
                    query.setString(6, resource.group());
                    query.setString(7, resource.function());
                    query.setString(8, String.join(",", resource.declaredPermissions()));
                    query.setString(9, resource.match());
                    query.setBoolean(10, resource.adminOnly());
                    query.setBoolean(11, resource.configurable());
                    query.setLong(12, actor);
                    query.setLong(13, actor);
                    query.addBatch();
                }
                query.executeBatch();
            }
            try (
                var query = connection.prepareStatement(
                    "UPDATE auth_role_functions rf SET operation_codes=f.operation_codes,updated_at=CURRENT_TIMESTAMP,updated_by=? FROM auth_functions f WHERE f.code=rf.function_code AND f.code=?"
                )
            ) {
                query.setLong(1, actor);
                query.setString(2, body.getCode());
                query.executeUpdate();
            }
        }
        // Cả bật/tắt cũng làm stale form gán quyền; không ghi đè một snapshot role được mở trước thay đổi.
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_roles r SET permission_version=permission_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE EXISTS(SELECT 1 FROM auth_role_functions rf WHERE rf.role_code=r.code AND rf.function_code=?)"
            )
        ) {
            query.setLong(1, actor);
            query.setString(2, body.getCode());
            query.executeUpdate();
        }
        return find(connection, body.getCode());
    }

    /** Xóa mềm khi không còn role tham chiếu; tombstone giữ code để không tái sử dụng ngoài ý muốn. */
    public void delete(Connection connection, String code, long version, long actor) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT EXISTS(SELECT 1 FROM auth_role_functions WHERE function_code=?)"
            )
        ) {
            query.setString(1, code);
            try (var row = query.executeQuery()) {
                row.next();
                if (row.getBoolean(1)) throw new BusinessException(ErrorCode.RESOURCE_IN_USE);
            }
        }
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_functions SET active=false,deleted=true,registry_version=registry_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE code=? AND registry_version=? AND NOT deleted AND NOT source_managed"
            )
        ) {
            query.setLong(1, actor);
            query.setString(2, code);
            query.setLong(3, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
    }

    public RoleFunctionsResponse role(Connection connection, Role role) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT r.permission_version,ARRAY(SELECT rf.function_code FROM auth_role_functions rf JOIN auth_functions f ON f.code=rf.function_code WHERE rf.role_code=r.code AND " +
                    VISIBLE +
                    " ORDER BY rf.function_code) AS functions FROM auth_roles r WHERE r.code=? AND NOT r.deleted"
            )
        ) {
            query.setString(1, role.name());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                return new RoleFunctionsResponse(
                    role.name(),
                    AccountAccessSql.strings(row, "functions"),
                    row.getLong(1)
                );
            }
        }
    }

    /** Giữ mapping không đổi và projection chuyển tiếp ALL cũ; tập rỗng luôn deny, không query theo function. */
    public RoleFunctionsResponse assign(
        Connection connection,
        Role role,
        Set<String> functions,
        long version,
        long actor
    ) throws SQLException {
        var codes = connection.createArrayOf("text", functions.toArray(String[]::new));
        try {
            try (
                var query = connection.prepareStatement(
                    "SELECT count(*)=(SELECT count(*) FROM unnest(?::text[])) FROM auth_functions f WHERE code=ANY(?) AND active AND NOT deleted AND " +
                        VISIBLE
                )
            ) {
                query.setArray(1, codes);
                query.setArray(2, codes);
                try (var row = query.executeQuery()) {
                    row.next();
                    if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.INVALID_REQUEST);
                }
            }
            AccessRepository.bump(connection, role, version, actor);
            try (
                var query = connection.prepareStatement(
                    "DELETE FROM auth_role_functions WHERE role_code=? AND NOT(function_code=ANY(?))"
                )
            ) {
                query.setString(1, role.name());
                query.setArray(2, codes);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "INSERT INTO auth_role_functions(role_code,function_code,operation_codes,created_by,updated_by) SELECT ?,code,operation_codes,?,? FROM auth_functions WHERE code=ANY(?) ON CONFLICT DO NOTHING"
                )
            ) {
                query.setString(1, role.name());
                query.setLong(2, actor);
                query.setLong(3, actor);
                query.setArray(4, codes);
                query.executeUpdate();
            }
            return role(connection, role);
        } finally {
            codes.free();
        }
    }

    /** Đọc và kiểm cả tập thao tác trong một query; role tắt/permission tắt vẫn không giúp vượt trần ủy quyền. */
    public void requireDelegable(Connection connection, Set<String> selected, Set<String> held) throws SQLException {
        var codes = connection.createArrayOf("text", selected.toArray(String[]::new));
        var permissions = connection.createArrayOf("text", held.toArray(String[]::new));
        try (
            var query = connection.prepareStatement(
                "SELECT NOT EXISTS(SELECT 1 FROM auth_functions f CROSS JOIN LATERAL unnest(f.operation_codes) op(code) WHERE f.code=ANY(?) AND NOT(op.code=ANY(?)))"
            )
        ) {
            query.setArray(1, codes);
            query.setArray(2, permissions);
            try (var row = query.executeQuery()) {
                row.next();
                if (!row.getBoolean(1)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        } finally {
            codes.free();
            permissions.free();
        }
    }

    public Set<String> targets(Connection connection, Set<String> selected) throws SQLException {
        var codes = connection.createArrayOf("text", selected.toArray(String[]::new));
        try (
            var query = connection.prepareStatement(
                "SELECT ARRAY(SELECT DISTINCT target FROM auth_function_resources WHERE function_code=ANY(?) ORDER BY target) AS targets"
            )
        ) {
            query.setArray(1, codes);
            try (var row = query.executeQuery()) {
                row.next();
                return AccountAccessSql.strings(row, "targets");
            }
        } finally {
            codes.free();
        }
    }

    private static void bind(PreparedStatement query, FunctionSearchRequest search) throws SQLException {
        query.setObject(1, search.getActive(), Types.BOOLEAN);
        query.setObject(2, search.getActive(), Types.BOOLEAN);
        query.setString(3, search.getQuery().strip());
    }

    private static FunctionResponse row(ResultSet row) throws SQLException {
        return new FunctionResponse(
            row.getString("code"),
            row.getString("name_vi"),
            row.getString("name_en"),
            row.getBoolean("active"),
            row.getBoolean("source_managed"),
            AccountAccessSql.strings(row, "targets"),
            row.getLong("registry_version"),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("updated_by")
        );
    }
}
