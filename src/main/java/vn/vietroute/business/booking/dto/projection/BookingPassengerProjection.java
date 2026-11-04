package vn.vietroute.business.booking.dto.projection;

/** Danh sách hành khách theo điểm; chỉ đọc sau kiểm quyền chuyến và phân công tài xế. */
public interface BookingPassengerProjection {
    String getBookingCode();

    int getPickupOrder();

    int getDropoffOrder();

    String getPickupNameVi();

    String getPickupNameEn();

    String getDropoffNameVi();

    String getDropoffNameEn();

    int getSeats();

    String getContactName();

    String getContactPhone();

    String getStatus();
}
