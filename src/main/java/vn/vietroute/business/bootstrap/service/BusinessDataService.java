package vn.vietroute.business.bootstrap.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.bootstrap.dto.response.BusinessDataResponse;
import vn.vietroute.business.bootstrap.repo.custom.BusinessDataCustomRepo;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Chỉ seed khi chủ động gọi; không constructor/startup/build tự tạo dữ liệu demo. */
@Service
@RequiredArgsConstructor
public class BusinessDataService {

    private final BusinessDataCustomRepo seeds;
    private final JdbcTransactions transactions;

    private final UserRoleRepo mappings;

    /** Một giao dịch all-or-nothing, đúng endpoint init và phạm vi global hiện hành, actor không nhận từ client. */
    public BusinessDataResponse seed(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            mappings.requireGlobal(actor);
            return seeds.seed(connection, actor.id());
        });
    }
}
