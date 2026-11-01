package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import vn.vietroute.business.booking.constant.BookingPaymentKind;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.carrier.dto.request.CarrierRequest;
import vn.vietroute.business.route.dto.request.RouteRequest;
import vn.vietroute.business.route.dto.request.RouteStopRequest;
import vn.vietroute.business.tracking.dto.request.TrackingRequest;
import vn.vietroute.business.trip.constant.TripProgressAction;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.dto.request.TripRequest;
import vn.vietroute.common.validation.RequestGroups;

/** DTO dùng chung vẫn giữ biên bắt buộc theo từng thao tác, kể cả validation điểm con. */
class BusinessRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void start() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void close() {
        factory.close();
    }

    @Test
    void createNeedsIdentityWhileUpdateNeedsVersionOnTheSameRequest() {
        CarrierRequest body = new CarrierRequest();
        body.setName("Nhà xe kiểm thử");
        body.setPhone("0901234567");
        assertEquals(Set.of("code"), fields(body, RequestGroups.Create.class));
        assertEquals(Set.of("version"), fields(body, RequestGroups.Update.class));
        body.setCode("CARRIER01");
        assertTrue(fields(body, RequestGroups.Create.class).isEmpty());
        body.setCode(null);
        body.setVersion(0L);
        assertTrue(fields(body, RequestGroups.Update.class).isEmpty());
        body.setVersion(-1L);
        assertEquals(Set.of("version"), fields(body, RequestGroups.Update.class));
    }

    @Test
    void tripStateNoticeAndProgressDoNotRequireCreationOrAssignmentFields() {
        TripRequest body = new TripRequest();
        body.setVersion(0L);
        body.setNote("");
        body.setStatus(TripStatus.OPEN);
        assertTrue(fields(body, RequestGroups.State.class).isEmpty());
        assertEquals(Set.of("note"), fields(body, RequestGroups.Notice.class));
        body.setAction(TripProgressAction.START);
        body.setStopOrder(1);
        assertTrue(fields(body, RequestGroups.Progress.class).isEmpty());
        body.setVersion(null);
        assertEquals(Set.of("version"), fields(body, RequestGroups.Progress.class));
    }

    @Test
    void paymentAndTrackingStopRetainTheirOwnRequiredFields() {
        BookingRequest booking = new BookingRequest();
        booking.setStatus(BookingStatus.CANCELLED);
        booking.setVersion(0L);
        booking.setNote("Khách yêu cầu hủy");
        assertTrue(fields(booking, RequestGroups.State.class).isEmpty());
        assertEquals(Set.of("amount", "kind", "requestKey"), fields(booking, RequestGroups.Payment.class));
        booking.setKind(BookingPaymentKind.REFUND);
        booking.setAmount(BigDecimal.ONE);
        booking.setRequestKey(UUID.randomUUID());
        assertTrue(fields(booking, RequestGroups.Payment.class).isEmpty());
        TrackingRequest tracking = new TrackingRequest();
        tracking.setTripVersion(0L);
        assertTrue(fields(tracking, RequestGroups.Create.class).isEmpty());
        assertEquals(Set.of("sessionKey"), fields(tracking, RequestGroups.Delete.class));
        tracking.setSessionKey(UUID.randomUUID());
        assertTrue(fields(tracking, RequestGroups.Delete.class).isEmpty());
    }

    @Test
    void groupedCreateStillValidatesEveryNestedRouteStop() {
        RouteRequest route = new RouteRequest();
        route.setCode("ROUTE01");
        route.setCarrierId(1L);
        route.setNameVi("Tuyến kiểm thử");
        route.setNameEn("Test route");
        RouteStopRequest first = new RouteStopRequest(1, 0, 0, BigDecimal.ZERO, true, false);
        RouteStopRequest last = new RouteStopRequest(2, 60, 60, BigDecimal.TEN.negate(), false, true);
        route.setStops(List.of(first, last));
        assertEquals(Set.of("stops[1].cumulativeFare"), fields(route, RequestGroups.Create.class));
        last.setCumulativeFare(BigDecimal.TEN);
        assertTrue(fields(route, RequestGroups.Create.class).isEmpty());
        assertEquals(Set.of("version"), fields(route, RequestGroups.Update.class));
    }

    private static Set<String> fields(Object value, Class<?> group) {
        return validator
            .validate(value, group)
            .stream()
            .map(violation -> violation.getPropertyPath().toString())
            .collect(Collectors.toSet());
    }
}
