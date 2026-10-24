package vn.vietroute.common.monitoring.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;

/** Nhật ký toàn DB chỉ ADMIN có SYSTEM_READ; không cho role nghiệp vụ xem dữ liệu chéo người dùng. */
@Service
@RequiredArgsConstructor
public class ApiJournalService {

    private final ApiJournalStore store;

    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.READ, adminOnly = true)
    public ApiJournalResponse page(ApiJournalSearchRequest search) {
        return store.page(search);
    }

    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.READ, adminOnly = true)
    public ApiJournalDetail find(UUID id) {
        ApiJournalDetail result = store.find(id);
        if (result == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return result;
    }
}
