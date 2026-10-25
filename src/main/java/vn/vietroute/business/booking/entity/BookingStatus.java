package vn.vietroute.business.booking.entity;

/** Trạng thái đơn độc lập tiền đã thu; hoàn tiền là thao tác thủ công riêng. */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED,
    COMPLETED,
}
