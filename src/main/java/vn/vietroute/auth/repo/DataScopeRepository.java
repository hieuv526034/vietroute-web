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
import vn.vietroute.auth.config.AuthSettings;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.scope.DataScopeRequest;
import vn.vietroute.auth.dto.request.scope.ScopeMemberSearchRequest;
import vn.vietroute.auth.dto.request.scope.ScopeMembershipRequest;
import vn.vietroute.auth.dto.response.scope.DataScopeResponse;
import vn.vietroute.auth.dto.response.scope.ScopeMembershipResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** Phạm vi thuộc nhà xe; gán role theo nhà xe dùng chung auth_user_roles, không tạo bảng scope riêng. */
@Repository
public class DataScopeRepository extends BaseRepository {

    private static final String COLUMNS =
        "scope_id AS id,scope_code AS code,scope_name_vi AS name_vi,scope_name_en AS name_en,scope_active AS active,scope_version AS version,updated_at,updated_by";

    private static final String MEMBER_KEYS = """
    WITH membership_keys AS (
        SELECT user_id,scope_id FROM auth_user_roles WHERE scope_id>0
        UNION
        SELECT u.id,k.key::bigint FROM auth_users u
        CROSS JOIN LATERAL jsonb_object_keys(u.scope_membership_versions) k(key)
    )
    """;

    private static final String MEMBERS =
        MEMBER_KEYS +
        """
        SELECT m.scope_id,m.user_id,u.username,COALESCE(bool_and(ur.active),false) AS active,
            COALESCE((u.scope_membership_versions->>m.scope_id::text)::bigint,max(ur.version),0) AS version,
            COALESCE(max(ur.updated_at),u.updated_at) AS updated_at,COALESCE(max(ur.updated_by),u.updated_by) AS updated_by,
            array_remove(array_agg(ur.role_code ORDER BY ur.role_code),NULL) AS role_codes
        FROM membership_keys m JOIN auth_users u ON u.id=m.user_id
        LEFT JOIN auth_user_roles ur ON ur.user_id=m.user_id AND ur.scope_id=m.scope_id
        """;

    private final AuthSettings settings;

    public DataScopeRepository(JdbcConnectionFactory connections, AuthSettings settings) {
        super(connections);
        this.settings = settings;
    }

    /** Tìm/đếm/phân trang nhà xe ngay tại DB, không phân trang lịch sử trong RAM. */
    public PageResponse<DataScopeResponse> page(AccessRegistrySearchRequest search) {
        return read(connection -> {
            String where =
                " FROM carriers WHERE (?::boolean IS NULL OR scope_active=?) AND (scope_code ILIKE ? ESCAPE '!' OR scope_name_vi ILIKE ? ESCAPE '!' OR scope_name_en ILIKE ? ESCAPE '!')";
            long total;
            try (var query = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(query, search);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var result = new ArrayList<DataScopeResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " + COLUMNS + where + " ORDER BY scope_code LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search);
                query.setInt(6, search.getSize());
                query.setLong(7, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.add(scope(rows));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    public DataScopeResponse find(Connection connection, long id) throws SQLException {
        try (var query = connection.prepareStatement("SELECT " + COLUMNS + " FROM carriers WHERE scope_id=?")) {
            query.setLong(1, id);
            try (var row = query.executeQuery()) {
                return row.next() ? scope(row) : null;
            }
        }
    }

    /** Khóa quản trị do caller giữ; hạn mức áp dụng cả nhà xe tắt và ID không bị tái sử dụng. */
    public long allocate(Connection connection) throws SQLException {
        try (var query = connection.prepareStatement("SELECT count(*) FROM carriers"); var row = query.executeQuery()) {
            row.next();
            if (row.getLong(1) >= settings.getScopeCapacity()) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                settings.getScopeCapacity()
            );
        }
        try (
            var query = connection.prepareStatement("SELECT nextval('carrier_scope_ids')");
            var row = query.executeQuery()
        ) {
            row.next();
            return row.getLong(1);
        }
    }

    /** Chỉ sửa phạm vi của nhà xe có thật; tạo nhà xe qua CarrierService. */
    public long save(Connection connection, long id, DataScopeRequest body, long actor) throws SQLException {
        if (id <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        try (
            var query = connection.prepareStatement(
                "UPDATE carriers SET scope_name_vi=?,scope_name_en=?,scope_active=?,scope_version=scope_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE scope_id=? AND scope_code=? AND scope_version=? RETURNING scope_id"
            )
        ) {
            query.setString(1, body.nameVi().strip());
            query.setString(2, body.nameEn().strip());
            query.setBoolean(3, body.active());
            query.setLong(4, actor);
            query.setLong(5, id);
            query.setString(6, body.code());
            query.setLong(7, body.version());
            try (var row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.STALE_VERSION);
                return row.getLong(1);
            }
        }
    }

    public ScopeMembershipResponse member(Connection connection, long scope, long user) throws SQLException {
        try (
            var query = connection.prepareStatement(
                MEMBERS + " WHERE m.scope_id=? AND m.user_id=? GROUP BY m.scope_id,m.user_id,u.id"
            )
        ) {
            query.setLong(1, scope);
            query.setLong(2, user);
            try (var row = query.executeQuery()) {
                return row.next() ? member(row) : null;
            }
        }
    }

    public static List<ScopeMembershipResponse> memberships(Connection connection, long user) throws SQLException {
        var result = new ArrayList<ScopeMembershipResponse>();
        try (
            var query = connection.prepareStatement(
                MEMBERS + " WHERE m.user_id=? GROUP BY m.scope_id,m.user_id,u.id ORDER BY m.scope_id LIMIT 33"
            )
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
            try (
                var query = connection.prepareStatement(
                    MEMBER_KEYS +
                        "SELECT count(*) FROM membership_keys WHERE scope_id=? AND (?::bigint IS NULL OR user_id=?)"
                )
            ) {
                bindMember(query, scope, search);
                try (var row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            var result = new ArrayList<ScopeMembershipResponse>();
            try (
                var query = connection.prepareStatement(
                    MEMBERS +
                        " WHERE m.scope_id=? AND (?::bigint IS NULL OR m.user_id=?) GROUP BY m.scope_id,m.user_id,u.id ORDER BY m.user_id LIMIT ? OFFSET ?"
                )
            ) {
                bindMember(query, scope, search);
                query.setInt(4, search.getSize());
                query.setLong(5, search.offset());
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.add(member(rows));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    /** CAS dưới khóa quản trị; thay toàn bộ tập bằng INSERT SELECT, giữ FK scoped là tập con global. */
    public void assign(Connection connection, long scope, long user, ScopeMembershipRequest body, long actor)
        throws SQLException {
        var before = member(connection, scope, user);
        long version = before == null ? 0 : before.version();
        if (body.version() != version) throw new BusinessException(ErrorCode.STALE_VERSION);
        if (before == null) try (
            var query = connection.prepareStatement(
                MEMBER_KEYS + "SELECT count(*) FROM membership_keys WHERE user_id=?"
            )
        ) {
            query.setLong(1, user);
            try (var row = query.executeQuery()) {
                row.next();
                if (row.getLong(1) >= 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
            }
        }
        var codes = connection.createArrayOf(
            "text",
            body
                .roles()
                .stream()
                .map(Role::name)
                .toArray(String[]::new)
        );
        try {
            // Giữ mốc version kể cả tập role rỗng; advisory lock chung bảo vệ CAS với gán role toàn cục.
            try (
                var query = connection.prepareStatement(
                    "UPDATE auth_users SET scope_membership_versions=jsonb_set(scope_membership_versions,ARRAY[?::text],to_jsonb(?::bigint),true),version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=?"
                )
            ) {
                query.setLong(1, scope);
                query.setLong(2, version + 1);
                query.setLong(3, actor);
                query.setLong(4, user);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement("DELETE FROM auth_user_roles WHERE user_id=? AND scope_id=?")
            ) {
                query.setLong(1, user);
                query.setLong(2, scope);
                query.executeUpdate();
            }
            try (
                var query = connection.prepareStatement(
                    "INSERT INTO auth_user_roles(user_id,role_code,scope_id,active,version,created_by,updated_by) SELECT ?,code,?,?,?,?,? FROM unnest(?::text[]) code"
                )
            ) {
                query.setLong(1, user);
                query.setLong(2, scope);
                query.setBoolean(3, body.active());
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

    public List<DataScopeResponse> mine(long user) {
        return read(connection -> {
            var result = new ArrayList<DataScopeResponse>();
            try (
                var query = connection.prepareStatement(
                    "SELECT " +
                        COLUMNS +
                        " FROM carriers c WHERE c.scope_active AND EXISTS(SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted JOIN auth_users u ON u.id=ur.user_id AND u.active AND NOT u.deleted AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL WHERE ur.user_id=? AND ur.scope_id=c.scope_id AND ur.active) ORDER BY c.scope_code LIMIT 32"
                )
            ) {
                query.setLong(1, user);
                try (var rows = query.executeQuery()) {
                    while (rows.next()) result.add(scope(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public Set<Permission> permissions(long user, long tokenVersion, long scope) {
        return read(connection -> {
            try (
                var query = connection.prepareStatement(
                    """
                    SELECT ARRAY(SELECT DISTINCT op.permission_code FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted JOIN (
                    """ +
                        FunctionSql.ROLE_OPERATIONS +
                        """
                        ) op ON op.role_code=r.code WHERE ur.user_id=u.id AND ur.active AND (ur.scope_id=c.scope_id OR ur.scope_id=0 AND r.code='ADMIN') ORDER BY op.permission_code) AS permission_codes
                        FROM auth_users u JOIN carriers c ON c.scope_id=? AND c.scope_active WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.deleted AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
                          AND EXISTS(SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted WHERE ur.user_id=u.id AND ur.active AND (ur.scope_id=c.scope_id OR ur.scope_id=0 AND r.code='ADMIN'))
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
        for (int i = 3; i <= 5; i++) query.setString(i, term);
    }

    private static void bindMember(PreparedStatement query, long scope, ScopeMemberSearchRequest search)
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
            AccountAccessSql.strings(row, "role_codes").stream().map(Role::valueOf).collect(Collectors.toSet()),
            row.getLong("version"),
            row.getTimestamp("updated_at").toInstant(),
            row.getLong("updated_by")
        );
    }
}
