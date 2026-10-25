package vn.vietroute.business.trip.entity;

/** Vòng đời chuyến không đi lùi; hủy chỉ khi chưa chạy. */
public enum TripStatus {
    DRAFT,
    OPEN,
    IN_PROGRESS,
    COMPLETED,
    CANCELLED,
}
