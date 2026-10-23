package vn.vietroute.common.monitoring.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;

/** Nhật ký rộng toàn node chỉ ADMIN có SYSTEM_READ; không cho role nghiệp vụ xem dữ liệu chéo người dùng. */
@Service
@RequiredArgsConstructor
public class ApiJournalService {

    private final ApiJournalStore store;

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('SYSTEM_READ')")
    public ApiJournalResponse page(ApiJournalSearchRequest search) {
        return store.page(search);
    }

    @PreAuthorize("hasRole('ADMIN') and hasAuthority('SYSTEM_READ')")
    public ApiJournalDetail find(UUID id) {
        ApiJournalDetail result = store.find(id);
        if (result == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return result;
    }
}
