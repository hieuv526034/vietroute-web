package vn.vietroute.auth.repo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Repository;

/** Seed đúng ba danh tính đã được chủ dự án cho phép, không ghi đè credential/account đã tồn tại. */
@Repository
public class DefaultAccountsRepository {

    @Getter
    @AllArgsConstructor
    public static class NewAccount {

        private final String username;
        private final String displayName;
        private final String role;
        private final String email;
        private final String passwordHash;
    }

    /** Khóa cùng quản trị/gán role; một SELECT cho toàn bộ danh tính thiếu. */
    public List<String> missing(Connection connection) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
        var result = new ArrayList<String>();
        try (
            var query = connection.prepareStatement(
                "SELECT wanted FROM unnest(ARRAY['custom01','driver01','operator01']) wanted WHERE NOT EXISTS(SELECT 1 FROM auth_users u WHERE u.username=wanted)"
            );
            var rows = query.executeQuery()
        ) {
            while (rows.next()) result.add(rows.getString(1));
        }
        return List.copyOf(result);
    }

    /** Batch user và mapping cùng transaction; email cấp sẵn/active chỉ cho ba account init được chốt. */
    public void insert(Connection connection, List<NewAccount> accounts) throws SQLException {
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_users(username,display_name,email,password_hash,email_verified_at,active,activation_pending,must_change_password,bootstrap_scope_pending) VALUES (?,?,?,?,CURRENT_TIMESTAMP,true,false,false,true)"
            )
        ) {
            for (var account : accounts) {
                query.setString(1, account.getUsername());
                query.setString(2, account.getDisplayName());
                query.setString(3, account.getEmail());
                query.setString(4, account.getPasswordHash());
                query.addBatch();
            }
            query.executeBatch();
        }
        try (
            var query = connection.prepareStatement(
                "INSERT INTO auth_user_roles(user_id,role_code) SELECT id,? FROM auth_users WHERE username=?"
            )
        ) {
            for (var account : accounts) {
                query.setString(1, account.getRole());
                query.setString(2, account.getUsername());
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    /** Chỉ account vừa init mới nhận scope mặc định một lần; không tự khôi phục mapping đã bị quản trị thu hồi. */
    public void assignInitialScopes(Connection connection) throws SQLException {
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO auth_user_roles(user_id,role_code,scope_id)
                SELECT u.id,ur.role_code,c.scope_id FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.scope_id=0
                JOIN carriers c ON c.code='VRMIENNAM' WHERE u.bootstrap_scope_pending
                    AND (u.username='operator01' AND ur.role_code='OPERATOR' OR u.username='driver01' AND ur.role_code='DRIVER')
                ON CONFLICT DO NOTHING
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                """
                UPDATE carrier_drivers d SET user_id=u.id,updated_at=CURRENT_TIMESTAMP,updated_by=0,version=d.version+1
                FROM auth_users u,carriers c WHERE u.username='driver01' AND u.bootstrap_scope_pending AND c.code='VRMIENNAM'
                    AND d.carrier_id=c.id AND d.code='DRVSOUTH01' AND d.user_id IS NULL
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_users SET bootstrap_scope_pending=false WHERE bootstrap_scope_pending AND (username='custom01' OR EXISTS(SELECT 1 FROM auth_user_roles ur WHERE ur.user_id=auth_users.id AND ur.scope_id>0))"
            )
        ) {
            query.executeUpdate();
        }
    }

    /** Mẫu chức năng chỉ gán lần đầu cho role hệ thống mới; không tự nhận API mới ở startup sau. */
    public void initializeFunctions(Connection connection) throws SQLException {
        try (var lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205611)")) {
            lock.execute();
        }
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO auth_functions(code,name_vi,name_en)
                SELECT 'DEFAULT_'||r.code,CASE r.code WHEN 'CUSTOMER' THEN 'Đặt chỗ của khách hàng' WHEN 'DRIVER' THEN 'Hành trình của tài xế' ELSE 'Điều hành nhà xe' END,
                    CASE r.code WHEN 'CUSTOMER' THEN 'Customer bookings' WHEN 'DRIVER' THEN 'Driver journeys' ELSE 'Carrier operations' END
                FROM auth_roles r WHERE r.code IN('OPERATOR','DRIVER','CUSTOMER') AND NOT r.functions_initialized AND NOT r.deleted
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO auth_function_resources(function_code,kind,target,handler,description,resource_group,feature_code,declared_permissions,permission_match,admin_only,public_route,configurable)
                SELECT 'DEFAULT_'||r.code,fr.kind,fr.target,fr.handler,fr.description,fr.resource_group,fr.feature_code,fr.declared_permissions,fr.permission_match,fr.admin_only,fr.public_route,fr.configurable
                FROM auth_roles r CROSS JOIN auth_functions f JOIN auth_function_resources fr ON fr.function_code=f.code
                WHERE r.code IN('OPERATOR','DRIVER','CUSTOMER') AND NOT r.functions_initialized AND NOT r.deleted
                  AND f.source_managed AND f.active AND NOT f.deleted AND NOT fr.public_route AND NOT fr.admin_only AND fr.declared_permissions<>''
                  AND ((r.code='CUSTOMER' AND fr.resource_group='MY_BOOKING')
                    OR (r.code='DRIVER' AND fr.resource_group='DRIVER_TRIP')
                    OR (r.code='OPERATOR' AND (fr.resource_group IN('VEHICLE','DRIVER','ROUTE','TRIP','BOOKING','REPORT')
                      OR fr.resource_group IN('CARRIER','VEHICLE_TYPE','BOOKING_SOURCE','PROVINCE','LOCATION') AND fr.target LIKE 'GET %')))
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                """
                UPDATE auth_functions f SET operation_codes=ARRAY(SELECT DISTINCT op FROM auth_function_resources fr
                    CROSS JOIN LATERAL unnest(string_to_array(fr.declared_permissions,',')) op
                    WHERE fr.function_code=f.code AND op<>'' AND right(op,6)<>'_WRITE' ORDER BY op)
                FROM auth_roles r WHERE f.code='DEFAULT_'||r.code AND r.code IN('OPERATOR','DRIVER','CUSTOMER') AND NOT r.functions_initialized AND NOT r.deleted
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                """
                INSERT INTO auth_role_functions(role_code,function_code,operation_codes)
                SELECT r.code,f.code,f.operation_codes FROM auth_roles r JOIN auth_functions f ON f.code='DEFAULT_'||r.code
                WHERE r.code IN('OPERATOR','DRIVER','CUSTOMER') AND NOT r.functions_initialized AND NOT r.deleted
                """
            )
        ) {
            query.executeUpdate();
        }
        try (
            var query = connection.prepareStatement(
                "UPDATE auth_roles SET functions_initialized=true WHERE code IN('OPERATOR','DRIVER','CUSTOMER') AND NOT functions_initialized"
            )
        ) {
            query.executeUpdate();
        }
    }
}
