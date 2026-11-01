package vn.vietroute.auth.service;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.config.AuthSettings;
import vn.vietroute.auth.dto.request.function.FunctionCreateRequest;
import vn.vietroute.auth.dto.request.function.FunctionSearchRequest;
import vn.vietroute.auth.dto.request.role.RoleFunctionsRequest;
import vn.vietroute.auth.dto.response.function.FunctionResponse;
import vn.vietroute.auth.dto.response.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.response.resource.AccessResourceResponse;
import vn.vietroute.auth.dto.response.role.RoleFunctionsResponse;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repo.AccessRepository;
import vn.vietroute.auth.repo.FunctionRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.AccessAuthorization;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** User → Role → Function → Menu/API; metadata guard lấy từ source, quyền hiệu lực luôn đọc từ DB. */
@Service
@RequiredArgsConstructor
public class FunctionService {

    private final FunctionRepository functions;

    private final AccessRepository roles;

    private final AccessResourceService definitions;

    private final JdbcTransactions transactions;

    private final AccessHistoryService history;

    private final AuthSettings settings;

    /** Tìm/đếm/phân trang chức năng ở DB, không nạp toàn bộ registry vào bộ nhớ. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public PageResponse<FunctionResponse> page(FunctionSearchRequest search) {
        return functions.page(search);
    }

    /** Đọc một chức năng theo mã, giữ projection response không có credential hoặc dữ liệu client audit. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public FunctionResponse get(String code) {
        return transactions.execute(connection -> require(connection, code));
    }

    /** CAS + binding + projection role + lịch sử cùng transaction; thất bại ở đâu rollback ở đó. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.UPDATE, createArgument = "create")
    public FunctionResponse save(FunctionCreateRequest body, long version, UserPrincipal actor, boolean create) {
        AccessActors.requireCurrent(actor);
        if (
            body.getCode().startsWith("ROLE_") ||
            (create &&
                (body.getCode().startsWith("HTTP_") ||
                    body.getCode().startsWith("DEFAULT_") ||
                    body.getCode().startsWith("MIGRATED_")))
        ) throw new BusinessException(ErrorCode.INVALID_CODE);
        var selected = select(body.getTargets(), actor);
        return transactions.execute(connection -> {
            roles.lockRoleAssignments(connection);
            var before = functions.find(connection, body.getCode());
            if (!create && before == null) throw new BusinessException(ErrorCode.NOT_FOUND);
            if (create) functions.requireCapacity(connection, settings.getRegistryCapacity());
            if (before != null) {
                select(before.getTargets(), actor);
                if (
                    before.isSourceManaged() && (!body.isActive() || !before.getTargets().equals(body.getTargets()))
                ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
                if (!actor.roles().contains(Role.ADMIN)) functions.requireDelegable(
                    connection,
                    Set.of(body.getCode()),
                    authorities()
                );
            }
            var after = functions.save(connection, body, version, selected, actor.id(), create);
            history.record(
                connection,
                create ? Event.FUNCTION_CREATED : Event.FUNCTION_UPDATED,
                Target.FUNCTION,
                body.getCode(),
                actor.id(),
                AccessChangeStateResponse.function(before),
                AccessChangeStateResponse.function(after)
            );
            return after;
        });
    }

    /** Không xóa function source hoặc function còn role tham chiếu; lịch sử giữ trạng thái trước khi tombstone. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.DELETE)
    public void delete(String code, long version, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        transactions.execute(connection -> {
            roles.lockRoleAssignments(connection);
            var before = require(connection, code);
            if (before.isSourceManaged()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
            select(before.getTargets(), actor);
            functions.delete(connection, code, version, actor.id());
            history.record(
                connection,
                Event.FUNCTION_DELETED,
                Target.FUNCTION,
                code,
                actor.id(),
                AccessChangeStateResponse.function(before),
                AccessChangeStateResponse.empty()
            );
            return null;
        });
    }

    /** Đọc tập chức năng của role và version gán chung để mở modal theo snapshot hiện hành. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ)
    public RoleFunctionsResponse role(Role role) {
        return transactions.execute(connection -> functions.role(connection, role));
    }

    /** Một kiểm tra cả tập, CAS và thay mapping theo tập; giữ ADMIN bất biến và trần ủy quyền hiện hành. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.ASSIGN)
    public RoleFunctionsResponse assign(Role role, RoleFunctionsRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        if (role.equals(Role.ADMIN)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            roles.lockRoleAssignments(connection);
            var before = functions.role(connection, role);
            if (!actor.roles().contains(Role.ADMIN)) {
                functions.requireDelegable(connection, before.getFunctionCodes(), authorities());
                functions.requireDelegable(connection, body.getFunctionCodes(), authorities());
                select(functions.targets(connection, before.getFunctionCodes()), actor);
                select(functions.targets(connection, body.getFunctionCodes()), actor);
            }
            var after = functions.assign(connection, role, body.getFunctionCodes(), body.getVersion(), actor.id());
            history.resources(
                connection,
                Event.ROLE_FUNCTIONS,
                Target.ROLE,
                role.name(),
                actor.id(),
                before.getFunctionCodes(),
                after.getFunctionCodes(),
                before.getVersion(),
                after.getVersion(),
                Set.of(),
                Set.of()
            );
            return after;
        });
    }

    private FunctionResponse require(Connection connection, String code) throws SQLException {
        var value = functions.find(connection, code);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        return value;
    }

    /** Server xác nhận từng route tồn tại/guard ADMIN; không nhận cờ public, handler hoặc authority từ payload. */
    private List<AccessResourceResponse> select(Set<String> targets, UserPrincipal actor) {
        var selected = definitions
            .inventory()
            .stream()
            .filter(RoleResourceService::assignable)
            .filter(item -> targets.contains(item.target()))
            .toList();
        if (
            !selected.stream().map(AccessResourceResponse::target).collect(Collectors.toSet()).equals(targets)
        ) throw new BusinessException(ErrorCode.INVALID_REQUEST);
        if (!actor.roles().contains(Role.ADMIN)) {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (
                selected.stream().anyMatch(item -> !AccessAuthorization.allows(item, auth))
            ) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return selected;
    }

    private static Set<String> authorities() {
        return SecurityContextHolder.getContext()
            .getAuthentication()
            .getAuthorities()
            .stream()
            .map(Object::toString)
            .collect(Collectors.toSet());
    }
}
