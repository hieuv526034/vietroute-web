package vn.vietroute.auth.repo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.dto.search.FunctionSearch;
import vn.vietroute.auth.entity.Function;

/**
 * SQL dùng schema Hibernate. Transaction đọc đặt ở default method được service gọi qua proxy;
 * các query bên trong tham gia giao dịch đó, không trông chờ annotation ở lời gọi nội bộ.
 */
public interface FunctionRepo extends JpaRepository<Function, String> {
    /** LIKE escape giữ nguyên %, _ và !; count và trang dùng cùng điều kiện. */
    @Transactional(readOnly = true)
    default Page<Function> search(FunctionSearch search) {
        String keyword =
            "%" +
            search.getQuery().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") +
            "%";
        return searchByFilters(keyword, search.getActive(), PageRequest.of(search.getPage(), search.getSize()));
    }

    @Query(
        value = """
        SELECT f.*
        FROM {h-schema}auth_functions f
        WHERE NOT f.deleted
            AND (CAST(:active AS BOOLEAN) IS NULL OR f.active = :active)
            AND (LOWER(f.code) LIKE :keyword ESCAPE '!'
                OR LOWER(f.name_vi) LIKE :keyword ESCAPE '!'
                OR LOWER(f.name_en) LIKE :keyword ESCAPE '!')
        ORDER BY f.code
        """,
        countQuery = """
        SELECT COUNT(*)
        FROM {h-schema}auth_functions f
        WHERE NOT f.deleted
            AND (CAST(:active AS BOOLEAN) IS NULL OR f.active = :active)
            AND (LOWER(f.code) LIKE :keyword ESCAPE '!'
                OR LOWER(f.name_vi) LIKE :keyword ESCAPE '!'
                OR LOWER(f.name_en) LIKE :keyword ESCAPE '!')
        """,
        nativeQuery = true
    )
    Page<Function> searchByFilters(@Param("keyword") String keyword, @Param("active") Boolean active, Pageable page);

    /** Khóa một function trên transaction của service; giữ bản ghi kể cả khi đã ngừng. */
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
        SELECT f.*
        FROM {h-schema}auth_functions f
        WHERE f.code = :code
        FOR UPDATE
        """,
        nativeQuery = true
    )
    Function lock(@Param("code") String code);

    /** Đọc grant hiện hành theo user; không suy quyền từ role trong token hoặc cache. */
    @Query(
        value = """
        SELECT DISTINCT f.code, f.name_vi, f.name_en, fr.kind, fr.target
        FROM {h-schema}auth_users u
        JOIN {h-schema}auth_user_roles ur ON ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
        JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
        JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
        JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
        JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
        WHERE u.id = :userId AND u.active AND NOT u.deleted AND NOT u.activation_pending
            AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
        ORDER BY f.code, fr.target
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> capabilityRows(@Param("userId") long userId);

    @Transactional(readOnly = true)
    default List<FunctionDTO> capabilities(long user) {
        Map<String, FunctionDTO> found = new LinkedHashMap<>();
        Map<String, Set<String>> targets = new LinkedHashMap<>();
        for (Map<String, Object> row : capabilityRows(user)) {
            String code = (String) row.get("code");
            FunctionDTO dto = found.get(code);
            if (dto == null) {
                dto = new FunctionDTO();
                dto.setCode(code);
                dto.setFunction(code);
                dto.setNameVi((String) row.get("name_vi"));
                dto.setNameEn((String) row.get("name_en"));
                found.put(code, dto);
                targets.put(code, new LinkedHashSet<>());
            }
            if ("MENU".equals(row.get("kind"))) dto.setView(true);
            targets.get(code).add((String) row.get("target"));
        }
        found.forEach((code, dto) -> dto.setTargets(Set.copyOf(targets.get(code))));
        return List.copyOf(found.values());
    }

    @Query(
        value = """
        SELECT DISTINCT ON (fr.target) fr.target, fr.name_vi, fr.name_en, fr.icon
        FROM {h-schema}auth_users u
        JOIN {h-schema}auth_user_roles ur ON ur.user_id = u.id AND ur.scope_id = 0 AND ur.active
        JOIN {h-schema}auth_roles r ON r.code = ur.role_code AND r.active AND NOT r.deleted
        JOIN {h-schema}auth_role_functions rf ON rf.role_code = r.code
        JOIN {h-schema}auth_functions f ON f.code = rf.function_code AND f.active AND NOT f.deleted
        JOIN {h-schema}auth_function_resources fr ON fr.function_code = f.code
        WHERE u.id = :userId AND u.active AND NOT u.deleted AND NOT u.activation_pending
            AND NOT u.must_change_password AND u.email_verified_at IS NOT NULL
            AND fr.kind = 'MENU' AND fr.http_method = 'GET'
        ORDER BY fr.target, f.sort_order, fr.sort_order, f.code
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> navigationRows(@Param("userId") long userId);

    @Transactional(readOnly = true)
    default List<FunctionDTO> navigation(long user, String current, Locale locale) {
        List<FunctionDTO> result = new ArrayList<>();
        for (Map<String, Object> row : navigationRows(user)) {
            String path = ((String) row.get("target")).substring(4);
            if (path.contains("{")) continue;
            FunctionDTO dto = new FunctionDTO();
            dto.setPath(path);
            dto.setName((String) row.get("en".equals(locale.getLanguage()) ? "name_en" : "name_vi"));
            dto.setIcon((String) row.get("icon"));
            dto.setActive(current.equals(path));
            result.add(dto);
        }
        return List.copyOf(result);
    }
}
