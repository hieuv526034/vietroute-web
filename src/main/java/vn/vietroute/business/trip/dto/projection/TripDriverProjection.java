package vn.vietroute.business.trip.dto.projection;

/** Tài khoản, tên và điện thoại hồ sơ tài xế; chỉ dùng nội bộ sau khóa hồ sơ. */
public interface TripDriverProjection {
    long getUserId();

    String getName();

    String getPhone();
}
