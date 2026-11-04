package vn.vietroute.business.trip.dto.projection;

/** Thông tin mẫu tuyến để tạo snapshot chuyến; khóa do query và transaction caller giữ. */
public interface TripTemplateProjection {
    long getId();

    long getCarrierId();

    long getScopeId();

    String getNameVi();

    String getNameEn();

    boolean getActive();
}
