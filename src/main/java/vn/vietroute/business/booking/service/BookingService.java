package vn.vietroute.business.booking.service;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.vietroute.auth.repo.UserRoleRepo;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.business.booking.constant.BookingPaymentKind;
import vn.vietroute.business.booking.constant.BookingStatus;
import vn.vietroute.business.booking.dto.request.BookingRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.dto.search.BookingSearch;
import vn.vietroute.business.booking.dto.search.BookingTripSearch;
import vn.vietroute.business.booking.repo.BookingRepo;
import vn.vietroute.business.booking.repo.BookingRepo.PaymentEvent;
import vn.vietroute.business.booking.repo.BookingRepo.Row;
import vn.vietroute.business.booking.repo.BookingRepo.Stop;
import vn.vietroute.business.booking.repo.BookingRepo.Trip;
import vn.vietroute.business.trip.constant.TripStatus;
import vn.vietroute.business.trip.repo.TripSegmentRepo;
import vn.vietroute.business.trip.repo.custom.TripSegmentCustomRepo;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.validation.BusinessRules;

/** Service đơn đặt chỗ: luồng khách/staff giữ guard riêng, transaction/thu tiền/tồn chỗ dùng một triển khai. */
@Service
@RequiredArgsConstructor
public class BookingService {

    private final TripSegmentCustomRepo segmentBatch;
    private final BookingRepo bookings;
    private final TripSegmentRepo capacity;
    private final UserRoleRepo scopes;

    /** Một connection trip → customer (WEB) → booking/segment; PENDING chỉ kiểm chỗ, không giữ chỗ. */
    private BookingResponse create(BookingRequest body, UserPrincipal actor, boolean staff) {
        BusinessRules.require(
            body.getRequestKey() != null &&
                body.getSeats() > 0 &&
                body.getSeats() <= 200 &&
                body.getContactName() != null &&
                !body.getContactName().isBlank() &&
                body.getContactName().length() <= 120 &&
                body.getContactPhone() != null &&
                body.getContactPhone().matches(Regex.PHONE_NUMBER),
            ErrorCode.BOOKING_INVALID
        );
        Trip trip = bookings.trip(body.getTripId(), true);
        Long owner = staff ? null : actor.id();
        String source = staff ? "PHONE" : "WEB";
        if (staff) scopes.require(trip.getScopeId());
        else bookings.requireCustomer(actor.id(), actor.tokenVersion(), true);
        Long previous = bookings.requestId(body.getRequestKey());
        if (previous != null) {
            Row row = bookings.find(previous, owner, true, true);
            BookingResponse existing = row.getResponse();
            BusinessRules.require(
                row.getCreatedBy() == actor.id() &&
                    existing.getTripId() == trip.getId() &&
                    existing.getSource().equals(source) &&
                    existing.getPickupStopId() == body.getPickupStopId() &&
                    existing.getDropoffStopId() == body.getDropoffStopId() &&
                    existing.getSeats() == body.getSeats() &&
                    existing.getContactName().equals(body.getContactName().strip()) &&
                    existing.getContactPhone().equals(body.getContactPhone().strip()),
                ErrorCode.BOOKING_REQUEST_CONFLICT
            );
            return existing;
        }
        Stop pickup = bookings.stop(trip.getId(), body.getPickupStopId());
        Stop dropoff = bookings.stop(trip.getId(), body.getDropoffStopId());
        requireBookable(trip, pickup);
        BusinessRules.require(
            pickup.isPickup() &&
                dropoff.isDropoff() &&
                pickup.getOrder() < dropoff.getOrder() &&
                dropoff.getFare().compareTo(pickup.getFare()) >= 0,
            ErrorCode.BOOKING_INVALID
        );
        BusinessRules.require(
            capacity.available(trip.getId(), pickup.getOrder(), dropoff.getOrder()) >= body.getSeats(),
            ErrorCode.BOOKING_CAPACITY
        );
        if (!staff) bookings.requireNoActive(actor.id());
        long id = bookings.insert(trip, pickup, dropoff, body, owner, source, bookings.source(source), actor.id());
        BookingResponse row = bookings.find(id, owner, false, false).getResponse();
        bookings.event(row, BookingStatus.PENDING, "CREATED", "", BigDecimal.ZERO, null, actor.id());
        return bookings.find(id, owner, false, true).getResponse();
    }

    /** Xác nhận/hủy dùng cùng mutex trip, CAS booking và segment tăng dần, history lỗi rollback. */
    private BookingResponse state(long id, BookingRequest body, UserPrincipal actor, boolean staff) {
        BusinessRules.require(body.getNote() != null && body.getNote().length() <= 2000, ErrorCode.BOOKING_INVALID);
        Long owner = staff ? null : actor.id();
        if (!staff) bookings.requireCustomer(actor.id(), actor.tokenVersion(), false);
        long parent = bookings.parent(id, owner);
        Trip trip = bookings.trip(parent, true);
        BookingResponse row = bookings.find(id, owner, true, false).getResponse();
        BusinessRules.require(
            body.getVersion() != null && body.getVersion() == row.getVersion(),
            ErrorCode.STALE_VERSION
        );
        BookingStatus current = row.getStatus();
        BookingStatus target = body.getStatus();
        Stop pickup = bookings.stop(parent, row.getPickupStopId());
        BusinessRules.require(
            target != null && (staff || target == BookingStatus.CANCELLED),
            ErrorCode.BOOKING_STATE_CONFLICT
        );
        switch (target) {
            case CONFIRMED -> {
                BusinessRules.require(staff && current == BookingStatus.PENDING, ErrorCode.BOOKING_STATE_CONFLICT);
                requireBookable(trip, pickup);
                capacity.change(parent, row.getPickupOrder(), row.getDropoffOrder(), row.getSeats(), true, actor.id());
            }
            case REJECTED -> BusinessRules.require(
                staff && current == BookingStatus.PENDING && body.getNote() != null && !body.getNote().isBlank(),
                ErrorCode.BOOKING_STATE_CONFLICT
            );
            case CANCELLED -> {
                BusinessRules.require(
                    (current == BookingStatus.PENDING || current == BookingStatus.CONFIRMED) &&
                        pickup.getActualDeparture() == null &&
                        body.getNote() != null &&
                        !body.getNote().isBlank(),
                    ErrorCode.BOOKING_STATE_CONFLICT
                );
                if (current == BookingStatus.CONFIRMED) capacity.change(
                    parent,
                    row.getPickupOrder(),
                    row.getDropoffOrder(),
                    row.getSeats(),
                    false,
                    actor.id()
                );
            }
            default -> throw new BusinessException(ErrorCode.BOOKING_STATE_CONFLICT);
        }
        bookings.state(row, target, actor.id());
        bookings.event(row, target, target.name(), body.getNote().strip(), BigDecimal.ZERO, null, actor.id());
        return bookings.find(id, owner, false, true).getResponse();
    }

    /** Tiền thủ công có request UUID riêng; replay cùng actor/nội dung không tăng số tiền lần nữa. */
    private BookingResponse recordPayment(long id, BookingRequest body, UserPrincipal actor) {
        long parent = bookings.parent(id, null);
        bookings.trip(parent, true);
        BookingResponse row = bookings.find(id, null, true, false).getResponse();
        BusinessRules.require(
            body.getKind() != null &&
                body.getRequestKey() != null &&
                body.getAmount() != null &&
                body.getAmount().signum() > 0 &&
                body.getAmount().scale() <= 2 &&
                body.getNote() != null &&
                !body.getNote().isBlank(),
            ErrorCode.BOOKING_INVALID
        );
        PaymentEvent old = bookings.paymentEvent(body.getRequestKey());
        if (old != null) {
            BusinessRules.require(
                old.getBookingId() == id &&
                    old.getActor() == actor.id() &&
                    old.getKind().equals(body.getKind().name()) &&
                    old.getAmount().compareTo(body.getAmount()) == 0 &&
                    old.getNote().equals(body.getNote().strip()),
                ErrorCode.BOOKING_REQUEST_CONFLICT
            );
            return bookings.find(id, null, false, true).getResponse();
        }
        BusinessRules.require(
            body.getVersion() != null && body.getVersion() == row.getVersion(),
            ErrorCode.STALE_VERSION
        );
        BigDecimal paid = row.getPaidAmount(),
            refunded = row.getRefundedAmount();
        if (body.getKind() == BookingPaymentKind.PAYMENT) {
            BusinessRules.require(
                row.getStatus() == BookingStatus.CONFIRMED || row.getStatus() == BookingStatus.COMPLETED,
                ErrorCode.BOOKING_STATE_CONFLICT
            );
            paid = paid.add(body.getAmount());
            BusinessRules.require(paid.compareTo(row.getTotalPrice()) <= 0, ErrorCode.BOOKING_MONEY_INVALID);
        } else {
            BusinessRules.require(row.getStatus() == BookingStatus.CANCELLED, ErrorCode.BOOKING_STATE_CONFLICT);
            refunded = refunded.add(body.getAmount());
            BusinessRules.require(refunded.compareTo(paid) <= 0, ErrorCode.BOOKING_MONEY_INVALID);
        }
        bookings.money(row, paid, refunded, actor.id());
        bookings.event(
            row,
            row.getStatus(),
            body.getKind().name(),
            body.getNote().strip(),
            body.getAmount(),
            body.getRequestKey(),
            actor.id()
        );
        return bookings.find(id, null, false, true).getResponse();
    }

    private void requireBookable(Trip trip, Stop pickup) {
        BusinessRules.require(accepting(trip, pickup, Instant.now()), ErrorCode.BOOKING_STATE_CONFLICT);
    }

    public static boolean accepting(Trip trip, Stop pickup, Instant now) {
        return (
            trip.isAccepting() &&
            pickup.getActualDeparture() == null &&
            (trip.getStatus() == TripStatus.IN_PROGRESS ||
                (trip.getStatus() == TripStatus.OPEN && trip.getDepartureAt().isAfter(now)))
        );
    }

    /** Picker PHONE dùng quyền CREATE trong scope hiện hành, không cần grant GET danh sách chuyến riêng. */
    @Transactional(readOnly = true)
    public PageResponse<BookingTripChoiceResponse> choices(BookingTripSearch query) {
        return bookings.choices(query);
    }

    /** SQL lọc scope trước count/page để không lộ đơn và tổng số nhà xe khác. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> page(BookingSearch query) {
        return bookings.page(query, null);
    }

    /** Chi tiết và lịch sử chỉ được projection sau khi đã kiểm ID cùng scope. */
    @Transactional(readOnly = true)
    public BookingResponse find(long id) {
        return bookings.find(id, null, false, true).getResponse();
    }

    /** PHONE không cần CUSTOMER giả, vẫn PENDING và dùng cùng cơ chế chặng khi xác nhận. */
    @Transactional
    public BookingResponse create(BookingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return create(body, actor, true);
    }

    /** Facade có guard; workflow khóa trip/booking/chặng và ghi lịch sử cùng transaction. */
    @Transactional
    public BookingResponse state(long id, BookingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return state(id, body, actor, true);
    }

    /** Chỉ ghi nhận thu/hoàn thực tế ngoài hệ thống; history/amount/version là một giao dịch. */
    @Transactional
    public BookingResponse payment(long id, BookingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return recordPayment(id, body, actor);
    }

    /** Quyền và owner lọc trước count/page, chỉ tài khoản CUSTOMER hiện hành. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> customerPage(BookingSearch query, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        bookings.requireCustomer(actor.id(), actor.tokenVersion(), false);
        return bookings.page(query, actor.id());
    }

    /** Đọc chi tiết theo owner; ID đơn người khác bị che trước khi trả contact và lịch sử. */
    @Transactional(readOnly = true)
    public BookingResponse customerFind(long id, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        bookings.requireCustomer(actor.id(), actor.tokenVersion(), false);
        return bookings.find(id, actor.id(), false, true).getResponse();
    }

    /** WEB là giá trị server, chụp giá từ stop của trip và ghi PENDING/history cùng transaction. */
    @Transactional
    public BookingResponse customerCreate(BookingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return create(body, actor, false);
    }

    /** Khách chỉ hủy đơn của chính mình trước rời điểm đón, không tự xác nhận hoặc ghi tiền. */
    @Transactional
    public BookingResponse customerState(long id, BookingRequest body, UserPrincipal actor) {
        UserPrincipal.requireCurrent(actor);
        return state(id, body, actor, false);
    }

    /** Khóa trip đã được caller giữ; booking → chặng → lịch sử cùng một transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void cancelTrip(Connection connection, long tripId, String reason, long actor) throws SQLException {
        bookings.lockActiveForTrip(tripId);
        segmentBatch.releaseTrip(connection, tripId, actor);
        bookings.cancelled(tripId, reason, actor);
    }
}
