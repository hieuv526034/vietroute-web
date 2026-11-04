package vn.vietroute.business.trip.dto.projection;

import java.math.BigDecimal;
import java.time.Instant;

/** Projection tìm chuyến/chi tiết công khai, không chứa dữ liệu riêng của tài xế hoặc khách. */
public interface JourneyProjection {
    long getId();

    String getCode();

    long getCarrierId();

    String getCarrierName();

    String getVehicleTypeNameVi();

    String getVehicleTypeNameEn();

    long getPickupStopId();

    long getDropoffStopId();

    String getPickupNameVi();

    String getPickupNameEn();

    String getDropoffNameVi();

    String getDropoffNameEn();

    Instant getPickupAt();

    Instant getDropoffAt();

    BigDecimal getUnitPrice();

    int getAvailableSeats();

    String getStatus();

    String getCarrierPhone();

    String getNameVi();

    String getNameEn();

    String getPlateNumber();

    Instant getDepartureAt();

    Instant getEndAt();

    String getOperationNote();
}
