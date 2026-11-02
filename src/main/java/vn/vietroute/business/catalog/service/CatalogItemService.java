package vn.vietroute.business.catalog.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.CatalogItemRequest;
import vn.vietroute.business.catalog.dto.response.CatalogItemResponse;
import vn.vietroute.business.catalog.dto.search.CatalogItemLookupSearch;
import vn.vietroute.business.catalog.dto.search.CatalogItemSearch;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.business.catalog.repo.CatalogItemRepo;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Service mẫu thể hiện quyền, transaction, audit và mapping; không chứa HTTP hoặc template. */
@Service
@RequiredArgsConstructor
public class CatalogItemService {

    /** Picker tìm/phân trang ở DB, public luôn ép active=true và không đọc cờ nội bộ từ client. */
    public PageResponse<CatalogItemResponse> lookup(CatalogType type, CatalogItemLookupSearch page, Locale locale) {
        CatalogItemSearch search = new CatalogItemSearch();
        search.setPage(page.getPage());
        search.setSize(page.getSize());
        search.setQuery(page.getQuery());
        search.setActive(true);
        PageResponse<CatalogItem> result = catalogs.search(type, search);
        return new PageResponse<>(
            result
                .getContent()
                .stream()
                .map(item -> CatalogItemResponse.from(item, locale))
                .toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    private final CatalogItemRepo catalogs;

    private final JdbcTransactions transactions;

    /** Đọc quản trị yêu cầu quyền riêng, không dùng quyền tạo/sửa để suy ra quyền xem dữ liệu đã xóa mềm. */
    public CatalogItemResponse find(long id, Locale locale) {
        return CatalogItemResponse.from(catalogs.find(id), locale);
    }

    /** Một transaction cập nhật version và audit; mã/type được giữ nguyên. */
    public CatalogItemResponse update(long id, CatalogItemRequest request, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            CatalogItemResponse.from(catalogs.update(connection, id, request, actor.id()), locale)
        );
    }

    /** Xóa mềm dùng quyền DELETE tường minh; WRITE cũ không cấp thêm khả năng xóa. */
    public CatalogItemResponse delete(long id, long version, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            CatalogItemResponse.from(catalogs.delete(connection, id, version, actor.id()), locale)
        );
    }

    /** Đọc trang dữ liệu rồi map tên theo locale, giữ tổng bản ghi và audit của kết quả repository. */
    public PageResponse<CatalogItemResponse> page(CatalogType type, PageRequest page, Locale locale) {
        PageResponse<CatalogItem> result = catalogs.page(type, page);
        return new PageResponse<>(
            result
                .getContent()
                .stream()
                .map(item -> CatalogItemResponse.from(item, locale))
                .toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    /** Tạo danh mục cần quyền chức năng; actor lấy từ JWT đã kiểm tra lại với DB. */
    public CatalogItemResponse create(CatalogItemRequest request, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        String code = request.getCode() == null ? BusinessRules.generateCode("CAT") : request.getCode();
        return transactions.execute(connection ->
            CatalogItemResponse.from(catalogs.insert(connection, request, code, actor.id()), locale)
        );
    }

    /** Loại tài nguyên do handler truyền từ server; endpoint GET đã được guard trước khi đọc. */
    public PageResponse<CatalogItemResponse> page(CatalogType type, CatalogItemSearch page, Locale locale) {
        PageResponse<CatalogItem> result = catalogs.search(type, page);
        return new PageResponse<>(
            result
                .getContent()
                .stream()
                .map(item -> CatalogItemResponse.from(item, locale))
                .toList(),
            result.getPage(),
            result.getSize(),
            result.getTotalElements(),
            result.getTotalPages(),
            result.isHasNext(),
            result.isHasPrevious()
        );
    }

    /** Không trả dữ liệu loại khác dù caller thay ID trên endpoint có quyền của mình. */
    public CatalogItemResponse find(CatalogType type, long id, Locale locale) {
        return CatalogItemResponse.from(requireType(type, catalogs.find(id)), locale);
    }

    /** Actor và loại server được kiểm trước INSERT; transaction/audit dùng nền chung. */
    public CatalogItemResponse create(CatalogType type, CatalogItemRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        BusinessRules.require(body.getType() == type, ErrorCode.INVALID_REQUEST);
        String code = body.getCode() == null ? BusinessRules.generateCode("CAT") : body.getCode();
        return transactions.execute(connection ->
            CatalogItemResponse.from(catalogs.insert(connection, body, code, actor.id()), locale)
        );
    }

    /** Type bất biến; kiểm cùng transaction trước optimistic UPDATE. */
    public CatalogItemResponse update(
        CatalogType type,
        long id,
        CatalogItemRequest body,
        UserPrincipal actor,
        Locale locale
    ) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            requireType(type, catalogs.find(connection, id));
            return CatalogItemResponse.from(catalogs.updateManaged(connection, id, type, body, actor.id()), locale);
        });
    }

    /** Quyền DELETE độc lập; WRITE không được ngầm dùng để xóa. */
    public CatalogItemResponse delete(CatalogType type, long id, long version, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            requireType(type, catalogs.find(connection, id));
            return CatalogItemResponse.from(catalogs.delete(connection, id, version, actor.id()), locale);
        });
    }

    private CatalogItem requireType(CatalogType type, CatalogItem item) {
        BusinessRules.require(item.getType() == type, ErrorCode.NOT_FOUND);
        return item;
    }
}
