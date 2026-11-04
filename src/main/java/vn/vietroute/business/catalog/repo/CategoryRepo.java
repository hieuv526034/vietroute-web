package vn.vietroute.business.catalog.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * SQL native và ánh xạ của CategoryRepo; transaction ghi thuộc Service.
 */
public interface CategoryRepo extends JpaRepository<Category, Long> {
    ObjectMapper JSON = new ObjectMapper();

    String TREE_GROUPS = """
    WITH RECURSIVE branch_types(code, parent_code) AS (
        SELECT g.code, g.props->>'parentRefType'
        FROM {h-schema}category g
        WHERE g.group_code = :refType
        UNION
        SELECT g.code, g.props->>'parentRefType'
        FROM {h-schema}category g
        JOIN branch_types t ON g.group_code = t.parent_code
    )
    """;

    String TREE_CHILDREN = """
    EXISTS (
        SELECT 1
        FROM {h-schema}category child
        JOIN branch_types t ON t.code = child.ref_type
        WHERE child.type = 'VALUE' AND child.parent_id = c.id
    ) AS has_children
    """;

    @Query(
        value = TREE_GROUPS +
            "SELECT c.*, FALSE AS search_match, " +
            TREE_CHILDREN +
            """
            FROM {h-schema}category c
            JOIN branch_types t ON t.code = c.ref_type
            WHERE c.type = 'VALUE'
                AND ((CAST(:parentId AS BIGINT) IS NULL AND c.parent_id IS NULL) OR c.parent_id = :parentId)
            AND (CAST(:active AS BOOLEAN) IS NULL OR c.active = :active)
            ORDER BY c.sort_order, c.id
            """,
        nativeQuery = true
    )
    List<Map<String, Object>> branchRows(String refType, Long parentId, Boolean active);

    @Query(
        value = TREE_GROUPS +
            """
            , matches AS (
                SELECT c.id, c.parent_id
                FROM {h-schema}category c
                JOIN branch_types t ON t.code = c.ref_type
                WHERE c.type = 'VALUE' AND (:matchAncestors OR c.ref_type = :refType)
                    AND (CAST(:active AS BOOLEAN) IS NULL OR c.active = :active)
                    AND TRANSLATE(LOWER(CONCAT_WS(' ', c.code, c.name, c.props->>'nameEn',
                        c.props->>'address', c.props->>'administrativeCode')),
                        'àáạảãâầấậẩẫăằắặẳẵèéẹẻẽêềếệểễìíịỉĩòóọỏõôồốộổỗơờớợởỡùúụủũưừứựửữỳýỵỷỹđ',
                        'aaaaaaaaaaaaaaaaaeeeeeeeeeeeiiiiiooooooooooooooooouuuuuuuuuuuyyyyyd')
                        LIKE :keyword ESCAPE '!'
            ), lineage(id, parent_id) AS (
                SELECT id, parent_id FROM matches
                UNION
                SELECT p.id, p.parent_id
                FROM lineage l
                JOIN {h-schema}category p ON p.id = l.parent_id AND p.type = 'VALUE'
                JOIN branch_types t ON t.code = p.ref_type
            )
            SELECT c.*, EXISTS (SELECT 1 FROM matches m WHERE m.id = c.id) AS search_match,
            """ +
            TREE_CHILDREN +
            """
            FROM lineage l
            JOIN {h-schema}category c ON c.id = l.id
            ORDER BY c.sort_order, c.id
            """,
        nativeQuery = true
    )
    List<Map<String, Object>> treeSearchRows(String refType, boolean matchAncestors, Boolean active, String keyword);

    @Query(value = "SELECT * FROM {h-schema}category WHERE type = 'GROUP' ORDER BY sort_order, id", nativeQuery = true)
    List<Category> allGroups();

    /** Đọc một cấp của tab hoặc lấy mục khớp và tổ tiên trong một câu SQL. */
    default List<Category> tree(CategorySearch search, boolean matchAncestors) {
        String query = search.getQuery() == null ? "" : search.getQuery().strip();
        List<Map<String, Object>> rows = query.isEmpty()
            ? branchRows(search.getRefType(), search.getParentId(), search.getActive())
            : treeSearchRows(search.getRefType(), matchAncestors, search.getActive(), keyword(fold(query)));
        return rows.stream().map(CategoryRepo::map).toList();
    }

    private static String keyword(String query) {
        return "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
    }

    private static String fold(String query) {
        return Normalizer.normalize(query.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .replace('đ', 'd');
    }

    @Query(
        value = """
        SELECT c.*, p.name AS parent_name, p.props->> 'nameEn' AS parent_name_en,
            COALESCE(p.active, FALSE) AS parent_active
        FROM {h-schema}category c
        LEFT JOIN {h-schema}category g ON g.group_code=c.ref_type
        LEFT JOIN {h-schema}category p ON p.id=c.parent_id
        WHERE c.type=:type AND (CAST(:refType AS TEXT) IS NULL OR c.ref_type=:refType)
            AND ((CAST(:parentId AS BIGINT) IS NULL AND c.parent_id IS NULL) OR c.parent_id=:parentId)
            AND (CAST(:active AS BOOLEAN) IS NULL OR c.active=:active)
            AND (NOT :publicOnly OR (c.active AND g.active AND NOT EXISTS (
                    WITH RECURSIVE ancestors AS (
                        SELECT a.id, a.parent_id, a.active, a.ref_type FROM {h-schema}category a WHERE a.id=c.parent_id
                        UNION ALL
                        SELECT a.id, a.parent_id, a.active, a.ref_type FROM {h-schema}category a
                        JOIN ancestors previous ON a.id=previous.parent_id
        )
                    SELECT 1 FROM ancestors a JOIN {h-schema}category ag ON ag.group_code=a.ref_type
                    WHERE NOT a.active OR NOT ag.active
        )))
            AND LOWER(c.code || ' ' || c.name || ' ' || COALESCE(c.props->> 'nameEn', '') ||
            ' ' || COALESCE(c.props->> 'address', '')) LIKE LOWER(:keyword) ESCAPE '!'
        ORDER BY c.sort_order, c.id
        LIMIT :size OFFSET :offset
        """,
        nativeQuery = true
    )
    List<Map<String, Object>> searchRows(
        String type,
        String refType,
        Long parentId,
        Boolean active,
        boolean publicOnly,
        String keyword,
        int size,
        long offset
    );

    @Query(
        value = """
        SELECT COUNT(*)
        FROM {h-schema}category c
        LEFT JOIN {h-schema}category g ON g.group_code=c.ref_type
        LEFT JOIN {h-schema}category p ON p.id=c.parent_id
        WHERE c.type=:type AND (CAST(:refType AS TEXT) IS NULL OR c.ref_type=:refType)
            AND ((CAST(:parentId AS BIGINT) IS NULL AND c.parent_id IS NULL) OR c.parent_id=:parentId)
            AND (CAST(:active AS BOOLEAN) IS NULL OR c.active=:active)
            AND (NOT :publicOnly OR (c.active AND g.active AND NOT EXISTS (
                    WITH RECURSIVE ancestors AS (
                        SELECT a.id, a.parent_id, a.active, a.ref_type FROM {h-schema}category a WHERE a.id=c.parent_id
                        UNION ALL
                        SELECT a.id, a.parent_id, a.active, a.ref_type FROM {h-schema}category a
                        JOIN ancestors previous ON a.id=previous.parent_id
        )
                    SELECT 1 FROM ancestors a JOIN {h-schema}category ag ON ag.group_code=a.ref_type
                    WHERE NOT a.active OR NOT ag.active
        )))
            AND LOWER(c.code || ' ' || c.name || ' ' || COALESCE(c.props->> 'nameEn', '') ||
            ' ' || COALESCE(c.props->> 'address', '')) LIKE LOWER(:keyword) ESCAPE '!'
        """,
        nativeQuery = true
    )
    long searchCount(String type, String refType, Long parentId, Boolean active, boolean publicOnly, String keyword);

    @Transactional(readOnly = true)
    default PageResponse<Category> search(CategorySearch page, String refType, String type, boolean publicOnly) {
        String value = page.getQuery() == null ? "" : page.getQuery();
        String keyword = keyword(value);
        Boolean active = publicOnly ? Boolean.TRUE : page.getActive();
        long total = searchCount(type, refType, page.parent(), active, publicOnly, keyword);
        List<Category> rows = searchRows(
            type,
            refType,
            page.parent(),
            active,
            publicOnly,
            keyword,
            page.getSize(),
            page.offset()
        )
            .stream()
            .map(CategoryRepo::map)
            .toList();
        return PageResponse.of(rows, page, total);
    }

    @Query(
        value = """
        SELECT c.*, p.name AS parent_name, p.props->> 'nameEn' AS parent_name_en,
            COALESCE(p.active, FALSE) AS parent_active
        FROM {h-schema}category c
        LEFT JOIN {h-schema}category g ON g.group_code=c.ref_type
        LEFT JOIN {h-schema}category p ON p.id=c.parent_id
        WHERE c.id = :id
        """,
        nativeQuery = true
    )
    Map<String, Object> itemRow(long id);

    @Transactional(readOnly = true)
    default Category item(long id) {
        Map<String, Object> row = itemRow(id);
        return BusinessRules.requireFound(row == null || row.isEmpty() ? null : map(row));
    }

    @Query(
        value = "SELECT * FROM {h-schema}category WHERE type = 'GROUP' AND code = :code FOR SHARE ",
        nativeQuery = true
    )
    Category groupRow(String code);

    @Transactional(propagation = Propagation.MANDATORY)
    default Category group(String code) {
        return BusinessRules.requireFound(groupRow(code));
    }

    @Query(value = "SELECT * FROM {h-schema}category WHERE id = :id FOR UPDATE ", nativeQuery = true)
    Category lockedRow(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default Category locked(long id) {
        return BusinessRules.requireFound(lockedRow(id));
    }

    @Query(value = "SELECT * FROM {h-schema}category WHERE id = :id FOR SHARE ", nativeQuery = true)
    Category parentRow(long id);

    @Transactional(propagation = Propagation.MANDATORY)
    default Category parent(long id) {
        return BusinessRules.requireFound(parentRow(id));
    }

    @Query(
        value = "SELECT {h-schema}category_props_valid(CAST(:props AS JSONB), CAST(:fields AS JSONB)) ",
        nativeQuery = true
    )
    boolean propsValid(String props, String fields);

    default void requireProps(Map<String, Object> props, Map<String, Object> group) {
        try {
            BusinessRules.require(
                propsValid(
                    JSON.writeValueAsString(props),
                    JSON.writeValueAsString(group.getOrDefault("fields", List.of()))
                ),
                ErrorCode.INVALID_REQUEST
            );
        } catch (IOException error) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private static Category map(Map<String, Object> row) {
        try {
            Map<String, Object> props = JSON.readValue(
                stringValue(row.get("props")),
                new TypeReference<LinkedHashMap<String, Object>>() {}
            );
            return Category.builder()
                .id(longValue(row.get("id")))
                .code(stringValue(row.get("code")))
                .type(stringValue(row.get("type")))
                .name(stringValue(row.get("name")))
                .description(stringValue(row.get("description")))
                .refType(stringValue(row.get("ref_type")))
                .parentId((Long) row.get("parent_id"))
                .props(props)
                .groupCode(stringValue(row.get("group_code")))
                .valueRef(stringValue(row.get("value_ref")))
                .active(booleanValue(row.get("active")))
                .sortOrder(intValue(row.get("sort_order")))
                .createdAt(instantValue(row.get("created_at")))
                .updatedAt(instantValue(row.get("updated_at")))
                .createdBy(longValue(row.get("created_by")))
                .updatedBy(longValue(row.get("updated_by")))
                .version(longValue(row.get("version")))
                .parentName(stringValue(row.get("parent_name")))
                .parentNameEn(stringValue(row.get("parent_name_en")))
                .parentActive(booleanValue(row.get("parent_active")))
                .hasChildren(booleanValue(row.get("has_children")))
                .searchMatch(booleanValue(row.get("search_match")))
                .build();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
        }
    }
}
