package vn.vietroute.auth.repository;

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
import vn.vietroute.auth.config.AccessSettings;
import vn.vietroute.auth.dto.request.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.DataScopeRequest;
import vn.vietroute.auth.dto.request.ScopeMemberSearchRequest;
import vn.vietroute.auth.dto.request.ScopeMembershipRequest;
import vn.vietroute.auth.dto.response.DataScopeResponse;
import vn.vietroute.auth.dto.response.ScopeMembershipResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Metadata/membership auth; không đọc hoặc tạo bảng nhà xe/chuyến, mọi mutation dùng transaction service. */
@Repository
public class DataScopeRepository extends BaseRepository {

    private final AccessSettings settings;

    private static final String MEMBER_COLUMNS = """
    SELECT m.scope_id,m.user_id,u.username,m.active,m.version,m.updated_at,m.updated_by,
           ARRAY(SELECT role_code FROM auth_scope_roles sr WHERE sr.scope_id=m.scope_id AND sr.user_id=m.user_id ORDER BY role_code) AS role_codes
    FROM auth_scope_memberships m JOIN auth_users u ON u.id=m.user_id
    """;

    public DataScopeRepository(JdbcConnectionFactory connections, AccessSettings settings) {
        super(connections);
        this.settings = settings;
    }

    /** Từ khóa bind nguyên văn; active/đếm/trang cùng xử lý tại DB. */
    public PageResponse<DataScopeResponse> page(AccessRegistrySearchRequest search) {
        return read(connection -> {
            String where =
                " FROM auth_data_scopes WHERE (?::boolean IS NULL OR active=?) AND (code ILIKE ? ESCAPE '!' OR name_vi ILIKE ? ESCAPE '!' OR name_en ILIKE ? ESCAPE '!')";
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(query, search);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            List<DataScopeResponse> values = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT id,code,name_vi,name_en,active,version,updated_at,updated_by" +
                        where +
                        " ORDER BY code,id LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search);
                query.setInt(6, search.getSize());
                query.setLong(7, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) values.add(scope(rows));
                }
            }
            return PageResponse.of(values, search, total);
        });
    }

    public DataScopeResponse find(Connection connection, long id) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "SELECT id,code,name_vi,name_en,active,version,updated_at,updated_by FROM auth_data_scopes WHERE id=?"
            )
        ) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                return row.next() ? scope(row) : null;
            }
        }
    }

    /** Service giữ advisory lock gán chung; quota/metadata/version và audit được commit hoặc rollback cùng nhau. */
    public long save(Connection connection, long id, DataScopeRequest body, long actor) throws SQLException {
        if (id == 0) {
            try (
                var query = connection.prepareStatement("SELECT count(*) FROM auth_data_scopes");
                var row = query.executeQuery()
            ) {
                row.next();
                if (row.getLong(1) >= settings.getScopeCapacity()) throw new BusinessException(
                    ErrorCode.ACCESS_CAPACITY_REACHED,
                    settings.getScopeCapacity()
                );
            }
        }
        String sql =
            id == 0
                ? "INSERT INTO auth_data_scopes(code,name_vi,name_en,active,created_by,updated_by) VALUES (?,?,?,?,?,?) RETURNING id"
                : "UPDATE auth_data_scopes SET name_vi=?,name_en=?,active=?,updated_by=?,updated_at=CURRENT_TIMESTAMP,version=version+1 WHERE id=? AND code=? AND version=? RETURNING id";
        try (var query = connection.prepareStatement(sql)) {
            if (id == 0) {
                query.setString(1, body.code());
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
                query.setLong(5, id);
                query.setString(6, body.code());
                query.setLong(7, body.version());
            }
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.STALE_VERSION);
                return row.getLong(1);
            }
        }
    }

    /** Snapshot mapping thô: giữ cả role tắt để xem/thu hồi, không coi danh sách này là quyền hiệu lực. */
    public ScopeMembershipResponse member(Connection connection, long scope, long user) throws SQLException {
        try (var query = connection.prepareStatement(MEMBER_COLUMNS + " WHERE m.scope_id=? AND m.user_id=?")) {
            query.setLong(1, scope);
            query.setLong(2, user);
            try (var row = query.executeQuery()) {
                return row.next() ? member(row) : null;
            }
        }
    }

    /** Snapshot tối đa 32 membership trong transaction gán role toàn cục để ghi thay đổi do FK thu hồi. */
    public static List<ScopeMembershipResponse> memberships(Connection connection, long user) throws SQLException {
        List<ScopeMembershipResponse> result = new ArrayList<>();
        try (
            var query = connection.prepareStatement(MEMBER_COLUMNS + " WHERE m.user_id=? ORDER BY m.scope_id LIMIT 33")
        ) {
            query.setLong(1, user);
            try (var rows = query.executeQuery()) {
                while (rows.next()) result.add(member(rows));
            }
        }
        if (result.size() > 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
        return List.copyOf(result);
    }

    public PageResponse<ScopeMembershipResponse> members(long scope, ScopeMemberSearchRequest search) {
        return read(connection -> {
            long total;
            String where = " WHERE m.scope_id=? AND (?::bigint IS NULL OR m.user_id=?)";
            try (var query = connection.prepareStatement("SELECT count(*) FROM auth_scope_memberships m" + where)) {
                memberFilter(query, scope, search);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            List<ScopeMembershipResponse> values = new ArrayList<>();
            try (
                var query = connection.prepareStatement(MEMBER_COLUMNS + where + " ORDER BY m.user_id LIMIT ? OFFSET ?")
            ) {
                memberFilter(query, scope, search);
                query.setInt(4, search.getSize());
                query.setLong(5, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) values.add(member(rows));
                }
            }
            return PageResponse.of(values, search, total);
        });
    }

    /** Role scoped là tập con của role toàn cục đã được service kiểm; FK chặn cả sai sót từ SQL/cạnh tranh. */
    public void assign(Connection connection, long scope, long user, ScopeMembershipRequest body, long actor)
        throws SQLException {
        if (body.version() == 0) {
            try (
                var query = connection.prepareStatement("SELECT count(*) FROM auth_scope_memberships WHERE user_id=?")
            ) {
                query.setLong(1, user);
                try (var row = query.executeQuery()) {
                    row.next();
                    if (row.getLong(1) >= 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
                }
            }
        }
        String sql =
            body.version() == 0
                ? "INSERT INTO auth_scope_memberships(scope_id,user_id,active,created_by,updated_by,version) VALUES (?,?,?,?,?,1) ON CONFLICT DO NOTHING"
                : "UPDATE auth_scope_memberships SET active=?,version=version+1,updated_by=?,updated_at=CURRENT_TIMESTAMP WHERE scope_id=? AND user_id=? AND version=?";
        try (var query = connection.prepareStatement(sql)) {
            if (body.version() == 0) {
                query.setLong(1, scope);
                query.setLong(2, user);
                query.setBoolean(3, body.active());
                query.setLong(4, actor);
                query.setLong(5, actor);
            } else {
                query.setBoolean(1, body.active());
                query.setLong(2, actor);
                query.setLong(3, scope);
                query.setLong(4, user);
                query.setLong(5, body.version());
            }
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        try (var query = connection.prepareStatement("DELETE FROM auth_scope_roles WHERE scope_id=? AND user_id=?")) {
            query.setLong(1, scope);
            query.setLong(2, user);
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_scope_roles(scope_id,user_id,role_code,created_by,updated_by) VALUES (?,?,?,?,?)"
            )
        ) {
            for (Role role : body.roles()) {
                query.setLong(1, scope);
                query.setLong(2, user);
                query.setString(3, role.name());
                query.setLong(4, actor);
                query.setLong(5, actor);
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    /** Danh sách scope để nhân viên chọn, không lấy scope đang làm việc từ cookie/JWT do client tự ghi. */
    public List<DataScopeResponse> mine(long user) {
        return read(connection -> {
            List<DataScopeResponse> values = new ArrayList<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT s.id,s.code,s.name_vi,s.name_en,s.active,s.version,s.updated_at,s.updated_by FROM auth_data_scopes s JOIN auth_scope_memberships m ON m.scope_id=s.id AND m.active JOIN auth_users u ON u.id=m.user_id WHERE m.user_id=? AND s.active AND u.active AND NOT u.activation_pending AND NOT u.must_change_password AND EXISTS (SELECT 1 FROM auth_scope_roles sr JOIN auth_roles r ON r.code=sr.role_code AND r.active AND NOT r.deleted WHERE sr.scope_id=m.scope_id AND sr.user_id=m.user_id) ORDER BY s.code LIMIT 32"
                )
            ) {
                query.setLong(1, user);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) values.add(scope(rows));
                }
            }
            return List.copyOf(values);
        });
    }

    /** Quyền scoped cho UI; không có membership/quyền ADMIN hiện hành thì trả 403, không lấy union role ở scope khác. */
    public Set<Permission> permissions(long user, long tokenVersion, long scope) {
        return read(connection -> {
            try (
                var query = connection.prepareStatement(
                    """
                    SELECT ARRAY(
                        SELECT DISTINCT p.code FROM auth_user_roles ur
                        JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
                        JOIN auth_role_permissions rp ON rp.role_code=r.code
                        JOIN auth_permissions p ON p.code=rp.permission_code AND p.active
                        WHERE ur.user_id=u.id AND (r.code='ADMIN' OR EXISTS (
                            SELECT 1 FROM auth_scope_memberships m JOIN auth_scope_roles sr ON sr.scope_id=m.scope_id AND sr.user_id=m.user_id
                            WHERE m.scope_id=s.id AND m.user_id=u.id AND m.active AND sr.role_code=r.code
                        )) ORDER BY p.code
                    ) AS permission_codes
                    FROM auth_users u JOIN auth_data_scopes s ON s.id=? AND s.active
                    WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.activation_pending AND NOT u.must_change_password
                      AND (EXISTS (SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted WHERE ur.user_id=u.id AND r.code='ADMIN')
                           OR EXISTS (SELECT 1 FROM auth_scope_memberships m JOIN auth_scope_roles sr ON sr.scope_id=m.scope_id AND sr.user_id=m.user_id JOIN auth_roles r ON r.code=sr.role_code AND r.active AND NOT r.deleted WHERE m.scope_id=s.id AND m.user_id=u.id AND m.active))
                    """
                )
            ) {
                query.setLong(1, scope);
                query.setLong(2, user);
                query.setLong(3, tokenVersion);
                try (var row = query.executeQuery()) {
                    if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    return AccountAccessSql.permissions(row);
                }
            }
        });
    }

    private static void bind(PreparedStatement query, AccessRegistrySearchRequest search) throws SQLException {
        query.setObject(1, search.getActive(), Types.BOOLEAN);
        query.setObject(2, search.getActive(), Types.BOOLEAN);
        String term = "%" + search.getQuery().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        for (int index = 3; index <= 5; index++) query.setString(index, term);
    }

    private static void memberFilter(PreparedStatement query, long scope, ScopeMemberSearchRequest search)
        throws SQLException {
        query.setLong(1, scope);
        query.setObject(2, search.getUserId(), Types.BIGINT);
        query.setObject(3, search.getUserId(), Types.BIGINT);
    }

    private static DataScopeResponse scope(ResultSet row) throws SQLException {
        return new DataScopeResponse(
            row.getLong("id"),
            row.getString("code"),
            row.getString("name_vi"),
            row.getString("name_en"),
            row.getBoolean("active"),
            row.getLong("version"),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("updated_by")
        );
    }

    private static ScopeMembershipResponse member(ResultSet row) throws SQLException {
        return new ScopeMembershipResponse(
            row.getLong("scope_id"),
            row.getLong("user_id"),
            row.getString("username"),
            row.getBoolean("active"),
            AccountAccessSql.strings(row, "role_codes")
                .stream()
                .map(Role::valueOf)
                .collect(Collectors.toUnmodifiableSet()),
            row.getLong("version"),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("updated_by")
        );
    }
}
