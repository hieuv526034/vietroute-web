package vn.vietroute.auth.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.AccessResourceSearchRequest;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.repository.AccessFeatureRepository;
import vn.vietroute.auth.repository.AccessResourceRepository;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Đồng bộ định nghĩa server đã validate; chỉ ADMIN nhận quyền lõi mới, không mở route từ public flag trong DB. */
@Service
@RequiredArgsConstructor
public class AccessResourceService {

    private final AccessResourceRepository resources;

    private final JdbcTransactions transactions;

    private final AccessFeatureRepository features;

    /** Chỉ lifecycle server gọi; dữ liệu được collector tạo từ mapping thật, không có API nhận danh sách route. */
    public void synchronize(List<AccessResourceResponse> inventory) {
        transactions.execute(connection -> {
            features.synchronize(connection, inventory);
            resources.synchronize(connection, inventory);
            return null;
        });
    }

    /** Quyền đặt tại service để lời gọi ngoài controller cũng không đọc được danh bạ quản trị. */
    @PreAuthorize(AccessRules.ACCESS_READ)
    public PageResponse<AccessResourceResponse> page(AccessResourceSearchRequest page) {
        return resources.page(page);
    }
}
