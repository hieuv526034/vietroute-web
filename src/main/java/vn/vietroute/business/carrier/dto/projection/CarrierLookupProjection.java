package vn.vietroute.business.carrier.dto.projection;

/** Thông tin nhà xe tối thiểu cho public lookup và picker báo cáo; API vẫn dùng DTO riêng. */
public interface CarrierLookupProjection {
    long getId();

    String getCode();

    String getName();

    String getPhone();

    String getDescription();

    boolean getActive();
}
