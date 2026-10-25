package vn.vietroute.business.booking.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.AccessActors;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;
import vn.vietroute.business.booking.dto.request.BookingCreateRequest;
import vn.vietroute.business.booking.dto.request.BookingSearchRequest;
import vn.vietroute.business.booking.dto.request.BookingStateRequest;
import vn.vietroute.business.booking.dto.response.BookingResponse;
import vn.vietroute.business.booking.repository.BookingRepository;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Đơn của chính CUSTOMER; không nhận owner/customer/source từ client hoặc dùng scope staff. */
@Service
@RequiredArgsConstructor
@AccessResource(Groups.MY_BOOKING)
public class CustomerBookingService {

    private final BookingRepository bookings;
    private final BookingWorkflow workflow;
    private final JdbcTransactions transactions;

    /** Quyền và owner lọc trước count/page, chỉ tài khoản CUSTOMER hiện hành. */
    @AccessOperation(action = PermissionAction.READ)
    public PageResponse<BookingResponse> page(BookingSearchRequest query, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), false);
            return bookings.page(connection, query, actor.id());
        });
    }

    /** Đọc chi tiết theo owner; ID đơn người khác bị che trước khi trả contact và lịch sử. */
    @AccessOperation(action = PermissionAction.READ)
    public BookingResponse find(long id, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> {
            bookings.requireCustomer(connection, actor.id(), actor.tokenVersion(), false);
            return bookings.find(connection, id, actor.id(), PermissionAction.READ, false, true).response();
        });
    }

    /** WEB là giá trị server, chụp giá từ stop của trip và ghi PENDING/history cùng transaction. */
    @AccessOperation(action = PermissionAction.CREATE)
    public BookingResponse create(BookingCreateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> workflow.create(connection, body, actor, false));
    }

    /** Khách chỉ hủy đơn của chính mình trước rời điểm đón, không tự xác nhận hoặc ghi tiền. */
    @AccessOperation(action = PermissionAction.UPDATE)
    public BookingResponse state(long id, BookingStateRequest body, UserPrincipal actor) {
        AccessActors.requireCurrent(actor);
        return transactions.execute(connection -> workflow.state(connection, id, body, actor, false));
    }
}
