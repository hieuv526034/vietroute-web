package vn.vietroute.business.catalog.service;

import java.util.Locale;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogManageUpdateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogSearchRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Permissions;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.util.CodeGenerator;
import vn.vietroute.common.validation.BusinessRules;

/** Mỗi bean con có AccessResource cố định; loại từ HTTP không được chọn tài nguyên quyền hoặc sửa ID loại khác. */
public abstract class TypedCatalogService {

    private final CatalogType type;

    private final CatalogRepository catalogs;

    private final JdbcTransactions transactions;

    protected TypedCatalogService(CatalogType type, CatalogRepository catalogs, JdbcTransactions transactions) {
        this.type = type;
        this.catalogs = catalogs;
        this.transactions = transactions;
    }

    /** Chỉ trả loại của bean; giữ tương thích quyền CATALOG_READ cũ trong phạm vi danh mục hiện hữu. */
    @AccessOperation(action = PermissionAction.READ, aliases = { Permissions.CATALOG_READ })
    public PageResponse<CatalogResponse> page(CatalogSearchRequest page, Locale locale) {
        var result = catalogs.search(type, page);
        return new PageResponse<>(
            result
                .content()
                .stream()
                .map(item -> CatalogResponse.from(item, locale))
                .toList(),
            result.page(),
            result.size(),
            result.totalElements(),
            result.totalPages(),
            result.hasNext(),
            result.hasPrevious()
        );
    }

    /** Không trả dữ liệu loại khác dù caller thay ID trên endpoint có quyền của mình. */
    @AccessOperation(action = PermissionAction.READ, aliases = { Permissions.CATALOG_READ })
    public CatalogResponse find(long id, Locale locale) {
        return CatalogResponse.from(requireType(catalogs.find(id)), locale);
    }

    /** Actor và loại server được kiểm trước INSERT; transaction/audit dùng nền chung. */
    @AccessOperation(
        action = PermissionAction.CREATE,
        aliases = { Permissions.CATALOG_CREATE, Permissions.CATALOG_WRITE }
    )
    public CatalogResponse create(CatalogCreateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        BusinessRules.require(body.getType() == type, ErrorCode.INVALID_REQUEST);
        String code = body.getCode() == null ? CodeGenerator.generate("CAT") : body.getCode();
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.insert(connection, body, code, actor.id()), locale)
        );
    }

    /** Type bất biến; kiểm cùng transaction trước optimistic UPDATE. */
    @AccessOperation(
        action = PermissionAction.UPDATE,
        aliases = { Permissions.CATALOG_UPDATE, Permissions.CATALOG_WRITE }
    )
    public CatalogResponse update(long id, CatalogManageUpdateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            requireType(catalogs.find(connection, id));
            return CatalogResponse.from(catalogs.updateManaged(connection, id, type, body, actor.id()), locale);
        });
    }

    /** Quyền DELETE độc lập; WRITE không được ngầm dùng để xóa. */
    @AccessOperation(action = PermissionAction.DELETE, aliases = { Permissions.CATALOG_DELETE })
    public CatalogResponse delete(long id, long version, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            requireType(catalogs.find(connection, id));
            return CatalogResponse.from(catalogs.delete(connection, id, version, actor.id()), locale);
        });
    }

    private CatalogItem requireType(CatalogItem item) {
        BusinessRules.require(item.getType() == type, ErrorCode.NOT_FOUND);
        return item;
    }
}
