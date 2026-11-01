package vn.vietroute.business.catalog.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogLookupRequest;
import vn.vietroute.business.catalog.dto.request.CatalogSearchRequest;
import vn.vietroute.business.catalog.dto.request.CatalogUpdateRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.entity.CatalogItem;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.CatalogType;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.util.CodeGenerator;

/** Service mẫu thể hiện quyền, transaction, audit và mapping; không chứa HTTP hoặc template. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    /** Picker tìm/phân trang ở DB, public luôn ép active=true và không đọc cờ nội bộ từ client. */
    public PageResponse<CatalogResponse> lookup(CatalogType type, CatalogLookupRequest page, Locale locale) {
        CatalogSearchRequest search = new CatalogSearchRequest();
        search.setPage(page.getPage());
        search.setSize(page.getSize());
        search.setQuery(page.getQuery());
        search.setActive(true);
        PageResponse<CatalogItem> result = catalogs.search(type, search);
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

    private final CatalogRepository catalogs;

    private final JdbcTransactions transactions;

    /** Đọc quản trị yêu cầu quyền riêng, không dùng quyền tạo/sửa để suy ra quyền xem dữ liệu đã xóa mềm. */
    public CatalogResponse find(long id, Locale locale) {
        return CatalogResponse.from(catalogs.find(id), locale);
    }

    /** Một transaction cập nhật version và audit; mã/type được giữ nguyên. */
    public CatalogResponse update(long id, CatalogUpdateRequest request, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.update(connection, id, request, actor.id()), locale)
        );
    }

    /** Xóa mềm dùng quyền DELETE tường minh; WRITE cũ không cấp thêm khả năng xóa. */
    public CatalogResponse delete(long id, long version, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.delete(connection, id, version, actor.id()), locale)
        );
    }

    /** Đọc trang dữ liệu rồi map tên theo locale, giữ tổng bản ghi và audit của kết quả repository. */
    public PageResponse<CatalogResponse> page(CatalogType type, PageRequest page, Locale locale) {
        PageResponse<CatalogItem> result = catalogs.page(type, page);
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

    /** Tạo danh mục cần quyền chức năng; actor lấy từ JWT đã kiểm tra lại với DB. */
    public CatalogResponse create(CatalogCreateRequest request, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        String code = request.getCode() == null ? CodeGenerator.generate("CAT") : request.getCode();
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.insert(connection, request, code, actor.id()), locale)
        );
    }
}
