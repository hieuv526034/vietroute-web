package vn.vietroute.business.trip.dto.projection;

import java.math.BigDecimal;
import java.time.Instant;

/** Snapshot điểm chuyến, giờ UTC và giá cộng dồn; query chỉ đọc cột cần dùng. */
public interface TripStopProjection {
    long getId();

    long getLocationId();

    long getProvinceId();

    int getStopOrder();

    String getNameVi();

    String getNameEn();

    String getAddress();

    BigDecimal getLatitude();

    BigDecimal getLongitude();

    Instant getScheduledArrivalAt();

    Instant getScheduledDepartureAt();

    Instant getActualArrivalAt();

    Instant getActualDepartureAt();

    BigDecimal getCumulativeFare();

    boolean getPickupAllowed();

    boolean getDropoffAllowed();
}
