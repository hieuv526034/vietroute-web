package vn.vietroute.business.catalog.constant;

/** Mã nhóm danh mục nền dùng trong nghiệp vụ; nhóm bổ sung vẫn được định nghĩa bằng dữ liệu Category. */
public final class CategoryRefType {

    /** Loại xe và số chỗ gợi ý. */
    public static final String VEHICLE_TYPE = "VEHICLE_TYPE";

    /** Nguồn tiếp nhận đơn đặt chỗ. */
    public static final String BOOKING_SOURCE = "BOOKING_SOURCE";

    /** Đơn vị hành chính cấp tỉnh/thành phố. */
    public static final String PROVINCE = "PROVINCE";

    /** Đơn vị hành chính cấp xã/phường, có cha là tỉnh/thành phố. */
    public static final String WARD = "WARD";

    /** Điểm đón/trả tĩnh, có cha là tỉnh/thành phố. */
    public static final String LOCATION = "LOCATION";

    private CategoryRefType() {}
}
