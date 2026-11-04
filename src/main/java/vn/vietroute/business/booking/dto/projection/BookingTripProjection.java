package vn.vietroute.business.booking.dto.projection;

import java.time.Instant;

/** Chuyến dùng trong booking và picker; query chọn đúng cột cho từng luồng. */
public interface BookingTripProjection {
    long getId();

    String getCode();

    String getCarrierName();

    String getNameVi();

    String getNameEn();

    Instant getDepartureAt();

    long getCarrierId();

    long getScopeId();

    String getStatus();

    boolean getAccepting();
}
