package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.BaseRepository;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;

/** DAO danh bạ route; metadata public không thay WebRoutePolicy và mapping quyền của user. */
@Repository
public class AccessResourceRepository extends BaseRepository {

    public AccessResourceRepository(JdbcConnectionFactory connections) {
        super(connections);
    }

    /**
     * Đồng bộ một transaction startup; khóa chung giúp nhiều node không tranh chấp batch upsert.
     * Không xóa route vắng mặt: rolling deploy có thể còn node bản cũ, không được vô hiệu hóa thay nó.
     */
    public void synchronize(Connection connection, List<AccessResourceResponse> resources) throws SQLException {
        try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(781205616)")) {
            lock.execute();
        }
        try (
            PreparedStatement query = connection.prepareStatement(
                "INSERT INTO auth_access_resources(kind,target,handler,description,public_route,configurable,created_by,updated_by,resource_group,declared_permissions,function_code,permission_match,admin_only) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (kind,target) DO UPDATE SET handler=EXCLUDED.handler," +
                    "description=EXCLUDED.description,public_route=EXCLUDED.public_route,configurable=EXCLUDED.configurable," +
                    "function_code=EXCLUDED.function_code,permission_match=EXCLUDED.permission_match,admin_only=EXCLUDED.admin_only,resource_group=EXCLUDED.resource_group,declared_permissions=EXCLUDED.declared_permissions,last_seen_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP,updated_by=EXCLUDED.updated_by,version=auth_access_resources.version+1"
            )
        ) {
            for (AccessResourceResponse resource : resources) {
                query.setString(1, resource.kind());
                query.setString(2, resource.target());
                query.setString(3, resource.handler());
                query.setString(4, resource.description());
                query.setBoolean(5, resource.publicRoute());
                query.setBoolean(6, resource.configurable());
                query.setLong(7, SystemActor.ID);
                query.setLong(8, SystemActor.ID);
                query.setString(9, resource.group());
                query.setString(10, String.join(",", resource.declaredPermissions()));
                query.setString(11, resource.function().isEmpty() ? null : resource.function());
                query.setString(12, resource.match());
                query.setBoolean(13, resource.adminOnly());
                query.addBatch();
            }
            query.executeBatch();
        }
    }

    /** Phân trang trong DB; không tải toàn bộ danh bạ route vào RAM hoặc chấp nhận tên cột SQL từ client. */
    public PageResponse<AccessResourceResponse> page(PageRequest page) {
        return read(connection -> {
            long total;
            try (
                PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM auth_access_resources");
                ResultSet rows = count.executeQuery()
            ) {
                rows.next();
                total = rows.getLong(1);
            }
            List<AccessResourceResponse> result = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT kind,target,handler,description,public_route,configurable,first_seen_at,last_seen_at,resource_group,declared_permissions,function_code,permission_match,admin_only " +
                        "FROM auth_access_resources ORDER BY kind,target LIMIT ? OFFSET ?"
                )
            ) {
                query.setInt(1, page.getSize());
                query.setLong(2, page.offset());
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        result.add(
                            new AccessResourceResponse(
                                rows.getString(1),
                                rows.getString(2),
                                rows.getString(3),
                                rows.getString(4),
                                rows.getBoolean(5),
                                rows.getBoolean(6),
                                rows.getTimestamp(7).toInstant(),
                                rows.getTimestamp(8).toInstant(),
                                rows.getString(9),
                                rows.getString(10).isEmpty() ? List.of() : List.of(rows.getString(10).split(",")),
                                rows.getString(11) == null ? "" : rows.getString(11),
                                rows.getString(12),
                                rows.getBoolean(13)
                            )
                        );
                    }
                }
            }
            return PageResponse.of(result, page, total);
        });
    }
}
