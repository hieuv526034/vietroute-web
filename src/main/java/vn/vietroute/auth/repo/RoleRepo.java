package vn.vietroute.auth.repo;

import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.dto.search.RoleSearch;
import vn.vietroute.auth.entity.Role;

/**
 * SQL native của Role; transaction ghi do Service/Initializer sở hữu.
 */
public interface RoleRepo extends JpaRepository<Role, String> {
    @Query(
        value = """
            SELECT r.*
            FROM {h-schema}auth_roles r
            WHERE NOT r.deleted
                AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
                AND (LOWER(r.code) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_vi) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_en) LIKE :keyword ESCAPE '!')
            ORDER BY r.code
            """,
        countQuery = """
            SELECT COUNT(*)
            FROM {h-schema}auth_roles r
            WHERE NOT r.deleted
                AND (CAST(:active AS BOOLEAN) IS NULL OR r.active = :active)
                AND (LOWER(r.code) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_vi) LIKE :keyword ESCAPE '!'
                    OR LOWER(r.name_en) LIKE :keyword ESCAPE '!')
            """,
        nativeQuery = true
    )
    Page<Role> searchRows(@Param("active") Boolean active, @Param("keyword") String keyword, Pageable page);

    @Transactional(readOnly = true)
    default Page<Role> search(RoleSearch search) {
        String keyword =
            "%" +
                search.getQuery().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") +
                "%";
        return searchRows(search.getActive(), keyword, PageRequest.of(search.getPage(), search.getSize()));
    }

    @Transactional(readOnly = true)
    @Query(
        value = """
            SELECT *
            FROM {h-schema}auth_roles
            WHERE NOT deleted
            ORDER BY code
            """,
        nativeQuery = true
    )
    List<Role> all();

    @Transactional(propagation = Propagation.MANDATORY)
    @Query(
        value = """
            SELECT *
            FROM {h-schema}auth_roles
            WHERE code = :code
            FOR UPDATE
            """,
        nativeQuery = true
    )
    Role lock(@Param("code") String code);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE {h-schema}auth_roles
            SET active = TRUE, deleted = FALSE, all_scopes = TRUE, protected_role = TRUE, updated_by = :actor,
                updated_at = CURRENT_TIMESTAMP, version = version + 1
            WHERE code = :code
                AND (NOT active OR deleted OR NOT all_scopes OR NOT protected_role)
            """,
        nativeQuery = true
    )
    int repairBootstrapRole(@Param("code") String code, @Param("actor") long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            UPDATE {h-schema}auth_functions
            SET active = TRUE, deleted = FALSE, updated_by = :actor, updated_at = CURRENT_TIMESTAMP,
                version = version + 1
            WHERE code = :code
                AND (NOT active OR deleted)
            """,
        nativeQuery = true
    )
    int repairBootstrapFunction(@Param("code") String code, @Param("actor") long actor);

    @Transactional(propagation = Propagation.MANDATORY)
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
        value = """
            INSERT INTO {h-schema}auth_role_functions(role_code, function_code, created_by, updated_by)
            VALUES (:role, :function, :actor, :actor)
            ON CONFLICT DO NOTHING
            """,
        nativeQuery = true
    )
    int assignBootstrapFunction(
        @Param("role") String role,
        @Param("function") String function,
        @Param("actor") long actor
    );
}
