package vn.vietroute.business.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.booking.dto.request.BookingCreateRequest;
import vn.vietroute.business.booking.dto.request.BookingPaymentRequest;
import vn.vietroute.business.booking.dto.request.BookingSearchRequest;
import vn.vietroute.business.booking.dto.request.BookingStateRequest;
import vn.vietroute.business.booking.dto.request.BookingTripSearchRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.dto.response.BookingTripChoiceResponse;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Staff thao tác đơn WEB/PHONE trong scope hiện hành; không nhận actor/scope/giá client. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.BOOKING)
public class StaffBookingService {

    private final BookingRepository bookings;
    private final BookingWorkflow workflow;
    private final JdbcTransactions transactions;

    /** Picker PHONE dùng quyền CREATE trong scope hiện hành, không phụ thuộc TRIP_READ. */
    @AccessOperation(action = PermissionAction.CREATE)
    public PageResponse<BookingTripChoiceResponse> choices(BookingTripSearchRequest query) {
        return transactions.execute(connection -> bookings.choices(connection, query));
    }

    /** SQL lọc scope trước count/page để không lộ đơn và tổng số nhà xe khác. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<BookingResponse> page(BookingSearchRequest query) {
        return transactions.execute(connection -> bookings.page(connection, query, null));
    }

    /** Chi tiết và lịch sử chỉ được projection sau khi đã kiểm ID cùng scope. */
    @AccessOperation(action = PermissionAction.READ)
    public BookingResponse find(long id) {
        return transactions.execute(connection ->
            bookings.find(connection, id, null, PermissionAction.READ, false, true).response()
        );
    }

    /** PHONE không cần CUSTOMER giả, vẫn PENDING và dùng cùng cơ chế chặng khi xác nhận. */
    @AccessOperation(action = PermissionAction.CREATE)
    public BookingResponse create(BookingCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> workflow.create(connection, body, actor, true));
    }

    /** Facade có guard; workflow khóa trip/booking/chặng và ghi lịch sử cùng transaction. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public BookingResponse state(long id, BookingStateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> workflow.state(connection, id, body, actor, true));
    }

    /** Chỉ ghi nhận thu/hoàn thực tế ngoài hệ thống; history/amount/version là một giao dịch. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public BookingResponse payment(long id, BookingPaymentRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> workflow.payment(connection, id, body, actor));
    }
}
