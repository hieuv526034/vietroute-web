package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.AccessResourceSearchRequest;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.repository.AccessFeatureRepository;
import vn.vietroute.auth.repository.AccessResourceRepository;
import vn.vietroute.auth.repository.AdminRecoveryRepository;
import vn.vietroute.auth.security.AccessPermissionDefinitions;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Đồng bộ định nghĩa server đã validate; chỉ ADMIN nhận quyền lõi mới, không mở route từ public flag trong DB. */
@Service
@RequiredArgsConstructor
public class AccessResourceService {

    private final AccessResourceRepository resources;

    private final JdbcTransactions transactions;

    private final AccessFeatureRepository features;

    private final BootstrapAdminInitializer bootstrap;

    private final AdminRecoveryRepository recovery;

    private final AccessPermissionDefinitions permissions;

    private volatile Definition definition;

    /** Chỉ collector servlet công bố metadata server đã validate, kể cả khi tắt ghi startup. Không chứa quyền user. */
    public void remember(List<AccessResourceResponse> inventory, Set<String> serviceCodes) {
        permissions.remember(inventory, serviceCodes);
        definition = new Definition(List.copyOf(inventory), Set.copyOf(serviceCodes));
    }

    /** Chỉ lifecycle server gọi; dữ liệu được collector tạo từ mapping thật, không có API nhận danh sách route. */
    public void synchronize(List<AccessResourceResponse> inventory, Set<String> serviceCodes) {
        transactions.execute(connection -> {
            bootstrap.initialize(connection, SystemActor.ID);
            features.synchronize(connection, inventory, serviceCodes);
            resources.synchronize(connection, inventory);
            recovery.grantAll(connection, SystemActor.ID);
            return null;
        });
    }

    /** Khôi phục thủ công dùng đúng định nghĩa của node, trong transaction lịch sử do caller sở hữu. */
    public void restore(Connection connection, long actor) throws SQLException {
        var current = definition;
        if (current == null) throw new IllegalStateException("Danh bạ endpoint chưa khởi tạo");
        bootstrap.initialize(connection, actor);
        features.synchronize(connection, current.inventory(), current.serviceCodes());
        resources.synchronize(connection, current.inventory());
        recovery.grantAll(connection, actor);
    }

    private record Definition(List<AccessResourceResponse> inventory, Set<String> serviceCodes) {}

    /** Quyền đặt tại service để lời gọi ngoài controller cũng không đọc được danh bạ quản trị. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public PageResponse<AccessResourceResponse> page(AccessResourceSearchRequest page) {
        return resources.page(page);
    }
}
