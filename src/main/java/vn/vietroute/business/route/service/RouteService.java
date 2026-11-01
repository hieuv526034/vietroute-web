package vn.vietroute.business.route.service;

import java.math.BigDecimal;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.request.CarrierSearchRequest;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repository.CarrierRepository;
import vn.vietroute.business.route.dto.request.RouteStopRequest;
import vn.vietroute.business.route.dto.request.RouteWriteRequest;
import vn.vietroute.business.route.dto.response.RouteResponse;
import vn.vietroute.business.route.repository.RouteRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Mẫu tuyến có version, scope nhà xe và thứ tự điểm; không tự sửa snapshot của chuyến. */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepository routes;
    private final CarrierRepository carriers;
    private final JdbcTransactions transactions;

    /** Tổng và trang được lọc trực tiếp ở PostgreSQL. */
    public PageResponse<RouteResponse> page(CarrierSearchRequest query) {
        return routes.page(query);
    }

    public RouteResponse find(long id) {
        return transactions.execute(connection -> routes.find(connection, id, false, true));
    }

    /** Picker CREATE không bắt thêm quyền đọc hồ sơ nhà xe hoặc quyền khác scope. */
    public PageResponse<CarrierChoiceResponse> choices(CarrierSearchRequest query) {
        query.setActive(true);
        PageResponse<Carrier> page = carriers.choices(query);
        return new PageResponse<>(
            page.content().stream().map(CarrierChoiceResponse::from).toList(),
            page.page(),
            page.size(),
            page.totalElements(),
            page.totalPages(),
            page.hasNext(),
            page.hasPrevious()
        );
    }

    /** Thứ tự khóa parent nhà xe → tuyến → địa điểm; tạo/cập nhật điểm trong cùng transaction. */
    public RouteResponse create(RouteWriteRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        validate(body);
        return transactions.execute(connection -> {
            Carrier carrier = carriers.carrier(connection, body.getCarrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.CONFLICT);
            routes.validateLocations(connection, body);
            long id = routes.insert(connection, carrier.getScopeId(), body, actor.id());
            routes.replaceStops(connection, id, carrier.getScopeId(), body, actor.id());
            return routes.find(connection, id, false, true);
        });
    }

    /** Hai chiều được sửa riêng; code/nhà xe không được chuyển qua payload cập nhật. */
    public RouteResponse update(long id, RouteWriteRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        validate(body);
        BusinessRules.require(body.getVersion() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            RouteResponse existing = routes.find(connection, id, false, false);
            BusinessRules.require(
                existing.carrierId() == body.getCarrierId() && existing.code().equals(body.getCode()),
                ErrorCode.INVALID_REQUEST
            );
            Carrier carrier = carriers.carrier(connection, existing.carrierId(), true);
            RouteResponse locked = routes.find(connection, id, true, false);
            BusinessRules.require(locked.version() == body.getVersion(), ErrorCode.STALE_VERSION);
            BusinessRules.require(!body.isActive() || carrier.isActive(), ErrorCode.CONFLICT);
            routes.validateLocations(connection, body);
            routes.update(connection, id, body, actor.id());
            routes.replaceStops(connection, id, existing.scopeId(), body, actor.id());
            return routes.find(connection, id, false, true);
        });
    }

    /** Kiểm thứ tự/giờ/giá cả khi service được gọi nội bộ, không chỉ dựa Bean Validation ở HTTP. */
    private void validate(RouteWriteRequest body) {
        BusinessRules.require(
            body.getStops() != null && body.getStops().size() >= 2 && body.getStops().size() <= 32,
            ErrorCode.ROUTE_INVALID
        );
        HashSet<Long> seen = new HashSet<Long>();
        int previousDeparture = -1;
        BigDecimal previousFare = BigDecimal.ZERO;
        for (RouteStopRequest stop : body.getStops()) {
            BusinessRules.require(
                stop != null &&
                    stop.locationId() > 0 &&
                    seen.add(stop.locationId()) &&
                    stop.arrivalOffsetMinutes() > previousDeparture &&
                    stop.departureOffsetMinutes() >= stop.arrivalOffsetMinutes() &&
                    stop.departureOffsetMinutes() <= 43200 &&
                    stop.cumulativeFare() != null &&
                    stop.cumulativeFare().compareTo(previousFare) >= 0 &&
                    stop.cumulativeFare().scale() <= 2 &&
                    stop.cumulativeFare().precision() - stop.cumulativeFare().scale() <= 10,
                ErrorCode.ROUTE_INVALID
            );
            previousDeparture = stop.departureOffsetMinutes();
            previousFare = stop.cumulativeFare();
        }
        RouteStopRequest first = body.getStops().get(0);
        RouteStopRequest last = body.getStops().get(body.getStops().size() - 1);
        BusinessRules.require(
            first.arrivalOffsetMinutes() == 0 &&
                first.cumulativeFare().signum() == 0 &&
                first.pickupAllowed() &&
                last.dropoffAllowed(),
            ErrorCode.ROUTE_INVALID
        );
    }
}
