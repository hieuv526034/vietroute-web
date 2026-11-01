package vn.vietroute.auth.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.response.RoleDTO;
import vn.vietroute.auth.dto.response.UserDTO;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.RoleFunctionRepo;
import vn.vietroute.auth.repo.RoleRepo;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Role và hai bảng nối cùng một luồng quản trị; version/ủy quyền/history kiểm trước commit. */
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepo roles;
    private final UserRepo users;
    private final UserRoleRepo mappings;
    private final RoleFunctionRepo functions;
    private final AccessChangeRepo changes;
    private final JdbcTransactions transactions;
    private final AppSettings settings;

    public List<RoleDTO> all() {
        UserPrincipal.current();
        return roles.all().stream().map(RoleDTO::from).toList();
    }

    public PageResponse<RoleDTO> search(RoleSearch search) {
        UserPrincipal.current();
        return transactions.execute(connection -> {
            Page<Role> page = roles.search(search);
            return PageResponse.of(
                page.getContent().stream().map(RoleDTO::from).toList(),
                search,
                page.getTotalElements()
            );
        });
    }

    public RoleDTO save(String code, RoleRequest body, boolean create, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(
            code.matches(Regex.ROLE_CODE) &&
                body.getNameVi() != null &&
                !body.getNameVi().isBlank() &&
                body.getNameEn() != null &&
                !body.getNameEn().isBlank(),
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            Role value;
            if (create) {
                BusinessRules.require(
                    !roles.existsById(code) && roles.count() < settings.getAuthRegistryCapacity(),
                    ErrorCode.CONFLICT
                );
                value = new Role();
                value.setCode(code);
                value.setCreatedAt(Instant.now());
                value.setCreatedBy(actor.id());
            } else {
                value = BusinessRules.requireFound(roles.lock(code));
                BusinessRules.require(!value.isProtectedRole() && !value.isDeleted(), ErrorCode.ACCESS_DENIED);
                BusinessRules.require(
                    body.getVersion() != null && value.getVersion() == body.getVersion(),
                    ErrorCode.STALE_VERSION
                );
                mappings.requireDelegableRoles(connection, Set.of(code));
            }
            Set<String> targets = functions.targets(connection, code);
            boolean wide = body.getAllScopes() == null ? value.isAllScopes() : body.getAllScopes();
            if (wide) BusinessRules.require(
                UserPrincipal.current().getGlobalTargets().containsAll(targets),
                ErrorCode.ACCESS_DENIED
            );
            value.setAllScopes(wide);
            value.setNameVi(body.getNameVi().strip());
            value.setNameEn(body.getNameEn().strip());
            if (create || body.getActive() != null) value.setActive(!Boolean.FALSE.equals(body.getActive()));
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            roles.saveAndFlush(value);
            changes.append(
                connection,
                create ? "ROLE_CREATED" : "ROLE_UPDATED",
                "ROLE",
                code,
                actor.id(),
                Map.of(),
                Map.of("active", value.isActive(), "allScopes", wide)
            );
            return RoleDTO.from(value);
        });
    }

    public void delete(String code, long version, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            Role value = BusinessRules.requireFound(roles.lock(code));
            BusinessRules.require(!value.isProtectedRole(), ErrorCode.ACCESS_DENIED);
            BusinessRules.require(value.getVersion() == version, ErrorCode.STALE_VERSION);
            mappings.requireDelegableRoles(connection, Set.of(code));
            BusinessRules.require(!mappings.usesRole(connection, code), ErrorCode.CONFLICT);
            Set<String> before = functions.functions(connection, code);
            functions.replace(connection, code, Set.of(), actor.id());
            value.setDeleted(true);
            value.setActive(false);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            roles.saveAndFlush(value);
            changes.append(
                connection,
                "ROLE_DELETED",
                "ROLE",
                code,
                actor.id(),
                Map.of("functions", before),
                Map.of("deleted", true)
            );
            return null;
        });
    }

    public RoleDTO functions(String code) {
        UserPrincipal.current();
        return transactions.execute(connection -> {
            Role value = BusinessRules.requireFound(roles.findById(code).orElse(null));
            RoleDTO dto = RoleDTO.from(value);
            dto.setFunctionCodes(functions.functions(connection, code));
            return dto;
        });
    }

    public RoleDTO assignFunctions(String code, RoleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getFunctionCodes() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            Role value = BusinessRules.requireFound(roles.lock(code));
            BusinessRules.require(!value.isProtectedRole() && !value.isDeleted(), ErrorCode.ACCESS_DENIED);
            BusinessRules.require(
                body.getVersion() != null && value.getVersion() == body.getVersion(),
                ErrorCode.STALE_VERSION
            );
            Set<String> before = functions.functions(connection, code);
            Set<String> targets = functions.functionTargets(connection, body.getFunctionCodes());
            UserPrincipal.requireDelegable(functions.targets(connection, code));
            UserPrincipal.requireDelegable(targets);
            if (value.isAllScopes()) BusinessRules.require(
                UserPrincipal.current().getGlobalTargets().containsAll(targets),
                ErrorCode.ACCESS_DENIED
            );
            value.setFunctionsInitialized(true);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            roles.saveAndFlush(value);
            functions.replace(connection, code, body.getFunctionCodes(), actor.id());
            changes.append(
                connection,
                "ROLE_FUNCTIONS",
                "ROLE",
                code,
                actor.id(),
                Map.of("functions", before),
                Map.of("functions", body.getFunctionCodes())
            );
            RoleDTO dto = RoleDTO.from(value);
            dto.setFunctionCodes(body.getFunctionCodes());
            return dto;
        });
    }

    /** Thay global giữ mapping không đổi; thu hồi scoped role và tokenVersion trong cùng transaction. */
    public UserDTO assignUser(long id, UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(id > 0 && id != actor.id() && body.getRoles() != null, ErrorCode.ACCESS_DENIED);
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            User value = BusinessRules.requireFound(users.load(connection, id, true));
            BusinessRules.require(
                !value.getUsername().equalsIgnoreCase(settings.getBootstrapUsername()),
                ErrorCode.ACCESS_DENIED
            );
            Set<String> before = mappings.assignedRoles(connection, id);
            Set<String> affected = new LinkedHashSet<>(before);
            affected.addAll(body.getRoles());
            mappings.validateMappings(connection, body.getRoles(), Set.of());
            mappings.requireDelegableRoles(connection, affected);
            BusinessRules.require(body.getVersion() != null, ErrorCode.INVALID_REQUEST);
            List<RoleDTO> scopedBefore = UserRoleRepo.memberships(connection, id);
            mappings.replaceRoles(connection, id, body.getRoles(), body.getVersion(), actor.id());
            List<RoleDTO> scopedAfter = UserRoleRepo.memberships(connection, id);
            changes.append(
                connection,
                "USER_ROLES",
                "USER",
                Long.toString(id),
                actor.id(),
                Map.of("roles", before),
                Map.of("roles", body.getRoles())
            );
            changes.appendMemberships(connection, id, scopedBefore, scopedAfter, actor.id());
            return UserDTO.from(users.load(connection, id, false));
        });
    }

    public PageResponse<RoleDTO> scopes(RoleSearch search) {
        UserPrincipal.current();
        return mappings.page(search);
    }

    public PageResponse<RoleDTO> members(long id, UserSearch search) {
        UserPrincipal.current();
        return mappings.members(id, search);
    }

    public RoleDTO saveScope(long id, RoleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(
            id > 0 &&
                body.getNameVi() != null &&
                !body.getNameVi().isBlank() &&
                body.getNameEn() != null &&
                !body.getNameEn().isBlank() &&
                body.getVersion() != null &&
                body.getActive() != null,
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            mappings.save(connection, id, body, actor.id());
            changes.append(
                connection,
                "SCOPE_UPDATED",
                "SCOPE",
                Long.toString(id),
                actor.id(),
                Map.of(),
                Map.of("active", Boolean.TRUE.equals(body.getActive()))
            );
            return mappings.find(connection, id);
        });
    }

    public RoleDTO assignScope(long scope, long user, UserRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(
            user > 0 &&
                user != actor.id() &&
                body.getRoles() != null &&
                body.getVersion() != null &&
                body.getActive() != null,
            ErrorCode.INVALID_REQUEST
        );
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            BusinessRules.requireFound(mappings.find(connection, scope));
            User target = BusinessRules.requireFound(users.load(connection, user, true));
            BusinessRules.require(
                !mappings.protectedUser(connection, user) &&
                    (!Boolean.TRUE.equals(body.getActive()) || (target.isActive() && !target.isActivationPending())),
                ErrorCode.ACCESS_DENIED
            );
            BusinessRules.require(
                mappings.assignedRoles(connection, user).containsAll(body.getRoles()),
                ErrorCode.INVALID_REQUEST
            );
            mappings.validateMappings(connection, body.getRoles(), Set.of());
            mappings.requireDelegableRoles(connection, body.getRoles());
            RoleDTO before = mappings.member(connection, scope, user);
            mappings.assign(connection, scope, user, body, actor.id());
            RoleDTO after = mappings.member(connection, scope, user);
            changes.append(
                connection,
                "SCOPE_MEMBERSHIP",
                "MEMBERSHIP",
                scope + ":" + user,
                actor.id(),
                Map.of("roles", before == null ? Set.of() : before.getRoles()),
                Map.of("roles", after.getRoles(), "version", after.getVersion())
            );
            return after;
        });
    }

    public List<RoleDTO> mine(UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return mappings.mine(actor.id());
    }

    public RoleDTO scopeTargets(long scope, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        RoleDTO result = new RoleDTO();
        result.setScopeId(scope);
        result.setTargets(mappings.targets(actor.id(), actor.tokenVersion(), scope));
        return result;
    }
}
