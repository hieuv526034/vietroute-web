package vn.vietroute.auth.service;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.RoleDTO;
import vn.vietroute.auth.dto.UserDTO;
import vn.vietroute.auth.dto.request.RoleRequest;
import vn.vietroute.auth.dto.request.UserRequest;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.dto.search.UserSearch;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.repo.AccessChangeCustomRepo;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.RoleFunctionCustomRepo;
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

    private final AccessChangeCustomRepo changeBatch;

    private final RoleFunctionCustomRepo functionBatch;

    public List<RoleDTO> all() {
        UserPrincipal.current();
        return roles.all().stream().map(RoleDTO::from).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<RoleDTO> search(RoleSearch search) {
        UserPrincipal.current();
        Page<Role> page = roles.search(search);
        return PageResponse.of(page.getContent().stream().map(RoleDTO::from).toList(), search, page.getTotalElements());
    }

    @Transactional
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
        mappings.lockRoleAssignments();
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
            mappings.requireDelegableRoles(Set.of(code));
        }
        Set<String> targets = functions.targets(code);
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
            create ? "ROLE_CREATED" : "ROLE_UPDATED",
            "ROLE",
            code,
            actor.id(),
            Map.of(),
            Map.of("active", value.isActive(), "allScopes", wide)
        );
        return RoleDTO.from(value);
    }

    public void delete(String code, long version, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        transactions.execute(connection -> {
            mappings.lockRoleAssignments();
            Role value = BusinessRules.requireFound(roles.lock(code));
            BusinessRules.require(!value.isProtectedRole(), ErrorCode.ACCESS_DENIED);
            BusinessRules.require(value.getVersion() == version, ErrorCode.STALE_VERSION);
            mappings.requireDelegableRoles(Set.of(code));
            BusinessRules.require(!mappings.usesRole(code), ErrorCode.CONFLICT);
            Set<String> before = functions.functions(code);
            functionBatch.replace(connection, code, Set.of(), actor.id());
            value.setDeleted(true);
            value.setActive(false);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            roles.saveAndFlush(value);
            changes.append(
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

    @Transactional(readOnly = true)
    public RoleDTO functions(String code) {
        UserPrincipal.current();
        Role value = BusinessRules.requireFound(roles.findById(code).orElse(null));
        RoleDTO dto = RoleDTO.from(value);
        dto.setFunctionCodes(functions.functions(code));
        return dto;
    }

    public RoleDTO assignFunctions(String code, RoleRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getFunctionCodes() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments();
            Role value = BusinessRules.requireFound(roles.lock(code));
            BusinessRules.require(!value.isProtectedRole() && !value.isDeleted(), ErrorCode.ACCESS_DENIED);
            BusinessRules.require(
                body.getVersion() != null && value.getVersion() == body.getVersion(),
                ErrorCode.STALE_VERSION
            );
            Set<String> before = functions.functions(code);
            Set<String> targets = functions.functionTargets(body.getFunctionCodes());
            UserPrincipal.requireDelegable(functions.targets(code));
            UserPrincipal.requireDelegable(targets);
            if (value.isAllScopes()) BusinessRules.require(
                UserPrincipal.current().getGlobalTargets().containsAll(targets),
                ErrorCode.ACCESS_DENIED
            );
            value.setFunctionsInitialized(true);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            roles.saveAndFlush(value);
            BusinessRules.require(
                body.getFunctionCodes().isEmpty() ||
                    functions.availableFunctions(body.getFunctionCodes()) == body.getFunctionCodes().size(),
                ErrorCode.INVALID_REQUEST
            );
            functionBatch.replace(connection, code, body.getFunctionCodes(), actor.id());
            changes.append(
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
            mappings.lockRoleAssignments();
            User value = BusinessRules.requireFound(users.load(id, true));
            BusinessRules.require(
                !value.getUsername().equalsIgnoreCase(settings.getBootstrapUsername()),
                ErrorCode.ACCESS_DENIED
            );
            Set<String> before = mappings.assignedRoles(id);
            Set<String> affected = new LinkedHashSet<>(before);
            affected.addAll(body.getRoles());
            mappings.validateMappings(body.getRoles(), Set.of());
            mappings.requireDelegableRoles(affected);
            BusinessRules.require(body.getVersion() != null, ErrorCode.INVALID_REQUEST);
            List<RoleDTO> scopedBefore = mappings.memberships(id);
            mappings.replaceRoles(connection, id, body.getRoles(), body.getVersion(), actor.id());
            List<RoleDTO> scopedAfter = mappings.memberships(id);
            changes.append(
                "USER_ROLES",
                "USER",
                Long.toString(id),
                actor.id(),
                Map.of("roles", before),
                Map.of("roles", body.getRoles())
            );
            changeBatch.appendMemberships(connection, id, scopedBefore, scopedAfter, actor.id());
            return UserDTO.from(users.load(id, false));
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

    @Transactional
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
        mappings.lockRoleAssignments();
        mappings.save(id, body, actor.id());
        changes.append(
            "SCOPE_UPDATED",
            "SCOPE",
            Long.toString(id),
            actor.id(),
            Map.of(),
            Map.of("active", Boolean.TRUE.equals(body.getActive()))
        );
        return mappings.find(id);
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
            mappings.lockRoleAssignments();
            BusinessRules.requireFound(mappings.find(scope));
            User target = BusinessRules.requireFound(users.load(user, true));
            BusinessRules.require(
                !mappings.protectedUser(user) &&
                    (!Boolean.TRUE.equals(body.getActive()) || (target.isActive() && !target.isActivationPending())),
                ErrorCode.ACCESS_DENIED
            );
            BusinessRules.require(mappings.assignedRoles(user).containsAll(body.getRoles()), ErrorCode.INVALID_REQUEST);
            mappings.validateMappings(body.getRoles(), Set.of());
            mappings.requireDelegableRoles(body.getRoles());
            RoleDTO before = mappings.member(scope, user);
            mappings.assign(connection, scope, user, body, actor.id());
            RoleDTO after = mappings.member(scope, user);
            changes.append(
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
