package vn.vietroute.business.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.demo.repo.DemoSeedRepo;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ seed khi chủ động gọi; không constructor/startup/build tự tạo dữ liệu demo. */
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    private final DemoSeedRepo seeds;
    private final JdbcTransactions transactions;

    /** Một giao dịch all-or-nothing, đúng endpoint init và phạm vi global hiện hành, actor không nhận từ client. */
    public DemoSeedResponse seed(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            UserRoleRepo.requireGlobal(connection, actor);
            return seeds.seed(connection, actor.id());
        });
    }
}
