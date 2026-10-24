package vn.vietroute.business.location.service;

import java.util.Locale;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.location.dto.request.LocationCreateRequest;
import vn.vietroute.business.location.dto.request.LocationLookupRequest;
import vn.vietroute.business.location.dto.request.LocationSearchRequest;
import vn.vietroute.business.location.dto.request.LocationUpdateRequest;
import vn.vietroute.business.location.dto.request.ProvinceCreateRequest;
import vn.vietroute.business.location.dto.request.ProvinceLookupRequest;
import vn.vietroute.business.location.dto.request.ProvinceSearchRequest;
import vn.vietroute.business.location.dto.request.ProvinceUpdateRequest;
import vn.vietroute.business.location.dto.response.LocationLookupResponse;
import vn.vietroute.business.location.dto.response.LocationResponse;
import vn.vietroute.business.location.dto.response.ProvinceLookupResponse;
import vn.vietroute.business.location.dto.response.ProvinceResponse;
import vn.vietroute.business.location.repository.LocationRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Danh mục địa lý toàn hệ thống, ADMIN quản trị; không cấp OPERATOR quyền sửa danh mục dùng chung bằng membership nhà xe. */
@Service
@AccessResource(Groups.LOCATION)
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepository locations;

    private final JdbcTransactions transactions;

    /** Công khai chỉ đọc tỉnh đang hoạt động, không trả audit/trạng thái nội bộ. */
    public PageResponse<ProvinceLookupResponse> lookupProvinces(ProvinceLookupRequest page, Locale locale) {
        return map(locations.provinces(page, true), item -> ProvinceLookupResponse.from(item, locale));
    }

    /** Cả tỉnh và địa điểm phải active, client không có cờ để nới điều kiện công khai. */
    public PageResponse<LocationLookupResponse> lookupLocations(LocationLookupRequest page, Locale locale) {
        return map(locations.locations(page, true, true), item -> LocationLookupResponse.from(item, locale));
    }

    /** Quản trị xem cả dữ liệu ngừng sử dụng để sửa/bật lại đúng version. */
    @AccessOperation(action = PermissionAction.READ, adminOnly = true)
    public PageResponse<ProvinceResponse> provinces(ProvinceSearchRequest page, Locale locale) {
        return map(locations.provinces(page, page.getActive()), item -> ProvinceResponse.from(item, locale));
    }

    /** Đọc trang quản trị theo trạng thái riêng, kể cả địa điểm có tỉnh đã tắt. */
    @AccessOperation(action = PermissionAction.READ, adminOnly = true)
    public PageResponse<LocationResponse> locations(LocationSearchRequest page, Locale locale) {
        return map(locations.locations(page, page.getActive(), false), item -> LocationResponse.from(item, locale));
    }

    /** Đọc tỉnh mới nhất để modal giữ đúng version, không thay bằng lookup công khai. */
    @AccessOperation(action = PermissionAction.READ, adminOnly = true)
    public ProvinceResponse province(long id, Locale locale) {
        return ProvinceResponse.from(locations.province(id), locale);
    }

    /** Đọc địa điểm và trạng thái tỉnh hiện hành cho quản trị; không suy quyền từ menu. */
    @AccessOperation(action = PermissionAction.READ, adminOnly = true)
    public LocationResponse location(long id, Locale locale) {
        return LocationResponse.from(locations.location(id), locale);
    }

    /** Transaction tạo mới có actor thật; registry mã duy nhất chốt tại DB. */
    @AccessOperation(action = PermissionAction.CREATE, adminOnly = true)
    public ProvinceResponse createProvince(ProvinceCreateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection ->
            ProvinceResponse.from(locations.createProvince(connection, body, actor.id()), locale)
        );
    }

    /** Tắt tỉnh chỉ ẩn khỏi tra cứu, không xóa con hoặc lịch sử; version và audit cùng transaction. */
    @AccessOperation(action = PermissionAction.UPDATE, adminOnly = true)
    public ProvinceResponse updateProvince(long id, ProvinceUpdateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection ->
            ProvinceResponse.from(locations.updateProvince(connection, id, body, actor.id()), locale)
        );
    }

    /** Khóa tỉnh trước INSERT địa điểm trong cùng connection/transaction, chống lọt trạng thái parent khi ghi đồng thời. */
    @AccessOperation(action = PermissionAction.CREATE, adminOnly = true)
    public LocationResponse createLocation(LocationCreateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            locations.requireActiveProvince(connection, body.getProvinceId());
            return LocationResponse.from(locations.createLocation(connection, body, actor.id()), locale);
        });
    }

    /** Giữ tỉnh/mã bất biến; chỉ yêu cầu tỉnh active khi địa điểm sẽ bật, vẫn cho ngừng sử dụng khi tỉnh đã tắt. */
    @AccessOperation(action = PermissionAction.UPDATE, adminOnly = true)
    public LocationResponse updateLocation(long id, LocationUpdateRequest body, UserPrincipal actor, Locale locale) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            var current = locations.location(connection, id);
            if (body.isActive()) locations.requireActiveProvince(connection, current.getProvinceId());
            return LocationResponse.from(locations.updateLocation(connection, id, body, actor.id()), locale);
        });
    }

    private static <T, R> PageResponse<R> map(PageResponse<T> page, Function<T, R> mapper) {
        return new PageResponse<>(
            page.content().stream().map(mapper).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }
}
