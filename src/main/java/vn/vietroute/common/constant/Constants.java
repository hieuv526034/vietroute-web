package vn.vietroute.common.constant;

/** Các hằng số ổn định dùng chung; mã lỗi và mã thành công nằm ở enum riêng cùng package. */
public final class Constants {

    public static final String API_PREFIX = "/api/v1";

    public static final String CODE_PATTERN = "^[A-Z][A-Z0-9]{2,39}$";

    public static final String USERNAME_PATTERN = "^[A-Za-z][A-Za-z0-9]{2,31}$";

    public static final String AUTH_COOKIE = "VIETROUTE_ACCESS";

    public static final String REQUEST_ID = "X-Request-ID";

    public static final String TRACE_ATTRIBUTE = "vietroute.traceId";

    private Constants() {}

    /** Chỉ hỗ trợ hai ngôn ngữ trong hợp đồng API hiện tại. */
    public enum LocaleCode {
        vi,
        en,
    }

    public enum CatalogType {
        VEHICLE_TYPE,
        BOOKING_SOURCE,
    }
}
