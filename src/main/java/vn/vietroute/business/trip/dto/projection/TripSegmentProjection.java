package vn.vietroute.business.trip.dto.projection;

/** Tồn chỗ từng đoạn và tổng hợp hoàn chỗ; versionChanges là số đơn chạm đoạn. */
public interface TripSegmentProjection {
    int getSegmentOrder();

    int getCapacity();

    int getReservedSeats();

    long getSeatsToRelease();

    long getVersionChanges();

    int getRemainingSeats();
}
