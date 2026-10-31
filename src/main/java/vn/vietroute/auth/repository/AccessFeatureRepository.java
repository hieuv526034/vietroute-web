package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.record.capability.EffectiveAccessResponse;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.security.AccessFeatures;
import vn.vietroute.auth.security.AccessPermissionDefinitions;
import vn.vietroute.common.constant.AccessConstants.Limits;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** DAO metadata cây và nguồn cấp quyền; không cache lịch sử hay quyền giữa node. */
@Repository
public class AccessFeatureRepository extends BaseRepository {

    public AccessFeatureRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /** Gọi trong transaction sync startup: cùng khóa mapping để không chạy lọt thao tác gán quyền. */
    public void synchronize(Connection connection, List<AccessResourceResponse> inventory, Set<String> serviceCodes)
        throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
        Set<String> codes = AccessPermissionDefinitions.collect(inventory, serviceCodes);
        var vi = ResourceBundle.getBundle("i18n.messages", Locale.forLanguageTag("vi"));
        var en = ResourceBundle.getBundle("i18n.messages", Locale.ENGLISH);
        try (
            var insert = connection.prepareStatement(
                "INSERT INTO auth_permissions(code,name_vi,name_en,created_by,updated_by) VALUES (?,?,?,0,0) ON CONFLICT(code) DO UPDATE SET active=true,updated_at=CURRENT_TIMESTAMP,updated_by=0,registry_version=auth_permissions.registry_version+1 WHERE NOT auth_permissions.active"
            );
            var admin = connection.prepareStatement(
                "INSERT INTO auth_role_permissions(role_code,permission_code,created_by,updated_by) VALUES ('ADMIN',?,0,0) ON CONFLICT DO NOTHING"
            )
        ) {
            for (String code : codes) {
                Permission permission = Permission.valueOf(code);
                insert.setString(1, code);
                insert.setString(2, AccessPermissionDefinitions.localizedName(permission, vi));
                insert.setString(3, AccessPermissionDefinitions.localizedName(permission, en));
                insert.addBatch();
                admin.setString(1, code);
                admin.addBatch();
            }
            insert.executeBatch();
            admin.executeBatch();
        }
        try (
            var upsert = connection.prepareStatement(
                "INSERT INTO auth_access_functions(code,parent_code,name_key,menu_path,view_permission,sort_order) VALUES (?,?,?,?,?,?) ON CONFLICT(code) DO UPDATE SET parent_code=EXCLUDED.parent_code,name_key=EXCLUDED.name_key,menu_path=EXCLUDED.menu_path,view_permission=EXCLUDED.view_permission,sort_order=EXCLUDED.sort_order,updated_at=CURRENT_TIMESTAMP,updated_by=0,version=auth_access_functions.version+1"
            )
        ) {
            int order = 0;
            for (var feature : AccessFeatures.definitions()) {
                upsert.setString(1, feature.code());
                upsert.setString(2, feature.parent());
                upsert.setString(3, feature.nameKey());
                upsert.setString(4, feature.path());
                upsert.setString(5, feature.viewPermission());
                upsert.setInt(6, order++);
                upsert.addBatch();
            }
            upsert.executeBatch();
        }
    }

    /** Tối đa số permission registry; gom nguồn tại SQL, không tải toàn bộ tài khoản để suy ra quyền. */
    public EffectiveAccessResponse effective(long id) {
        return read(connection -> {
            String username;
            boolean active;
            boolean usable;
            try (
                var user = connection.prepareStatement(
                    "SELECT username,active,(active AND NOT activation_pending AND NOT must_change_password AND email_verified_at IS NOT NULL) AS usable FROM auth_users WHERE id=? AND id<>0"
                )
            ) {
                user.setLong(1, id);
                try (var rows = user.executeQuery()) {
                    if (!rows.next()) throw new BusinessException(ErrorCode.NOT_FOUND);
                    username = rows.getString(1);
                    active = rows.getBoolean(2);
                    usable = rows.getBoolean(3);
                }
            }
            Map<String, Set<String>> sources = new LinkedHashMap<>();
            try (
                var query = connection.prepareStatement(
                    "SELECT p.code,string_agg(DISTINCT r.code,',' ORDER BY r.code) FROM auth_user_roles ur JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted JOIN auth_role_permissions rp ON rp.role_code=r.code JOIN auth_permissions p ON p.code=rp.permission_code AND p.active WHERE ur.user_id=? GROUP BY p.code ORDER BY p.code LIMIT " +
                        (Limits.REGISTRY_MAX + 1)
                )
            ) {
                query.setLong(1, id);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) sources.put(rows.getString(1), Set.of(rows.getString(2).split(",")));
                }
            }
            if (sources.size() > Limits.REGISTRY_MAX) throw new BusinessException(
                ErrorCode.ACCESS_CAPACITY_REACHED,
                Limits.REGISTRY_MAX
            );
            // Mapping vẫn còn để quản trị sửa, nhưng không được mô tả là quyền hiệu lực khi user chưa sẵn sàng.
            return new EffectiveAccessResponse(id, username, active, usable ? sources : Map.of());
        });
    }
}
