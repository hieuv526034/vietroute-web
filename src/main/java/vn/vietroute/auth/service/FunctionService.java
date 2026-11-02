package vn.vietroute.auth.service;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.request.FunctionRequest;
import vn.vietroute.auth.dto.search.FunctionSearch;
import vn.vietroute.auth.entity.Function;
import vn.vietroute.auth.repo.AccessChangeRepo;
import vn.vietroute.auth.repo.FunctionRepo;
import vn.vietroute.auth.repo.FunctionResourceRepo;
import vn.vietroute.auth.repo.RoleFunctionRepo;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.EndpointAccess;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Function CRUD, gán resource/menu và khả năng UI lấy cùng tập endpoint DB. */
@Service
@RequiredArgsConstructor
public class FunctionService {

    private final FunctionRepo functions;
    private final FunctionResourceRepo resources;
    private final RoleFunctionRepo roles;
    private final UserRoleRepo mappings;
    private final AccessChangeRepo changes;
    private final EndpointAccess catalog;
    private final JdbcTransactions transactions;
    private final AppSettings settings;

    public PageResponse<FunctionDTO> search(FunctionSearch search) {
        UserPrincipal.current();
        return transactions.execute(connection -> {
            Page<Function> page = functions.search(search);
            Map<String, Set<String>> targets = resources.targetsFor(
                connection,
                page.getContent().stream().map(Function::getCode).toList()
            );
            return PageResponse.of(
                page
                    .getContent()
                    .stream()
                    .map(value -> FunctionDTO.from(value, targets.getOrDefault(value.getCode(), Set.of())))
                    .toList(),
                search,
                page.getTotalElements()
            );
        });
    }

    public FunctionDTO get(String code) {
        UserPrincipal.current();
        return transactions.execute(connection -> {
            Function value = BusinessRules.requireFound(functions.findById(code).orElse(null));
            BusinessRules.require(!value.isDeleted(), ErrorCode.NOT_FOUND);
            return FunctionDTO.from(value, resources.targets(connection, code));
        });
    }

    /** Khóa/CAS/batch binding/history chung một transaction; endpoint chưa khai báo hoặc ngoài trần đều bị từ chối. */
    public FunctionDTO save(FunctionRequest body, boolean create, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(
            body.getCode() != null &&
                body.getNameVi() != null &&
                !body.getNameVi().isBlank() &&
                body.getNameEn() != null &&
                !body.getNameEn().isBlank() &&
                body.getTargets() != null,
            ErrorCode.INVALID_REQUEST
        );
        List<FunctionDTO> selected = catalog
            .all()
            .stream()
            .filter(
                item ->
                    !item.isPublicEndpoint() && !item.isSelfService() && body.getTargets().contains(item.getTarget())
            )
            .toList();
        BusinessRules.require(selected.size() == body.getTargets().size(), ErrorCode.INVALID_REQUEST);
        UserPrincipal.requireDelegable(body.getTargets());
        return transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            Function value;
            Set<String> before = Set.of();
            if (create) {
                BusinessRules.require(
                    !functions.existsById(body.getCode()) && functions.count() < settings.getAuthRegistryCapacity(),
                    ErrorCode.CONFLICT
                );
                value = new Function();
                value.setCode(body.getCode());
                value.setCreatedAt(Instant.now());
                value.setCreatedBy(actor.id());
            } else {
                value = BusinessRules.requireFound(functions.lock(body.getCode()));
                BusinessRules.require(
                    !value.isDeleted() && body.getVersion() != null && value.getVersion() == body.getVersion(),
                    ErrorCode.STALE_VERSION
                );
                before = resources.targets(connection, value.getCode());
                UserPrincipal.requireDelegable(before);
                if (roles.hasAllScopesRole(connection, value.getCode())) {
                    BusinessRules.require(
                        UserPrincipal.current().getGlobalTargets().containsAll(body.getTargets()) &&
                            UserPrincipal.current().getGlobalTargets().containsAll(before),
                        ErrorCode.ACCESS_DENIED
                    );
                }
            }
            value.setNameVi(body.getNameVi().strip());
            value.setNameEn(body.getNameEn().strip());
            if (create || body.getActive() != null) value.setActive(!Boolean.FALSE.equals(body.getActive()));
            if (create || body.getDescription() != null) value.setDescription(
                body.getDescription() == null ? "" : body.getDescription()
            );
            if (create || body.getIcon() != null) value.setIcon(body.getIcon() == null ? "shield" : body.getIcon());
            if (create || body.getSortOrder() != null) value.setSortOrder(
                body.getSortOrder() == null ? 0 : body.getSortOrder()
            );
            value.setResourcesInitialized(true);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            functions.saveAndFlush(value);
            resources.replace(connection, value.getCode(), selected, actor.id());
            if (!create) roles.touchRoles(connection, value.getCode(), actor.id());
            changes.append(
                connection,
                create ? "FUNCTION_CREATED" : "FUNCTION_UPDATED",
                "FUNCTION",
                value.getCode(),
                actor.id(),
                Map.of("resources", before),
                Map.of("resources", body.getTargets())
            );
            return FunctionDTO.from(value, body.getTargets());
        });
    }

    public void delete(String code, long version, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        transactions.execute(connection -> {
            mappings.lockRoleAssignments(connection);
            Function value = BusinessRules.requireFound(functions.lock(code));
            BusinessRules.require(!value.isDeleted() && value.getVersion() == version, ErrorCode.STALE_VERSION);
            Set<String> before = resources.targets(connection, code);
            UserPrincipal.requireDelegable(before);
            BusinessRules.require(!roles.usesFunction(connection, code), ErrorCode.CONFLICT);
            value.setActive(false);
            value.setDeleted(true);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            functions.saveAndFlush(value);
            changes.append(
                connection,
                "FUNCTION_DELETED",
                "FUNCTION",
                code,
                actor.id(),
                Map.of("resources", before),
                Map.of("deleted", true)
            );
            return null;
        });
    }

    public List<FunctionDTO> capabilities(long user) {
        return functions.capabilities(user);
    }

    public List<FunctionDTO> navigation(UserPrincipal actor, String current, Locale locale) {
        return actor == null ? List.of() : functions.navigation(actor.id(), current, locale);
    }

    public List<FunctionDTO> inventory() {
        UserPrincipal.current();
        return catalog.all();
    }

    public PageResponse<FunctionDTO> resources(FunctionSearch search) {
        UserPrincipal.current();
        String keyword = search.getQuery().toLowerCase(Locale.ROOT);
        List<FunctionDTO> result = catalog
            .all()
            .stream()
            .filter(item -> search.getKind().isBlank() || search.getKind().equals(item.getKind()))
            .filter(item ->
                (item.getTarget() + " " + item.getDescription() + " " + item.getHandler())
                    .toLowerCase(Locale.ROOT)
                    .contains(keyword)
            )
            .toList();
        int start = (int) Math.min(search.offset(), result.size());
        return PageResponse.of(
            result.subList(start, Math.min(start + search.getSize(), result.size())),
            search,
            result.size()
        );
    }
}
