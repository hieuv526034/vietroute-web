package vn.vietroute.business.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.demo.repository.DemoSeedRepository;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ seed khi chủ động gọi; không constructor/startup/build tự tạo dữ liệu demo. */
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    private final DemoSeedRepository seeds;
    private final JdbcTransactions transactions;

    /** Một giao dịch all-or-nothing, đúng quyền CREATE của từng module và ADMIN hiện hành, actor không nhận từ client. */
    @AccessOperation(
        resource = Groups.DEMO,
        action = PermissionAction.CREATE,
        adminOnly = true,
        required = {
            Groups.CARRIER + "_CREATE",
            Groups.VEHICLE + "_CREATE",
            Groups.DRIVER + "_CREATE",
            Groups.PROVINCE + "_CREATE",
            Groups.LOCATION + "_CREATE",
            Groups.VEHICLE_TYPE + "_CREATE",
            Groups.BOOKING_SOURCE + "_CREATE",
        }
    )
    public DemoSeedResponse seed(UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            DataScopeGuard.requireAdmin(connection, actor);
            return seeds.seed(connection, actor.id());
        });
    }
}
