package vn.vietroute.business.catalog.service;

import java.util.Locale;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.catalog.dto.request.LocationRequest;
import vn.vietroute.business.catalog.dto.request.ProvinceRequest;
import vn.vietroute.business.catalog.dto.response.LocationLookupResponse;
import vn.vietroute.business.catalog.dto.response.LocationResponse;
import vn.vietroute.business.catalog.dto.response.ProvinceLookupResponse;
import vn.vietroute.business.catalog.dto.response.ProvinceResponse;
import vn.vietroute.business.catalog.dto.search.LocationLookupSearch;
import vn.vietroute.business.catalog.dto.search.LocationSearch;
import vn.vietroute.business.catalog.dto.search.ProvinceLookupSearch;
import vn.vietroute.business.catalog.dto.search.ProvinceSearch;
import vn.vietroute.business.catalog.entity.Location;
import vn.vietroute.business.catalog.repo.LocationRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Danh mục địa lý toàn hệ thống; grant endpoint quản trị độc lập membership nhà xe. */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepo locations;

    private final JdbcTransactions transactions;

    /** Công khai chỉ đọc tỉnh đang hoạt động, không trả audit/trạng thái nội bộ. */
    public PageResponse<ProvinceLookupResponse> lookupProvinces(ProvinceLookupSearch page, Locale locale) {
        return map(locations.provinces(page, true), item -> ProvinceLookupResponse.from(item, locale));
    }

    /** Cả tỉnh và địa điểm phải active, client không có cờ để nới điều kiện công khai. */
    public PageResponse<LocationLookupResponse> lookupLocations(LocationLookupSearch page, Locale locale) {
        return map(locations.locations(page, true, true), item -> LocationLookupResponse.from(item, locale));
    }

    /** Quản trị xem cả dữ liệu ngừng sử dụng để sửa/bật lại đúng version. */
    public PageResponse<ProvinceResponse> provinces(ProvinceSearch page, Locale locale) {
        return map(locations.provinces(page, page.getActive()), item -> ProvinceResponse.from(item, locale));
    }

    /** Đọc trang quản trị theo trạng thái riêng, kể cả địa điểm có tỉnh đã tắt. */
    public PageResponse<LocationResponse> locations(LocationSearch page, Locale locale) {
        return map(locations.locations(page, page.getActive(), false), item -> LocationResponse.from(item, locale));
    }

    /** Đọc tỉnh mới nhất để modal giữ đúng version, không thay bằng lookup công khai. */
    public ProvinceResponse province(long id, Locale locale) {
        return ProvinceResponse.from(locations.province(id), locale);
    }

    /** Đọc địa điểm và trạng thái tỉnh hiện hành cho quản trị; không suy quyền từ menu. */
    public LocationResponse location(long id, Locale locale) {
        return LocationResponse.from(locations.location(id), locale);
    }

    /** Transaction tạo mới có actor thật; registry mã duy nhất chốt tại DB. */
    public ProvinceResponse createProvince(ProvinceRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            ProvinceResponse.from(locations.createProvince(connection, body, actor.id()), locale)
        );
    }

    /** Tắt tỉnh chỉ ẩn khỏi tra cứu, không xóa con hoặc lịch sử; version và audit cùng transaction. */
    public ProvinceResponse updateProvince(long id, ProvinceRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection ->
            ProvinceResponse.from(locations.updateProvince(connection, id, body, actor.id()), locale)
        );
    }

    /** Khóa tỉnh trước INSERT địa điểm trong cùng connection/transaction, chống lọt trạng thái parent khi ghi đồng thời. */
    public LocationResponse createLocation(LocationRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            locations.requireActiveProvince(connection, body.getProvinceId());
            return LocationResponse.from(locations.createLocation(connection, body, actor.id()), locale);
        });
    }

    /** Giữ tỉnh/mã bất biến; chỉ yêu cầu tỉnh active khi địa điểm sẽ bật, vẫn cho ngừng sử dụng khi tỉnh đã tắt. */
    public LocationResponse updateLocation(long id, LocationRequest body, UserPrincipal actor, Locale locale) {
        UserPrincipal.requireCurrent(actor);
        return transactions.execute(connection -> {
            Location current = locations.location(connection, id);
            if (body.isActive()) locations.requireActiveProvince(connection, current.getProvinceId());
            return LocationResponse.from(locations.updateLocation(connection, id, body, actor.id()), locale);
        });
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
