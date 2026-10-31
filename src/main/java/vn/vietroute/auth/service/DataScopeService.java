package vn.vietroute.auth.service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.record.history.AccessChangeStateResponse;
import vn.vietroute.auth.dto.record.registry.AccessRegistryResponse;
import vn.vietroute.auth.dto.record.scope.DataScopeRequest;
import vn.vietroute.auth.dto.record.scope.DataScopeResponse;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipRequest;
import vn.vietroute.auth.dto.record.scope.ScopeMembershipResponse;
import vn.vietroute.auth.dto.record.scope.ScopePermissionsResponse;
import vn.vietroute.auth.dto.request.access.AccessRegistrySearchRequest;
import vn.vietroute.auth.dto.request.scope.ScopeMemberSearchRequest;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AccessRepository;
import vn.vietroute.auth.repository.DataScopeRepository;
import vn.vietroute.auth.repository.UserRepository;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessAuditConstants.Event;
import vn.vietroute.common.constant.AccessAuditConstants.Target;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** ADMIN quản trị scope; chưa cấp quyền tạo tài xế/nhà xe nghiệp vụ cho OPERATOR. */
@Service
@RequiredArgsConstructor
public class DataScopeService {

    private final DataScopeRepository scopes;

    private final AccessRepository access;

    private final UserRepository users;

    private final JdbcTransactions transactions;

    private final AccessChangeService changes;

    /** Danh bạ scope bảo mật riêng với tra cứu nhà xe/chuyến công khai cho khách. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ, adminOnly = true)
    public PageResponse<DataScopeResponse> page(AccessRegistrySearchRequest search) {
        return scopes.page(search);
    }

    /** Mã bất biến; thay trạng thái scope là thao tác bảo mật, không thay trạng thái nhận booking của nhà xe. */
    @AccessOperation(
        resource = Groups.ACCESS,
        action = PermissionAction.UPDATE,
        createArgument = "id",
        adminOnly = true
    )
    public DataScopeResponse save(long id, DataScopeRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id >= 0, ErrorCode.INVALID_REQUEST);
        BusinessRules.require(id != 0 || body.version() == 0, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            var before = id == 0 ? null : BusinessRules.requireFound(scopes.find(connection, id));
            long key = scopes.save(connection, id, body, actor.id());
            var after = scopes.find(connection, key);
            changes.record(
                connection,
                id == 0 ? Event.SCOPE_CREATED : Event.SCOPE_UPDATED,
                Target.SCOPE,
                Long.toString(key),
                actor.id(),
                history(before),
                history(after)
            );
            return after;
        });
    }

    /** Mapping thô chỉ ADMIN xem, có role tắt để thu hồi; không coi response là quyền user đang có hiệu lực. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.READ, adminOnly = true)
    public PageResponse<ScopeMembershipResponse> members(long id, ScopeMemberSearchRequest search) {
        BusinessRules.require(id > 0, ErrorCode.INVALID_REQUEST);
        return scopes.members(id, search);
    }

    /** Khóa gán chung, kiểm tập con role của user rồi thay membership/role/audit nguyên tử; không tự gán role toàn cục. */
    @AccessOperation(resource = Groups.ACCESS, action = PermissionAction.ASSIGN, adminOnly = true)
    public ScopeMembershipResponse assign(long id, long userId, ScopeMembershipRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id > 0 && userId > 0 && userId != actor.id(), ErrorCode.ACCESS_DENIED);
        BusinessRules.require(
            !body.roles().contains(Role.ADMIN) && (!body.active() || !body.roles().isEmpty()),
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            access.lockRoleAssignments(connection);
            BusinessRules.requireFound(scopes.find(connection, id));
            var target = BusinessRules.requireFound(users.findById(connection, userId, true));
            BusinessRules.require(
                !access.assignedRoles(connection, userId).contains(Role.ADMIN),
                ErrorCode.ACCESS_DENIED
            );
            BusinessRules.require(
                !body.active() || (target.isActive() && !target.isActivationPending()),
                ErrorCode.INVALID_REQUEST
            );
            BusinessRules.require(
                access.assignedRoles(connection, userId).containsAll(body.roles()),
                ErrorCode.INVALID_REQUEST
            );
            access.validateMappings(connection, body.roles(), Set.of());
            var before = scopes.member(connection, id, userId);
            scopes.assign(connection, id, userId, body, actor.id());
            var after = scopes.member(connection, id, userId);
            changes.record(
                connection,
                Event.SCOPE_MEMBERSHIP,
                Target.MEMBERSHIP,
                id + ":" + userId,
                actor.id(),
                membershipHistory(before),
                membershipHistory(after)
            );
            return after;
        });
    }

    /** Nhân viên chỉ nhận scope đang bật của chính mình, không nhận userId từ query hoặc token scope do client tạo. */
    @AccessOperation(authenticated = true)
    public List<DataScopeResponse> mine(UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return scopes.mine(actor.id());
    }

    /** UI lấy quyền riêng ở scope đang xem, đồng thời vẫn dùng capabilities/policy chung; backend luôn kiểm lại. */
    @AccessOperation(authenticated = true)
    public ScopePermissionsResponse permissions(long id, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(id > 0, ErrorCode.INVALID_REQUEST);
        return new ScopePermissionsResponse(id, scopes.permissions(actor.id(), actor.tokenVersion(), id));
    }

    private static AccessChangeStateResponse history(DataScopeResponse value) {
        return value == null
            ? AccessChangeStateResponse.empty()
            : AccessChangeStateResponse.registry(
                  new AccessRegistryResponse(
                      value.code(),
                      value.nameVi(),
                      value.nameEn(),
                      value.active(),
                      false,
                      value.version(),
                      value.updatedAt(),
                      value.updatedBy()
                  )
              );
    }

    private static AccessChangeStateResponse membershipHistory(ScopeMembershipResponse value) {
        return value == null
            ? AccessChangeStateResponse.empty()
            : AccessChangeStateResponse.membership(
                  value.roles().stream().map(Role::name).collect(Collectors.toSet()),
                  value.active(),
                  value.version()
              );
    }
}
