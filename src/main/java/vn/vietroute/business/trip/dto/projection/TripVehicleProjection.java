package vn.vietroute.business.trip.dto.projection;

/** Xe và tên loại xe sau kiểm nhà xe/trạng thái; không tải entity hoặc quan hệ thừa. */
public interface TripVehicleProjection {
    String getPlateNumber();

    long getVehicleTypeId();

    String getVehicleTypeNameVi();

    String getVehicleTypeNameEn();

    int getCapacity();
}
