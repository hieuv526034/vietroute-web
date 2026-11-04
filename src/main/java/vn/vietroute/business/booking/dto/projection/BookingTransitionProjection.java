package vn.vietroute.business.booking.dto.projection;

/** Số đơn khóa, cập nhật và ghi history trong cùng CTE; phải khớp để commit. */
public interface BookingTransitionProjection {
    long getTargetCount();

    long getChangedCount();

    long getLoggedCount();
}
