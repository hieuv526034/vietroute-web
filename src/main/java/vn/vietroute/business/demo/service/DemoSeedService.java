package vn.vietroute.business.demo.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.demo.dto.response.DemoSeedResponse;
import vn.vietroute.business.demo.repository.DemoSeedRepository;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ seed khi chủ động gọi; không constructor/startup/build tự tạo dữ liệu demo. */
@Service
@RequiredArgsConstructor
public class DemoSeedService {

    private final DemoSeedRepository seeds;
    private final JdbcTransactions transactions;

    /** Một giao dịch all-or-nothing, đúng quyền CREATE của từng module và ADMIN hiện hành, actor không nhận từ client. */
    public DemoSeedResponse seed(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            UserRoleRepo.requireGlobal(connection, actor);
            return seeds.seed(connection, actor.id());
        });
    }
}
