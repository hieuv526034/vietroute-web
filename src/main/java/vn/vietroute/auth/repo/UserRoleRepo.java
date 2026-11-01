package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import java.sql.Array;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.UserRole;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Repository bảng UserRole; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class UserRoleRepo extends JpaRepositorySupport<UserRole, Long> {

    public UserRoleRepo(EntityManager manager, JdbcTransactions transactions, AppSettings settings) {
        super(UserRole.class, manager, transactions);
        scopeCapacity = settings.getScopeCapacity();
    }

    public static final String VISIBLE = """
    EXISTS(SELECT 1 FROM auth_users su JOIN carriers security_owner ON security_owner.scope_id=owner.scope_id AND security_owner.scope_active
      WHERE su.id=? AND su.token_version=? AND su.active AND NOT su.deleted AND NOT su.activation_pending
        AND NOT su.must_change_password AND su.email_verified_at IS NOT NULL
        AND EXISTS(SELECT 1 FROM auth_user_roles global_role JOIN auth_roles r ON r.code=global_role.role_code AND r.active AND NOT r.deleted
          JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
          JOIN auth_function_resources fr ON fr.function_code=f.code
          WHERE global_role.user_id=su.id AND global_role.scope_id=0 AND global_role.active AND fr.target=?
            AND (r.all_scopes OR EXISTS(SELECT 1 FROM auth_user_roles scoped_role WHERE scoped_role.user_id=su.id
              AND scoped_role.role_code=r.code AND scoped_role.scope_id=security_owner.scope_id AND scoped_role.active))))
    """;
    public static final String REQUIRE = "SELECT " + VISIBLE + " FROM carriers owner WHERE owner.scope_id=?";
    public static final String GLOBAL = """
    SELECT 1 FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.scope_id=0 AND ur.active
    JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted AND r.all_scopes
    JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    JOIN auth_function_resources fr ON fr.function_code=f.code WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.deleted
    AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL AND fr.target=?
    """;

    private final int scopeCapacity;

    public List<String> roles() {
        return read(connection -> {
            List<String> roles = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT code FROM auth_roles WHERE active AND NOT deleted ORDER BY code LIMIT " + (2048 + 1)
                );
                ResultSet rows = query.executeQuery()
            ) {
                while (rows.next()) {
                    roles.add(rows.getString(1));
                }
            }
            if (roles.size() > 2048) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 2048);
            return List.copyOf(roles);
        });
    }

    /** Tất cả mã role cần tồn tại/active, kiểm một statement cho cả tập. */
    public void validateMappings(Connection connection, Set<String> roles, Set<String> unused) throws SQLException {
        Array codes = connection.createArrayOf("text", roles.stream().toArray(String[]::new));
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT count(*) FROM auth_roles WHERE code=ANY(?) AND active AND NOT deleted"
            )
        ) {
            query.setArray(1, codes);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                if (row.getLong(1) != roles.size()) throw new BusinessException(ErrorCode.INVALID_REQUEST);
            }
        } finally {
            codes.free();
        }
    }

    /** Khóa transaction chung cho sửa role user, giữ invariant luôn có ít nhất một admin hoạt động. */
    public void lockRoleAssignments(Connection connection) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
    }

    /** Snapshot mapping thô cho audit, gồm role đang tắt để việc gỡ không bị mất dấu trong lịch sử. */
    public Set<String> assignedRoles(Connection connection, long id) throws SQLException {
        Set<String> result = new LinkedHashSet<>();
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT role_code FROM auth_user_roles WHERE user_id=? AND scope_id=0 ORDER BY role_code"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(rows.getString(1));
            }
        }
        return Set.copyOf(result);
    }

    public void replaceRoles(Connection connection, long id, Set<String> roles, long version, long actor)
        throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "UPDATE auth_users SET version=version+1,token_version=token_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE id=? AND version=? AND NOT deleted"
            )
        ) {
            query.setLong(1, actor);
            query.setLong(2, id);
            query.setLong(3, version);
            if (query.executeUpdate() != 1) throw new BusinessException(ErrorCode.STALE_VERSION);
        }
        Array codes = connection.createArrayOf(
            "text",
            roles
                .stream()

                .toArray(String[]::new)
        );
        try {
            // Lưu mốc thu hồi trước khi FK cascade xóa scoped role cuối; gán lại không nhận version cũ.
            try (
                PreparedStatement query = connection.prepareStatement(
                    "UPDATE auth_users u SET scope_membership_versions=u.scope_membership_versions||v.versions FROM (SELECT jsonb_object_agg(scope_id::text,next_version) AS versions FROM (SELECT scope_id,GREATEST(max(version),COALESCE((SELECT (scope_membership_versions->>ur.scope_id::text)::bigint FROM auth_users WHERE id=?),0))+1 AS next_version FROM auth_user_roles ur WHERE user_id=? AND scope_id>0 GROUP BY scope_id HAVING bool_or(NOT(role_code=ANY(?)))) affected) v WHERE u.id=? AND v.versions IS NOT NULL"
                )
            ) {
                query.setLong(1, id);
                query.setLong(2, id);
                query.setArray(3, codes);
                query.setLong(4, id);
                query.executeUpdate();
            }
            try (
                PreparedStatement query = connection.prepareStatement(
                    "UPDATE auth_user_roles ur SET version=version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE ur.user_id=? AND ur.scope_id>0 AND EXISTS(SELECT 1 FROM auth_user_roles affected WHERE affected.user_id=ur.user_id AND affected.scope_id=ur.scope_id AND NOT(affected.role_code=ANY(?)))"
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
                    "INSERT INTO auth_user_roles(user_id,role_code,created_by,updated_by) SELECT ?,code,?,? FROM unnest(?::text[]) code ON CONFLICT DO NOTHING"
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

    public boolean protectedUser(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code WHERE ur.user_id=? AND ur.scope_id=0 AND r.protected_role LIMIT 1"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet rows = query.executeQuery()) {
                return rows.next();
            }
        }
    }

    /** Đọc cả role đang tắt để không giấu quyền mạnh rồi kích hoạt sau khi gán. */
    public void requireDelegableRoles(Connection connection, Set<String> roles) throws SQLException {
        Array codes = connection.createArrayOf("text", roles.toArray(String[]::new));
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT r.protected_role,r.all_scopes,fr.target FROM auth_roles r LEFT JOIN auth_role_functions rf ON rf.role_code=r.code LEFT JOIN auth_function_resources fr ON fr.function_code=rf.function_code WHERE r.code=ANY(?)"
            )
        ) {
            query.setArray(1, codes);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    if (rows.getBoolean(1)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    String target = rows.getString(3);
                    if (target != null) {
                        UserPrincipal.requireDelegable(Set.of(target));
                        if (
                            rows.getBoolean(2) && !UserPrincipal.current().getGlobalTargets().contains(target)
                        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    }
                }
            }
        } finally {
            codes.free();
        }
    }

    public boolean usesRole(Connection connection, String code) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM auth_user_roles WHERE role_code=? LIMIT 1"
            )
        ) {
            query.setString(1, code);
            try (ResultSet row = query.executeQuery()) {
                return row.next();
            }
        }
    }

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

    /** Tìm/đếm/phân trang nhà xe ngay tại DB, không phân trang lịch sử trong RAM. */
    public PageResponse<RoleDTO> page(RoleSearch search) {
        return read(connection -> {
            String where =
                " FROM carriers WHERE (?::boolean IS NULL OR scope_active=?) AND (scope_code ILIKE ? ESCAPE '!' OR scope_name_vi ILIKE ? ESCAPE '!' OR scope_name_en ILIKE ? ESCAPE '!')";
            long total;
            try (PreparedStatement query = connection.prepareStatement("SELECT count(*)" + where)) {
                bind(query, search);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT " + COLUMNS + where + " ORDER BY scope_code LIMIT ? OFFSET ?"
                )
            ) {
                bind(query, search);
                query.setInt(6, search.getSize());
                query.setLong(7, search.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) result.add(scope(rows));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    public RoleDTO find(Connection connection, long id) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                "SELECT " + COLUMNS + " FROM carriers WHERE scope_id=?"
            )
        ) {
            query.setLong(1, id);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? scope(row) : null;
            }
        }
    }

    /** Khóa quản trị do caller giữ; hạn mức áp dụng cả nhà xe tắt và ID không bị tái sử dụng. */
    public long allocate(Connection connection) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement("SELECT count(*) FROM carriers");
            ResultSet row = query.executeQuery()
        ) {
            row.next();
            if (row.getLong(1) >= scopeCapacity) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                scopeCapacity
            );
        }
        try (
            PreparedStatement query = connection.prepareStatement("SELECT nextval('carrier_scope_ids')");
            ResultSet row = query.executeQuery()
        ) {
            row.next();
            return row.getLong(1);
        }
    }

    /** Chỉ sửa phạm vi của nhà xe có thật; tạo nhà xe qua CarrierService. */
    public long save(Connection connection, long id, RoleRequest body, long actor) throws SQLException {
        if (id <= 0) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        try (
            PreparedStatement query = connection.prepareStatement(
                "UPDATE carriers SET scope_name_vi=?,scope_name_en=?,scope_active=?,scope_version=scope_version+1,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE scope_id=? AND scope_version=? RETURNING scope_id"
            )
        ) {
            query.setString(1, body.getNameVi().strip());
            query.setString(2, body.getNameEn().strip());
            query.setBoolean(3, Boolean.TRUE.equals(body.getActive()));
            query.setLong(4, actor);
            query.setLong(5, id);
            query.setLong(6, body.getVersion());
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.STALE_VERSION);
                return row.getLong(1);
            }
        }
    }

    public RoleDTO member(Connection connection, long scope, long user) throws SQLException {
        try (
            PreparedStatement query = connection.prepareStatement(
                MEMBERS + " WHERE m.scope_id=? AND m.user_id=? GROUP BY m.scope_id,m.user_id,u.id"
            )
        ) {
            query.setLong(1, scope);
            query.setLong(2, user);
            try (ResultSet row = query.executeQuery()) {
                return row.next() ? member(row) : null;
            }
        }
    }

    public static List<RoleDTO> memberships(Connection connection, long user) throws SQLException {
        ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
        try (
            PreparedStatement query = connection.prepareStatement(
                MEMBERS + " WHERE m.user_id=? GROUP BY m.scope_id,m.user_id,u.id ORDER BY m.scope_id LIMIT 33"
            )
        ) {
            query.setLong(1, user);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) result.add(member(rows));
            }
        }
        if (result.size() > 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
        return List.copyOf(result);
    }

    public PageResponse<RoleDTO> members(long scope, UserSearch search) {
        return read(connection -> {
            long total;
            try (
                PreparedStatement query = connection.prepareStatement(
                    MEMBER_KEYS +
                        "SELECT count(*) FROM membership_keys WHERE scope_id=? AND (?::bigint IS NULL OR user_id=?)"
                )
            ) {
                bindMember(query, scope, search);
                try (ResultSet row = query.executeQuery()) {
                    row.next();
                    total = row.getLong(1);
                }
            }
            ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    MEMBERS +
                        " WHERE m.scope_id=? AND (?::bigint IS NULL OR m.user_id=?) GROUP BY m.scope_id,m.user_id,u.id ORDER BY m.user_id LIMIT ? OFFSET ?"
                )
            ) {
                bindMember(query, scope, search);
                query.setInt(4, search.getSize());
                query.setLong(5, search.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) result.add(member(rows));
                }
            }
            return PageResponse.of(result, search, total);
        });
    }

    /** CAS dưới khóa quản trị; thay toàn bộ tập bằng INSERT SELECT, giữ FK scoped là tập con global. */
    public void assign(Connection connection, long scope, long user, UserRequest body, long actor) throws SQLException {
        RoleDTO before = member(connection, scope, user);
        long version = before == null ? 0 : before.getVersion();
        if (body.getVersion() != version) throw new BusinessException(ErrorCode.STALE_VERSION);
        if (before == null) try (
            PreparedStatement query = connection.prepareStatement(
                MEMBER_KEYS + "SELECT count(*) FROM membership_keys WHERE user_id=?"
            )
        ) {
            query.setLong(1, user);
            try (ResultSet row = query.executeQuery()) {
                row.next();
                if (row.getLong(1) >= 32) throw new BusinessException(ErrorCode.ACCESS_CAPACITY_REACHED, 32);
            }
        }
        Array codes = connection.createArrayOf(
            "text",
            body
                .getRoles()
                .stream()

                .toArray(String[]::new)
        );
        try {
            // Giữ mốc version kể cả tập role rỗng; advisory lock chung bảo vệ CAS với gán role toàn cục.
            try (
                PreparedStatement query = connection.prepareStatement(
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
                    "INSERT INTO auth_user_roles(user_id,role_code,scope_id,active,version,created_by,updated_by) SELECT ?,code,?,?,?,?,? FROM unnest(?::text[]) code"
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

    public List<RoleDTO> mine(long user) {
        return read(connection -> {
            ArrayList<RoleDTO> result = new ArrayList<RoleDTO>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT " +
                        COLUMNS +
                        " FROM carriers c WHERE c.scope_active AND EXISTS(SELECT 1 FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted JOIN auth_users u ON u.id=ur.user_id AND u.active AND NOT u.deleted AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL WHERE ur.user_id=? AND ur.scope_id=c.scope_id AND ur.active) ORDER BY c.scope_code LIMIT 32"
                )
            ) {
                query.setLong(1, user);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) result.add(scope(rows));
                }
            }
            return List.copyOf(result);
        });
    }

    public Set<String> targets(long user, long tokenVersion, long scope) {
        return read(connection -> {
            try (
                PreparedStatement query = connection.prepareStatement(
                    """
                    SELECT ARRAY(SELECT DISTINCT fr.target FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
                      JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
                      JOIN auth_function_resources fr ON fr.function_code=f.code
                      WHERE ur.user_id=u.id AND ur.scope_id=0 AND ur.active
                        AND (r.all_scopes OR EXISTS(SELECT 1 FROM auth_user_roles sr WHERE sr.user_id=u.id AND sr.role_code=r.code AND sr.scope_id=c.scope_id AND sr.active))) AS resource_targets
                    FROM auth_users u JOIN carriers c ON c.scope_id=? AND c.scope_active WHERE u.id=? AND u.token_version=? AND u.active AND NOT u.deleted
                      AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
                    """
                )
            ) {
                query.setLong(1, scope);
                query.setLong(2, user);
                query.setLong(3, tokenVersion);
                try (ResultSet row = query.executeQuery()) {
                    if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                    return UserRepo.targets(row);
                }
            }
        });
    }

    private static void bind(PreparedStatement query, RoleSearch search) throws SQLException {
        query.setObject(1, search.getActive(), Types.BOOLEAN);
        query.setObject(2, search.getActive(), Types.BOOLEAN);
        String term = "%" + search.getQuery().strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        for (int i = 3; i <= 5; i++) query.setString(i, term);
    }

    private static void bindMember(PreparedStatement query, long scope, UserSearch search) throws SQLException {
        query.setLong(1, scope);
        query.setObject(2, search.getUserId(), Types.BIGINT);
        query.setObject(3, search.getUserId(), Types.BIGINT);
    }

    private static RoleDTO scope(ResultSet row) throws SQLException {
        RoleDTO dto = new RoleDTO();
        dto.setId(row.getLong("id"));
        dto.setScopeId(row.getLong("id"));
        dto.setCode(row.getString("code"));
        dto.setNameVi(row.getString("name_vi"));
        dto.setNameEn(row.getString("name_en"));
        dto.setActive(row.getBoolean("active"));
        dto.setVersion(row.getLong("version"));
        dto.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        dto.setUpdatedBy(row.getLong("updated_by"));
        return dto;
    }

    private static RoleDTO member(ResultSet row) throws SQLException {
        RoleDTO dto = new RoleDTO();
        dto.setScopeId(row.getLong("scope_id"));
        dto.setUserId(row.getLong("user_id"));
        dto.setUsername(row.getString("username"));
        dto.setRoles(UserRepo.strings(row, "role_codes"));
        dto.setActive(row.getBoolean("active"));
        dto.setVersion(row.getLong("version"));
        dto.setUpdatedAt(row.getTimestamp("updated_at").toInstant());
        dto.setUpdatedBy(row.getLong("updated_by"));
        return dto;
    }

    public static String visibleSql() {
        return VISIBLE;
    }

    public static int bindVisible(PreparedStatement query, int start) throws SQLException {
        return bindVisible(query, start, UserPrincipal.current().getTarget());
    }

    /** Cột khả năng thao tác kiểm endpoint đích riêng, không dùng quyền GET của trang đang xem. */
    public static int bindVisible(PreparedStatement query, int start, String target) throws SQLException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (
            auth == null ||
            !(auth.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id() == null ||
            actor.id() <= 0
        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        if (target == null) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        query.setLong(start++, actor.id());
        query.setLong(start++, actor.tokenVersion());
        query.setString(start++, target);
        return start;
    }

    /** Kiểm trạng thái hiện hành trên connection caller, không dùng quyền/phạm vi JWT làm nguồn sự thật. */
    public void require(Connection connection, long scopeId) throws SQLException {
        try (PreparedStatement query = connection.prepareStatement(REQUIRE)) {
            int next = bindVisible(query, 1);
            query.setLong(next, scopeId);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next() || !row.getBoolean(1)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    public static void requireGlobal(Connection connection, UserPrincipal actor) throws SQLException {
        UserPrincipal.requireCurrent(actor);
        try (PreparedStatement query = connection.prepareStatement(GLOBAL)) {
            bindVisible(query, 1);
            try (ResultSet row = query.executeQuery()) {
                if (!row.next()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
    }

    public static void requireSameScope(long parentScope, long childScope) {
        if (parentScope < 1 || parentScope != childScope) throw new BusinessException(ErrorCode.ACCESS_DENIED);
    }

    /** ID scope kỹ thuật được cấp trong transaction tạo Carrier; chưa tồn tại Carrier thì không cấp membership. */
    public long create(Connection connection, String name, long actor) throws SQLException {
        UserPrincipal current = UserPrincipal.current();
        BusinessRules.require(current.id() == actor, ErrorCode.ACCESS_DENIED);
        requireGlobal(connection, current);
        lockRoleAssignments(connection);
        return allocate(connection);
    }
}
