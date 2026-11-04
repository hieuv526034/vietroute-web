package vn.vietroute.business.trip.dto.projection;

/** ID, mã và tên chọn nhà xe/tuyến/xe/tài xế; capacity chỉ có ở lựa chọn xe. */
public interface TripChoiceProjection {
    long getId();

    String getCode();

    String getName();

    Integer getCapacity();
}
