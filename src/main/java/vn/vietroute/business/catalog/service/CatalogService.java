package vn.vietroute.business.catalog.service;

import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.CatalogCreateRequest;
import vn.vietroute.business.catalog.dto.request.CatalogUpdateRequest;
import vn.vietroute.business.catalog.dto.response.CatalogResponse;
import vn.vietroute.business.catalog.repository.CatalogRepository;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.Constants.CatalogType;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.util.CodeGenerator;

/** Service mẫu thể hiện quyền, transaction, audit và mapping; không chứa HTTP hoặc template. */
@Service
@RequiredArgsConstructor
public class CatalogService {

    private final CatalogRepository catalogs;

    private final JdbcTransactions transactions;

    /** Đọc quản trị yêu cầu quyền riêng, không dùng quyền tạo/sửa để suy ra quyền xem dữ liệu đã xóa mềm. */
    @PreAuthorize("hasAuthority('CATALOG_READ')")
    public CatalogResponse find(long id, Locale locale) {
        return CatalogResponse.from(catalogs.find(id), locale);
    }

    /** Một transaction cập nhật version và audit; mã/type được giữ nguyên. */
    @PreAuthorize("hasAnyAuthority('CATALOG_UPDATE','CATALOG_WRITE')")
    public CatalogResponse update(long id, CatalogUpdateRequest request, UserPrincipal actor, Locale locale) {
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.update(connection, id, request, actor.id()), locale)
        );
    }

    /** Xóa mềm dùng quyền DELETE tường minh; WRITE cũ không cấp thêm khả năng xóa. */
    @PreAuthorize("hasAuthority('CATALOG_DELETE')")
    public CatalogResponse delete(long id, long version, UserPrincipal actor, Locale locale) {
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.delete(connection, id, version, actor.id()), locale)
        );
    }

    /** Đọc trang dữ liệu rồi map tên theo locale, giữ tổng bản ghi và audit của kết quả repository. */
    public PageResponse<CatalogResponse> page(CatalogType type, PageRequest page, Locale locale) {
        var result = catalogs.page(type, page);
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
    @PreAuthorize("hasAnyAuthority('CATALOG_CREATE','CATALOG_WRITE')")
    public CatalogResponse create(CatalogCreateRequest request, UserPrincipal actor, Locale locale) {
        String code = request.getCode() == null ? CodeGenerator.generate("CAT") : request.getCode();
        return transactions.execute(connection ->
            CatalogResponse.from(catalogs.insert(connection, request, code, actor.id()), locale)
        );
    }
}
