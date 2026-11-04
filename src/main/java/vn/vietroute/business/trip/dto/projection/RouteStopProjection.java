package vn.vietroute.business.trip.dto.projection;

import java.math.BigDecimal;

/** Điểm tuyến nguồn và trạng thái usable; query chọn cột cho danh sách hoặc kiểm địa điểm. */
public interface RouteStopProjection {
    long getLocationId();

    long getProvinceId();

    int getStopOrder();

    String getNameVi();

    String getNameEn();

    String getAddress();

    BigDecimal getLatitude();

    BigDecimal getLongitude();

    int getArrivalOffsetMinutes();

    int getDepartureOffsetMinutes();

    BigDecimal getCumulativeFare();

    boolean getPickupAllowed();

    boolean getDropoffAllowed();

    boolean getUsable();

    String getProvinceName();

    long getWardId();

    String getWardName();
}
