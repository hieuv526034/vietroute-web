package vn.vietroute.business.booking.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.dto.request.BookingCreateRequest;
import vn.vietroute.business.booking.dto.request.BookingPaymentRequest;
import vn.vietroute.business.booking.dto.request.BookingStateRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.entity.BookingStatus;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.business.booking.repository.BookingRepository.PaymentEvent;
import vn.vietroute.business.booking.repository.BookingRepository.Row;
import vn.vietroute.business.booking.repository.BookingRepository.Stop;
import vn.vietroute.business.booking.repository.BookingRepository.Trip;
import vn.vietroute.business.booking.repository.TripCapacityRepository;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Luồng dùng chung package-private; chỉ gọi sau guard của facade khách/staff, không phải API bỏ qua quyền. */
@Component
@RequiredArgsConstructor
class BookingWorkflow {

    private final BookingRepository bookings;
    private final TripCapacityRepository capacity;
    private final UserRoleRepo scopes;

    /** Một connection trip → customer (WEB) → booking/segment; PENDING chỉ kiểm chỗ, không giữ chỗ. */
    BookingResponse create(Connection connection, BookingCreateRequest body, UserPrincipal actor, boolean staff)
        throws SQLException {
        BusinessRules.require(
            body.requestKey() != null &&
                body.seats() > 0 &&
                body.seats() <= 200 &&
                body.contactName() != null &&
                !body.contactName().isBlank() &&
                body.contactName().length() <= 120 &&
                body.contactPhone() != null &&
                body.contactPhone().matches(Regex.PHONE_NUMBER),
            ErrorCode.BOOKING_INVALID
        );
        Trip trip = bookings.trip(connection, body.tripId(), true);
        Long owner = staff ? null : actor.id();
        String source = staff ? "PHONE" : "WEB";
        if (staff) scopes.require(connection, trip.scopeId());
        else bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), true);
        Long previous = bookings.requestId(connection, body.requestKey());
        if (previous != null) {
            Row row = bookings.find(connection, previous, owner, true, true);
            BookingResponse existing = row.response();
            BusinessRules.require(
                row.createdBy() == actor.id() &&
                    existing.tripId() == trip.id() &&
                    existing.source().equals(source) &&
                    existing.pickupStopId() == body.pickupStopId() &&
                    existing.dropoffStopId() == body.dropoffStopId() &&
                    existing.seats() == body.seats() &&
                    existing.contactName().equals(body.contactName().strip()) &&
                    existing.contactPhone().equals(body.contactPhone().strip()),
                ErrorCode.BOOKING_REQUEST_CONFLICT
            );
            return existing;
        }
        Stop pickup = bookings.stop(connection, trip.id(), body.pickupStopId());
        Stop dropoff = bookings.stop(connection, trip.id(), body.dropoffStopId());
        requireBookable(trip, pickup);
        BusinessRules.require(
            pickup.pickup() &&
                dropoff.dropoff() &&
                pickup.order() < dropoff.order() &&
                dropoff.fare().compareTo(pickup.fare()) >= 0,
            ErrorCode.BOOKING_INVALID
        );
        BusinessRules.require(
            capacity.available(connection, trip.id(), pickup.order(), dropoff.order()) >= body.seats(),
            ErrorCode.BOOKING_CAPACITY
        );
        if (!staff) bookings.requireNoActive(connection, actor.id());
        long id = bookings.insert(
            connection,
            trip,
            pickup,
            dropoff,
            body,
            owner,
            source,
            bookings.source(connection, source),
            actor.id()
        );
        BookingResponse row = bookings.find(connection, id, owner, false, false).response();
        bookings.event(connection, row, BookingStatus.PENDING, "CREATED", "", BigDecimal.ZERO, null, actor.id());
        return bookings.find(connection, id, owner, false, true).response();
    }

    /** Xác nhận/hủy dùng cùng mutex trip, CAS booking và segment tăng dần, history lỗi rollback. */
    BookingResponse state(Connection connection, long id, BookingStateRequest body, UserPrincipal actor, boolean staff)
        throws SQLException {
        BusinessRules.require(body.note() != null && body.note().length() <= 2000, ErrorCode.BOOKING_INVALID);
        Long owner = staff ? null : actor.id();
        if (!staff) bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), false);
        long parent = bookings.parent(connection, id, owner);
        Trip trip = bookings.trip(connection, parent, true);
        BookingResponse row = bookings.find(connection, id, owner, true, false).response();
        BusinessRules.require(body.version() != null && body.version() == row.version(), ErrorCode.STALE_VERSION);
        BookingStatus current = row.status();
        BookingStatus target = body.status();
        Stop pickup = bookings.stop(connection, parent, row.pickupStopId());
        BusinessRules.require(
            target != null && (staff || target == BookingStatus.CANCELLED),
            ErrorCode.BOOKING_STATE_CONFLICT
        );
        switch (target) {
            case CONFIRMED -> {
                BusinessRules.require(staff && current == BookingStatus.PENDING, ErrorCode.BOOKING_STATE_CONFLICT);
                requireBookable(trip, pickup);
                capacity.change(
                    connection,
                    parent,
                    row.pickupOrder(),
                    row.dropoffOrder(),
                    row.seats(),
                    true,
                    actor.id()
                );
            }
            case REJECTED -> BusinessRules.require(
                staff && current == BookingStatus.PENDING && body.note() != null && !body.note().isBlank(),
                ErrorCode.BOOKING_STATE_CONFLICT
            );
            case CANCELLED -> {
                BusinessRules.require(
                    (current == BookingStatus.PENDING || current == BookingStatus.CONFIRMED) &&
                        pickup.actualDeparture() == null &&
                        body.note() != null &&
                        !body.note().isBlank(),
                    ErrorCode.BOOKING_STATE_CONFLICT
                );
                if (current == BookingStatus.CONFIRMED) capacity.change(
                    connection,
                    parent,
                    row.pickupOrder(),
                    row.dropoffOrder(),
                    row.seats(),
                    false,
                    actor.id()
                );
            }
            default -> throw new BusinessException(ErrorCode.BOOKING_STATE_CONFLICT);
        }
        bookings.state(connection, row, target, actor.id());
        bookings.event(connection, row, target, target.name(), body.note().strip(), BigDecimal.ZERO, null, actor.id());
        return bookings.find(connection, id, owner, false, true).response();
    }

    /** Tiền thủ công có request UUID riêng; replay cùng actor/nội dung không tăng số tiền lần nữa. */
    BookingResponse payment(Connection connection, long id, BookingPaymentRequest body, UserPrincipal actor)
        throws SQLException {
        long parent = bookings.parent(connection, id, null);
        bookings.trip(connection, parent, true);
        BookingResponse row = bookings.find(connection, id, null, true, false).response();
        BusinessRules.require(
            body.kind() != null &&
                body.requestKey() != null &&
                body.amount() != null &&
                body.amount().signum() > 0 &&
                body.amount().scale() <= 2 &&
                body.note() != null &&
                !body.note().isBlank(),
            ErrorCode.BOOKING_INVALID
        );
        PaymentEvent old = bookings.paymentEvent(connection, body.requestKey());
        if (old != null) {
            BusinessRules.require(
                old.bookingId() == id &&
                    old.actor() == actor.id() &&
                    old.kind().equals(body.kind().name()) &&
                    old.amount().compareTo(body.amount()) == 0 &&
                    old.note().equals(body.note().strip()),
                ErrorCode.BOOKING_REQUEST_CONFLICT
            );
            return bookings.find(connection, id, null, false, true).response();
        }
        BusinessRules.require(body.version() != null && body.version() == row.version(), ErrorCode.STALE_VERSION);
        BigDecimal paid = row.paidAmount(),
            refunded = row.refundedAmount();
        if (body.kind() == BookingPaymentRequest.Kind.PAYMENT) {
            BusinessRules.require(
                row.status() == BookingStatus.CONFIRMED || row.status() == BookingStatus.COMPLETED,
                ErrorCode.BOOKING_STATE_CONFLICT
            );
            paid = paid.add(body.amount());
            BusinessRules.require(paid.compareTo(row.totalPrice()) <= 0, ErrorCode.BOOKING_MONEY_INVALID);
        } else {
            BusinessRules.require(row.status() == BookingStatus.CANCELLED, ErrorCode.BOOKING_STATE_CONFLICT);
            refunded = refunded.add(body.amount());
            BusinessRules.require(refunded.compareTo(paid) <= 0, ErrorCode.BOOKING_MONEY_INVALID);
        }
        bookings.money(connection, row, paid, refunded, actor.id());
        bookings.event(
            connection,
            row,
            row.status(),
            body.kind().name(),
            body.note().strip(),
            body.amount(),
            body.requestKey(),
            actor.id()
        );
        return bookings.find(connection, id, null, false, true).response();
    }

    private void requireBookable(Trip trip, Stop pickup) {
        BusinessRules.require(
            BookingAvailability.accepting(trip, pickup, Instant.now()),
            ErrorCode.BOOKING_STATE_CONFLICT
        );
    }
}
