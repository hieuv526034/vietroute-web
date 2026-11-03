package vn.vietroute.business.trip.service;

import java.math.BigDecimal;
import java.util.HashSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.carrier.dto.response.CarrierChoiceResponse;
import vn.vietroute.business.carrier.dto.search.CarrierSearch;
import vn.vietroute.business.carrier.entity.Carrier;
import vn.vietroute.business.carrier.repo.CarrierRepo;
import vn.vietroute.business.trip.dto.request.RouteRequest;
import vn.vietroute.business.trip.dto.request.RouteStopRequest;
import vn.vietroute.business.trip.dto.response.RouteResponse;
import vn.vietroute.business.trip.repo.RouteCustomRepo;
import vn.vietroute.business.trip.repo.RouteRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.validation.BusinessRules;

/** Mẫu tuyến có version, scope nhà xe và thứ tự điểm; không tự sửa snapshot của chuyến. */
@Service
@RequiredArgsConstructor
public class RouteService {

    private final RouteRepo routes;
    private final CarrierRepo carriers;
    private final JdbcTransactions transactions;

    /** Tổng và trang được lọc trực tiếp ở PostgreSQL. */
    private final RouteCustomRepo routeBatch;

    public PageResponse<RouteResponse> page(CarrierSearch query) {
        return routes.page(query);
    }

    @Transactional(readOnly = true)
    public RouteResponse find(long id) {
        return routes.find(id, false, true);
    }

    /** Picker CREATE không bắt thêm quyền đọc hồ sơ nhà xe hoặc quyền khác scope. */
    public PageResponse<CarrierChoiceResponse> choices(CarrierSearch query) {
        query.setActive(true);
        PageResponse<Carrier> page = carriers.choices(query);
        return new PageResponse<>(
            page.getContent().stream().map(CarrierChoiceResponse::from).toList(),
            page.getPage(),
            page.getSize(),
            page.getTotalElements(),
            page.getTotalPages(),
            page.isHasNext(),
            page.isHasPrevious()
        );
    }

    /** Thứ tự khóa parent nhà xe → tuyến → địa điểm; tạo/cập nhật điểm trong cùng transaction. */
    public RouteResponse create(RouteRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        validate(body);
        return transactions.execute(connection -> {
            Carrier carrier = carriers.carrier(body.getCarrierId(), true);
            BusinessRules.require(carrier.isActive(), ErrorCode.CONFLICT);
            routes.validateLocations(body);
            long id = routes.insert(carrier.getScopeId(), body, actor.id());
            routeBatch.replaceStops(connection, id, carrier.getScopeId(), body, actor.id());
            return routes.find(id, false, true);
        });
    }

    /** Hai chiều được sửa riêng; code/nhà xe không được chuyển qua payload cập nhật. */
    public RouteResponse update(long id, RouteRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        validate(body);
        BusinessRules.require(body.getVersion() != null, ErrorCode.INVALID_REQUEST);
        return transactions.execute(connection -> {
            RouteResponse existing = routes.find(id, false, false);
            BusinessRules.require(
                existing.getCarrierId() == body.getCarrierId() && existing.getCode().equals(body.getCode()),
                ErrorCode.INVALID_REQUEST
            );
            Carrier carrier = carriers.carrier(existing.getCarrierId(), true);
            RouteResponse locked = routes.find(id, true, false);
            BusinessRules.require(locked.getVersion() == body.getVersion(), ErrorCode.STALE_VERSION);
            BusinessRules.require(!body.isActive() || carrier.isActive(), ErrorCode.CONFLICT);
            routes.validateLocations(body);
            routes.update(id, body, actor.id());
            routeBatch.replaceStops(connection, id, existing.getScopeId(), body, actor.id());
            return routes.find(id, false, true);
        });
    }

    /** Kiểm thứ tự/giờ/giá cả khi service được gọi nội bộ, không chỉ dựa Bean Validation ở HTTP. */
    private void validate(RouteRequest body) {
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
                    stop.getLocationId() > 0 &&
                    seen.add(stop.getLocationId()) &&
                    stop.getArrivalOffsetMinutes() > previousDeparture &&
                    stop.getDepartureOffsetMinutes() >= stop.getArrivalOffsetMinutes() &&
                    stop.getDepartureOffsetMinutes() <= 43200 &&
                    stop.getCumulativeFare() != null &&
                    stop.getCumulativeFare().compareTo(previousFare) >= 0 &&
                    stop.getCumulativeFare().scale() <= 2 &&
                    stop.getCumulativeFare().precision() - stop.getCumulativeFare().scale() <= 10,
                ErrorCode.ROUTE_INVALID
            );
            previousDeparture = stop.getDepartureOffsetMinutes();
            previousFare = stop.getCumulativeFare();
        }
        RouteStopRequest first = body.getStops().get(0);
        RouteStopRequest last = body.getStops().get(body.getStops().size() - 1);
        BusinessRules.require(
            first.getArrivalOffsetMinutes() == 0 &&
                first.getCumulativeFare().signum() == 0 &&
                first.isPickupAllowed() &&
                last.isDropoffAllowed(),
            ErrorCode.ROUTE_INVALID
        );
    }
}
