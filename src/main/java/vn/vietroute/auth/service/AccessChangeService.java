package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.vietroute.auth.dto.request.AccessChangeSearchRequest;
import vn.vietroute.auth.dto.response.AccessChangeResponse;
import vn.vietroute.auth.dto.response.AccessChangeStateResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.repository.AccessChangeRepository;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ApplicationConstants.Http;

/** Điều phối lịch sử runtime qua app, không suy ngược/backfill các lần sửa DB trước migration này. */
@Service
@RequiredArgsConstructor
public class AccessChangeService {

    private final AccessChangeRepository changes;

    /** Dữ liệu trước–sau nhạy cảm chỉ ADMIN có ACCESS_READ; delegated role không được đọc qua URL trực tiếp. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ, adminOnly = true)
    public PageResponse<AccessChangeResponse> page(AccessChangeSearchRequest search) {
        return changes.page(search);
    }

    /** Callback dùng connection của mutation; không tạo transaction riêng hoặc nuốt lỗi journal. */
    public void record(
        Connection connection,
        Event event,
        Target kind,
        String key,
        long actor,
        AccessChangeStateResponse before,
        AccessChangeStateResponse after
    ) throws SQLException {
        String trace = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            Object value = attributes.getRequest().getAttribute(Http.TRACE_ATTRIBUTE);
            if (value instanceof String id) trace = id;
        }
        changes.append(connection, event, kind, key, actor, trace, before, after);
    }

    /** Lưu đầy đủ mã bị bỏ/thêm, đếm tổng mapping; không nhân bản phần không đổi của tập quyền lớn. */
    public void resources(
        Connection connection,
        Event event,
        Target kind,
        String key,
        long actor,
        Set<String> before,
        Set<String> after,
        long versionBefore,
        long versionAfter
    ) throws SQLException {
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        record(
            connection,
            event,
            kind,
            key,
            actor,
            AccessChangeStateResponse.resources(removed, before.size(), versionBefore),
            AccessChangeStateResponse.resources(added, after.size(), versionAfter)
        );
    }

    /** Lưu đầy đủ mã bị bỏ/thêm, đếm tổng mapping; không nhân bản phần không đổi của tập quyền lớn. */
    public void mappings(
        Connection connection,
        Event event,
        Target kind,
        String key,
        long actor,
        Set<String> before,
        Set<String> after,
        long versionBefore,
        long versionAfter
    ) throws SQLException {
        Set<String> removed = new LinkedHashSet<>(before);
        removed.removeAll(after);
        Set<String> added = new LinkedHashSet<>(after);
        added.removeAll(before);
        boolean roles = event == Event.USER_ROLES;
        record(
            connection,
            event,
            kind,
            key,
            actor,
            AccessChangeStateResponse.mapping(removed, before.size(), versionBefore, roles),
            AccessChangeStateResponse.mapping(added, after.size(), versionAfter, roles)
        );
    }
}
