package vn.vietroute.business.catalog.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.constant.CategoryRefType;
import vn.vietroute.business.catalog.dto.request.CategoryRequest;
import vn.vietroute.business.catalog.dto.response.CategoryLookupResponse;
import vn.vietroute.business.catalog.dto.response.CategoryResponse;
import vn.vietroute.business.catalog.dto.search.CategorySearch;
import vn.vietroute.business.catalog.entity.Category;
import vn.vietroute.business.catalog.repo.CategoryRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Một luồng CRUD cho Category, kiểm metadata/cha và giữ transaction dùng chung. */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final Set<String> PUBLIC_GROUPS = Set.of(
        CategoryRefType.VEHICLE_TYPE,
        CategoryRefType.BOOKING_SOURCE,
        CategoryRefType.PROVINCE,
        CategoryRefType.WARD,
        CategoryRefType.LOCATION
    );

    private final CategoryRepo categories;

    private final JdbcTransactions transactions;

    /** Lookup chỉ trả whitelist công khai; con chỉ đọc khi có ID cha, không tải toàn bộ cây. */
    public PageResponse<CategoryLookupResponse> lookup(String refType, CategorySearch search, Locale locale) {
        BusinessRules.require(refType != null && PUBLIC_GROUPS.contains(refType), ErrorCode.NOT_FOUND);
        if (
            (CategoryRefType.WARD.equals(refType) || CategoryRefType.LOCATION.equals(refType)) &&
            search.parent() == null
        ) {
            return PageResponse.of(List.of(), search, 0);
        }
        return map(categories.search(search, refType, "VALUE", true), item ->
            CategoryLookupResponse.from(item, locale)
        );
    }

    /** Quản trị cây nhận đúng một nhóm và một cha; GROUP có API đọc riêng. */
    public PageResponse<CategoryResponse> search(CategorySearch search, Locale locale) {
        BusinessRules.require(search.getRefType() != null || search.parent() != null, ErrorCode.INVALID_REQUEST);
        return list(search.getRefType(), search, locale);
    }

    public PageResponse<CategoryResponse> groups(CategorySearch search, Locale locale) {
        return map(categories.search(search, null, "GROUP", false), item -> CategoryResponse.from(item, locale));
    }

    private PageResponse<CategoryResponse> list(String refType, CategorySearch search, Locale locale) {
        return map(categories.search(search, refType, "VALUE", false), item -> CategoryResponse.from(item, locale));
    }

    /** Handler đã duyệt endpoint trước khi đọc dữ liệu quản trị. */
    public CategoryResponse findCategory(long id, Locale locale) {
        return CategoryResponse.from(categories.item(id), locale);
    }

    /** Khóa nhóm → cha → mục; JPA và JDBC cùng transaction, version/audit do server quản lý. */
    public CategoryResponse createCategory(CategoryRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            String kind = body.getType();
            BusinessRules.require("GROUP".equals(kind) || "VALUE".equals(kind), ErrorCode.INVALID_REQUEST);
            String code = body.getCode() == null ? BusinessRules.generateCode("CAT") : body.getCode();
            requireCode(code);
            requireCode(body.getRefType());
            Map<String, Object> props = new LinkedHashMap<>(body.getProps());
            props.putIfAbsent("nameEn", body.getName());
            Category value = Category.builder()
                .code(code)
                .type(kind)
                .name(body.getName())
                .description(body.getDescription())
                .refType(body.getRefType())
                .parentId(body.getParentId())
                .props(props)
                .active(body.isActive())
                .sortOrder(body.getSortOrder())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .createdBy(actor.id())
                .updatedBy(actor.id())
                .build();
            validate(value);
            categories.saveAndFlush(value);
            return CategoryResponse.from(categories.item(connection, value.getId()), locale);
        });
    }

    /** Identity bất biến; mọi cập nhật nội dung/metadata đều kiểm hợp đồng và version đã đọc. */
    public CategoryResponse updateCategory(long id, CategoryRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Category snapshot = categories.item(connection, id);
            if ("VALUE".equals(snapshot.getType())) categories.group(snapshot.getRefType());
            if (snapshot.getParentId() != null) categories.parent(snapshot.getParentId());
            Category value = categories.locked(id);
            BusinessRules.requireVersion(BusinessRules.requireFound(body.getVersion()), value.getVersion());
            BusinessRules.require(
                body.getCode() == null || body.getCode().equals(value.getCode()),
                ErrorCode.INVALID_REQUEST
            );
            BusinessRules.require(
                body.getRefType() == null || body.getRefType().equals(value.getRefType()),
                ErrorCode.INVALID_REQUEST
            );
            BusinessRules.require(
                body.getParentId() == null || body.getParentId().equals(value.getParentId()),
                ErrorCode.INVALID_REQUEST
            );
            BusinessRules.require(Objects.equals(body.getType(), value.getType()), ErrorCode.INVALID_REQUEST);
            value.setName(body.getName());
            value.setDescription(body.getDescription());
            value.setProps(new LinkedHashMap<>(body.getProps()));
            value.getProps().putIfAbsent("nameEn", body.getName());
            value.setActive(body.isActive());
            value.setSortOrder(body.getSortOrder());
            validate(value);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            categories.saveAndFlush(value);
            return CategoryResponse.from(categories.item(connection, id), locale);
        });
    }

    /** Ngừng sử dụng giữ FK/lịch sử; dữ liệu công khai còn xét trạng thái của cha và nhóm. */
    public CategoryResponse deleteCategory(long id, long version, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Category snapshot = categories.item(connection, id);
            if ("VALUE".equals(snapshot.getType())) categories.group(snapshot.getRefType());
            Category value = categories.locked(id);
            BusinessRules.requireVersion(version, value.getVersion());
            value.setActive(false);
            value.setUpdatedAt(Instant.now());
            value.setUpdatedBy(actor.id());
            categories.saveAndFlush(value);
            return CategoryResponse.from(categories.item(connection, id), locale);
        });
    }

    /** Metadata chỉ diễn giải kiểu/range/enum, không nhận SQL hoặc mã thực thi từ JSON. */
    private void validate(Category value) {
        if ("GROUP".equals(value.getType())) {
            BusinessRules.require(
                value.getCode().equals(value.getRefType()) && value.getParentId() == null,
                ErrorCode.INVALID_REQUEST
            );
            Object fields = value.getProps().getOrDefault("fields", List.of());
            BusinessRules.require(
                fields instanceof List<?> && ((List<?>) fields).size() <= 32,
                ErrorCode.INVALID_REQUEST
            );
            Set<String> names = new HashSet<>();
            for (Object definition : (List<?>) fields) {
                BusinessRules.require(definition instanceof Map<?, ?>, ErrorCode.INVALID_REQUEST);
                Map<?, ?> field = (Map<?, ?>) definition;
                String name = String.valueOf(field.get("name"));
                BusinessRules.require(
                    name.matches("[a-z][A-Za-z0-9]{0,39}") && !"nameEn".equals(name) && names.add(name),
                    ErrorCode.INVALID_REQUEST
                );
                BusinessRules.require(
                    Set.of("string", "number", "boolean").contains(field.get("type")),
                    ErrorCode.INVALID_REQUEST
                );
                BusinessRules.require(
                    Set.of("name", "type", "required", "maxLength", "min", "max", "scale", "values").containsAll(
                        field.keySet()
                    ),
                    ErrorCode.INVALID_REQUEST
                );
            }
            BusinessRules.require(
                Set.of("nameEn", "fields", "parentRefType", "hierarchical").containsAll(value.getProps().keySet()),
                ErrorCode.INVALID_REQUEST
            );
            Object parentRef = value.getProps().get("parentRefType");
            if (parentRef != null) {
                requireCode(String.valueOf(parentRef));
                if (!parentRef.equals(value.getCode())) categories.group(String.valueOf(parentRef));
            }
            return;
        }
        Category group = categories.group(value.getRefType());
        BusinessRules.require(!value.isActive() || group.isActive(), ErrorCode.LOCATION_PROVINCE_INACTIVE);
        categories.requireProps(value.getProps(), group.getProps());
        Object parentRef = group.getProps().get("parentRefType");
        boolean hierarchical = Boolean.TRUE.equals(group.getProps().get("hierarchical"));
        BusinessRules.require(parentRef == null || value.getParentId() != null, ErrorCode.INVALID_REQUEST);
        BusinessRules.require(
            parentRef != null || hierarchical || value.getParentId() == null,
            ErrorCode.INVALID_REQUEST
        );
        if (value.getParentId() != null) {
            Category parent = categories.parent(value.getParentId());
            String expected = parentRef == null ? value.getRefType() : parentRef.toString();
            BusinessRules.require(
                "VALUE".equals(parent.getType()) && expected.equals(parent.getRefType()),
                ErrorCode.INVALID_REQUEST
            );
            BusinessRules.require(!value.isActive() || parent.isActive(), ErrorCode.LOCATION_PROVINCE_INACTIVE);
        }
    }

    private void requireCode(String value) {
        BusinessRules.require(value != null && value.matches("[A-Z0-9][A-Z0-9_]{1,63}"), ErrorCode.INVALID_CODE);
    }

    private static <T, R> PageResponse<R> map(PageResponse<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
            page.getContent().stream().map(mapper).toList(),
            page.getPage(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isHasNext(),
            page.isHasPrevious()
        );
    }
}
