package vn.vietroute.common.constant;

/**
 * Mã ổn định dùng chung cho danh bạ chức năng và registry quyền.
 * Hằng chuỗi compile-time dùng được trong annotation; mapping user/role/quyền vẫn đọc từ DB.
 * Không đổi mã đã lưu chỉ bằng sửa hằng số: cần migration tương thích và giữ quyền hiện có.
 */
public final class AccessConstants {

    /** Trần an toàn cho API danh sách tương thích; quota vận hành có thể cấu hình thấp hơn. */
    public static final class Limits {

        public static final int REGISTRY_MAX = 2048;

        public static final int POLICY_MAX = 1024;

        private Limits() {}
    }

    private AccessConstants() {}

    /** Nhóm mô tả/tài nguyên hiện tại; chưa biểu diễn quan hệ cha-con của cây menu. */
    public static final class Groups {

        public static final String GENERAL = "GENERAL";

        public static final String AUTH = "AUTH";

        public static final String PROFILE = "PROFILE";

        public static final String ACCESS = "ACCESS";

        public static final String ACCOUNTS = "ACCOUNTS";

        public static final String ACCOUNT = "ACCOUNT";

        public static final String CATALOG = "CATALOG";

        public static final String VEHICLE_TYPE = "VEHICLE_TYPE";

        public static final String BOOKING_SOURCE = "BOOKING_SOURCE";

        public static final String PROVINCE = "PROVINCE";

        public static final String CARRIER = "CARRIER";

        public static final String VEHICLE = "VEHICLE";

        public static final String DRIVER = "DRIVER";

        public static final String ROUTE = "ROUTE";

        public static final String TRIP = "TRIP";

        public static final String DRIVER_TRIP = "DRIVER_TRIP";
        public static final String BOOKING = "BOOKING";
        public static final String MY_BOOKING = "MY_BOOKING";

        public static final String REPORT = "REPORT";

        public static final String DEMO = "DEMO";

        public static final String LOCATION = "LOCATION";

        public static final String MEDIA = "MEDIA";

        public static final String SYSTEM = "SYSTEM";

        public static final String API = "API";

        public static final String JOB = "JOB";

        public static final String MAIL = "MAIL";

        public static final String CONFIGURATION = "CONFIGURATION";

        public static final String JOURNAL = "JOURNAL";

        private Groups() {}
    }

    /** Mã cây chức năng ổn định; quan hệ cha/con do AccessFeatures khai báo, không phải role. */
    public static final class Functions {

        public static final String REPORT = "REPORT";

        public static final String BOOKING = "BOOKING";
        public static final String MY_BOOKING = "MY_BOOKING";

        public static final String VEHICLE_TYPE = "VEHICLE_TYPE";

        public static final String BOOKING_SOURCE = "BOOKING_SOURCE";

        public static final String PROVINCE = "PROVINCE";

        public static final String BUSINESS = "BUSINESS";

        public static final String TRIP = "TRIP";

        public static final String DRIVER_TRIP = "DRIVER_TRIP";

        public static final String ROUTE = "ROUTE";

        public static final String CARRIER = "CARRIER";

        public static final String VEHICLE = "VEHICLE";

        public static final String DRIVER = "DRIVER";

        public static final String DEMO = "DEMO";

        public static final String LOCATION = "LOCATION";

        public static final String SYSTEM = "SYSTEM";

        public static final String AUTHORIZATION = "AUTHORIZATION";

        public static final String CATALOG = "CATALOG";

        public static final String MEDIA = "MEDIA";

        public static final String JOBS = "JOBS";

        public static final String MONITORING = "MONITORING";

        public static final String CONFIGURATION = "CONFIGURATION";

        public static final String JOURNAL = "JOURNAL";

        public static final String MAIL = "MAIL";

        public static final String ACCESS = "ACCESS";

        public static final String ACCOUNTS = "ACCOUNTS";

        private Functions() {}
    }

    /** Mã quyền lõi dùng chung giữa AccessFunction và Permission; không giới hạn registry tùy chỉnh trong DB. */
    public static final class Permissions {

        public static final String ACCOUNT_VIEW = "ACCOUNT_VIEW";
        public static final String ACCOUNT_READ = "ACCOUNT_READ";
        public static final String ACCOUNT_CREATE = "ACCOUNT_CREATE";
        public static final String ACCOUNT_UPDATE = "ACCOUNT_UPDATE";

        public static final String CATALOG_READ = Groups.CATALOG + "_READ";

        public static final String CATALOG_CREATE = Groups.CATALOG + "_CREATE";

        public static final String CATALOG_UPDATE = Groups.CATALOG + "_UPDATE";

        public static final String CATALOG_DELETE = Groups.CATALOG + "_DELETE";

        /** Tương thích dữ liệu cũ: chỉ tạo/sửa danh mục, không bao gồm đọc hoặc xóa. */
        public static final String CATALOG_WRITE = Groups.CATALOG + "_WRITE";

        public static final String ACCESS_READ = Groups.ACCESS + "_READ";

        public static final String ACCESS_CREATE = Groups.ACCESS + "_CREATE";

        public static final String ACCESS_UPDATE = Groups.ACCESS + "_UPDATE";

        public static final String ACCESS_DELETE = Groups.ACCESS + "_DELETE";

        public static final String ACCESS_ASSIGN = Groups.ACCESS + "_ASSIGN";

        /** Tương thích dữ liệu cũ: chỉ thao tác gán quyền, giữ nguyên giới hạn ủy quyền. */
        public static final String ACCESS_WRITE = Groups.ACCESS + "_WRITE";

        public static final String JOB_READ = Groups.JOB + "_READ";

        public static final String JOB_RETRY = Groups.JOB + "_RETRY";

        public static final String MAIL_SEND = Groups.MAIL + "_SEND";

        public static final String SYSTEM_READ = Groups.SYSTEM + "_READ";

        public static final String MEDIA_READ = Groups.MEDIA + "_READ";

        public static final String MEDIA_CREATE = Groups.MEDIA + "_CREATE";

        public static final String MEDIA_UPDATE = Groups.MEDIA + "_UPDATE";

        public static final String MEDIA_DELETE = Groups.MEDIA + "_DELETE";

        public static final String CATALOG_VIEW = "CATALOG_VIEW";

        public static final String MEDIA_VIEW = "MEDIA_VIEW";

        public static final String ACCESS_VIEW = "ACCESS_VIEW";

        public static final String JOB_VIEW = "JOB_VIEW";

        public static final String SYSTEM_VIEW = "SYSTEM_VIEW";

        public static final String CONFIGURATION_VIEW = "CONFIGURATION_VIEW";

        public static final String CONFIGURATION_READ = "CONFIGURATION_READ";

        public static final String JOURNAL_VIEW = "JOURNAL_VIEW";

        private Permissions() {}
    }
}
