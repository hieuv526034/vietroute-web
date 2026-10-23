package vn.vietroute.auth.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.repository.AccessResourceRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Danh bạ mapping khác với role/policy: không tự gán quyền và không dùng public flag trong DB để mở route. */
@Service
@RequiredArgsConstructor
public class AccessResourceService {

    private final AccessResourceRepository resources;

    private final JdbcTransactions transactions;

    /** Chỉ lifecycle server gọi; dữ liệu được collector tạo từ mapping thật, không có API nhận danh sách route. */
    public void synchronize(List<AccessResourceResponse> inventory) {
        transactions.execute(connection -> {
            resources.synchronize(connection, inventory);
            return null;
        });
    }

    /** Quyền đặt tại service để lời gọi ngoài controller cũng không đọc được danh bạ quản trị. */
    @PreAuthorize("hasRole('ADMIN') and hasAuthority('ACCESS_READ')")
    public PageResponse<AccessResourceResponse> page(PageRequest page) {
        return resources.page(page);
    }
}
