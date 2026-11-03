package vn.vietroute.business.catalog.repo;

import static vn.vietroute.common.base.BaseRepository.booleanValue;
import static vn.vietroute.common.base.BaseRepository.instantValue;
import static vn.vietroute.common.base.BaseRepository.intValue;
import static vn.vietroute.common.base.BaseRepository.longValue;
import static vn.vietroute.common.base.BaseRepository.stringValue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.Tuple;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.common.base.JpaRepositorySupport;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/**
 * CRUD JPA và query lookup/cha theo tập; JDBC và JPA tham gia cùng transaction.
 */
@Repository
public class CategoryRepo extends JpaRepositorySupport<Category, Long> {

    private final ObjectMapper json;

    private static final String COLUMNS = """
    c.*, p.name AS parent_name, p.props->>'nameEn' AS parent_name_en,
    COALESCE(p.active,FALSE) AS parent_active
    """;

    private static final String FROM = """
    FROM {h-schema}category c
    LEFT JOIN {h-schema}category g ON g.group_code=c.ref_type
    LEFT JOIN {h-schema}category p ON p.id=c.parent_id
    """;

    public CategoryRepo(EntityManager manager, ObjectMapper json) {
        super(Category.class, manager);
        this.json = json;
    }

    /**
     * Danh sách có filter cố định, count/page cùng predicate và thứ tự; không query từng dòng.
     */
    public PageResponse<Category> search(CategorySearch page, String refType, String type, boolean publicOnly) {
        String filter = """
        WHERE c.type=:type AND (CAST(:refType AS TEXT) IS NULL OR c.ref_type=:refType)
            AND ((CAST(:parentId AS BIGINT) IS NULL AND c.parent_id IS NULL) OR c.parent_id=:parentId)
            AND (CAST(:active AS BOOLEAN) IS NULL OR c.active=:active)
                AND (NOT :publicOnly OR (c.active AND g.active AND NOT EXISTS (
                    WITH RECURSIVE ancestors AS (
                        SELECT a.id,a.parent_id,a.active,a.ref_type FROM {h-schema}category a WHERE a.id=c.parent_id
                        UNION ALL
                        SELECT a.id,a.parent_id,a.active,a.ref_type FROM {h-schema}category a
                        JOIN ancestors previous ON a.id=previous.parent_id
                    )
                    SELECT 1 FROM ancestors a JOIN {h-schema}category ag ON ag.group_code=a.ref_type
                    WHERE NOT a.active OR NOT ag.active
                )))
            AND LOWER(c.code || ' ' || c.name || ' ' || COALESCE(c.props->>'nameEn','') ||
                ' ' || COALESCE(c.props->>'address','')) LIKE LOWER(:keyword) ESCAPE '!'
        """;
        long count;
        {
            Query query = entityManager.createNativeQuery("SELECT COUNT(*) " + FROM + filter, Tuple.class);

            bind(query, page, refType, type, publicOnly);
            {
                Tuple row = (Tuple) query.getSingleResult();
                count = longValue(row.get(0));
            }
        }
        List<Category> content = new ArrayList<>();
        {
            Query query = entityManager.createNativeQuery(
                "SELECT " + COLUMNS + FROM + filter + " ORDER BY c.sort_order,c.id LIMIT :size OFFSET :offset",
                Tuple.class
            );

            bind(query, page, refType, type, publicOnly);
            query.setParameter("size", page.getSize());
            query.setParameter("offset", page.offset());
            {
                for (Object rowsValue : query.getResultList()) {
                    Tuple rows = (Tuple) rowsValue;
                    content.add(map(rows));
                }
            }
        }
        return PageResponse.of(content, page, count);
    }

    private void bind(Query query, CategorySearch page, String refType, String type, boolean publicOnly) {
        query.setParameter("type", type);
        query.setParameter("refType", refType);
        query.setParameter("parentId", page.parent());
        query.setParameter("active", publicOnly ? Boolean.TRUE : page.getActive());
        query.setParameter("publicOnly", publicOnly);
        String value = page.getQuery() == null ? "" : page.getQuery();
        query.setParameter("keyword", "%" + value.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
    }

    /** Đọc mục và thông tin cha trong cùng truy vấn. */
    public Category item(long id) {
        {
            Query query = entityManager.createNativeQuery("SELECT " + COLUMNS + FROM + " WHERE c.id=:id", Tuple.class);

            query.setParameter("id", id);
            {
                List<?> rowsValues = query.getResultList();
                Tuple rows = rowsValues.isEmpty() ? null : (Tuple) rowsValues.get(0);

                return BusinessRules.requireFound(rows != null ? map(rows) : null);
            }
        }
    }

    /**
     * Khóa GROUP/cha trước mutation VALUE để không lọt hợp đồng/trạng thái khi cập nhật đồng thời.
     */
    public Category group(String code) {
        return BusinessRules.requireFound(
            (Category) entityManager
                .createNativeQuery(
                    "SELECT * FROM {h-schema}category WHERE type = 'GROUP' AND code = :code FOR SHARE",
                    Category.class
                )
                .setParameter("code", code)
                .getResultStream()
                .findFirst()
                .orElse(null)
        );
    }

    public Category locked(long id) {
        return BusinessRules.requireFound(entityManager.find(Category.class, id, LockModeType.PESSIMISTIC_WRITE));
    }

    public Category parent(long id) {
        return BusinessRules.requireFound(entityManager.find(Category.class, id, LockModeType.PESSIMISTIC_READ));
    }

    /**
     * Dùng cùng hợp đồng DB với trigger; dữ liệu bind JSON, không sinh SQL từ props.
     */
    public void requireProps(Map<String, Object> props, Map<String, Object> group) {
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT category_props_valid(CAST(:props AS JSONB),CAST(:fields AS JSONB))",
                Tuple.class
            );

            query.setParameter("props", json.writeValueAsString(props));
            query.setParameter("fields", json.writeValueAsString(group.getOrDefault("fields", List.of())));
            {
                Tuple row = (Tuple) query.getSingleResult();
                BusinessRules.require(booleanValue(row.get(0)), ErrorCode.INVALID_REQUEST);
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    private Category map(Tuple row) {
        try {
            Map<String, Object> props = json.readValue(
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
                .parentId(row.get("parent_id", Long.class))
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
                .build();
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.DATABASE_UNAVAILABLE);
        }
    }
}
