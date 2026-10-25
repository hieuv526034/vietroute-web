package vn.vietroute.auth.repository;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Repository;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.vietroute.auth.dto.request.DataScopeRequest;
import vn.vietroute.auth.dto.response.AccessChangeStateResponse;
import vn.vietroute.auth.dto.response.AccessRegistryResponse;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.DataScopeGuard;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Callback persistence nội bộ: nghiệp vụ tạo scope qua auth, dùng cùng connection/quota/khóa/lịch sử. */
@Repository
@RequiredArgsConstructor
public class BusinessScopeRepository {

    private final DataScopeRepository scopes;
    private final AccessRepository access;
    private final AccessChangeRepository changes;

    /** Caller đã kiểm CREATE nghiệp vụ; callback vẫn kiểm DB ADMIN, không mở API cấp scope/quyền mới. */
    public long create(Connection connection, String name, long actorId) throws SQLException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (
            authentication == null ||
            !(authentication.getPrincipal() instanceof UserPrincipal actor) ||
            actor.id() == null ||
            actor.id() != actorId
        ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        AccessActors.requireCurrent(actor);
        DataScopeGuard.requireAdmin(connection, actor);
        access.lockRoleAssignments(connection);
        String code = "CARRIER_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        long id = scopes.save(connection, 0, new DataScopeRequest(code, name.strip(), name.strip(), true, 0), actorId);
        var created = scopes.find(connection, id);
        String trace = null;
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            Object value = request.getRequest().getAttribute(Http.TRACE_ATTRIBUTE);
            if (value instanceof String text) trace = text;
        }
        changes.append(
            connection,
            Event.SCOPE_CREATED,
            Target.SCOPE,
            Long.toString(id),
            actorId,
            trace,
            AccessChangeStateResponse.empty(),
            AccessChangeStateResponse.registry(
                new AccessRegistryResponse(
                    created.code(),
                    created.nameVi(),
                    created.nameEn(),
                    created.active(),
                    false,
                    created.version(),
                    created.updatedAt(),
                    created.updatedBy()
                )
            )
        );
        return id;
    }
}
