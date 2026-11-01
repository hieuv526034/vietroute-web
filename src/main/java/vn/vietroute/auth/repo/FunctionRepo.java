package vn.vietroute.auth.repo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.search.FunctionSearch;
import vn.vietroute.auth.entity.Function;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Repository bảng Function; CRUD JPA và SQL batch/query phức tạp cùng transaction server. */
@Repository
public class FunctionRepo extends JpaRepositorySupport<Function, String> {

    public FunctionRepo(EntityManager manager, JdbcTransactions transactions) {
        super(Function.class, manager, transactions);
    }

    /** Query lọc/count/page nằm ở Repo; LIKE escape giữ nguyên %, _ và ! của người dùng. */
    public Page<Function> search(FunctionSearch search) {
        String keyword =
            "%" +
            search.getQuery().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") +
            "%";
        Specification<Function> spec = (item, query, cb) ->
            cb.and(
                cb.isFalse(item.get("deleted")),
                search.getActive() == null ? cb.conjunction() : cb.equal(item.get("active"), search.getActive()),
                cb.or(
                    cb.like(cb.lower(item.get("code")), keyword, '!'),
                    cb.like(cb.lower(item.get("nameVi")), keyword, '!'),
                    cb.like(cb.lower(item.get("nameEn")), keyword, '!')
                )
            );
        return findAll(spec, PageRequest.of(search.getPage(), search.getSize(), Sort.by("code")));
    }

    public Function lock(String code) {
        return entityManager.find(Function.class, code, LockModeType.PESSIMISTIC_WRITE);
    }

    private static final String GRANTS = """
    FROM auth_users u JOIN auth_user_roles ur ON ur.user_id=u.id AND ur.scope_id=0 AND ur.active
    JOIN auth_roles r ON r.code=ur.role_code AND r.active AND NOT r.deleted
    JOIN auth_role_functions rf ON rf.role_code=r.code JOIN auth_functions f ON f.code=rf.function_code AND f.active AND NOT f.deleted
    JOIN auth_function_resources fr ON fr.function_code=f.code WHERE u.id=? AND u.active AND NOT u.deleted
    AND NOT u.activation_pending AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
    """;

    public List<FunctionDTO> capabilities(long user) {
        return read(connection -> {
            Map<String, FunctionDTO> found = new LinkedHashMap<>();
            Map<String, Set<String>> targets = new LinkedHashMap<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT DISTINCT f.code,f.name_vi,f.name_en,fr.kind,fr.target " +
                        GRANTS +
                        " ORDER BY f.code,fr.target"
                )
            ) {
                query.setLong(1, user);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String code = rows.getString(1);
                        FunctionDTO dto = found.get(code);
                        if (dto == null) {
                            dto = new FunctionDTO();
                            dto.setCode(code);
                            dto.setFunction(code);
                            dto.setNameVi(rows.getString(2));
                            dto.setNameEn(rows.getString(3));
                            found.put(code, dto);
                            targets.put(code, new LinkedHashSet<>());
                        }
                        if ("MENU".equals(rows.getString(4))) dto.setView(true);
                        targets.get(code).add(rows.getString(5));
                    }
                }
            }
            found.forEach((code, dto) -> dto.setTargets(Set.copyOf(targets.get(code))));
            return List.copyOf(found.values());
        });
    }

    public List<FunctionDTO> navigation(long user, String current, Locale locale) {
        return read(connection -> {
            List<FunctionDTO> result = new ArrayList<>();
            try (
                PreparedStatement query = connection.prepareStatement(
                    "SELECT DISTINCT ON(fr.target) fr.target,fr.name_vi,fr.name_en,fr.icon " +
                        GRANTS +
                        " AND fr.kind='MENU' AND fr.http_method='GET' ORDER BY fr.target,f.sort_order,fr.sort_order,f.code"
                )
            ) {
                query.setLong(1, user);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        String path = rows.getString(1).substring(4);
                        if (path.contains("{")) continue;
                        FunctionDTO dto = new FunctionDTO();
                        dto.setPath(path);
                        dto.setName(rows.getString("en".equals(locale.getLanguage()) ? 3 : 2));
                        dto.setIcon(rows.getString(4));
                        dto.setActive(current.equals(path));
                        result.add(dto);
                    }
                }
            }
            return List.copyOf(result);
        });
    }
}
