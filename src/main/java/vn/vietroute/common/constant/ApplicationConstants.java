package vn.vietroute.common.constant;

/**
 * Danh mục hằng số ứng dụng: key cấu hình, route/matcher, regex và hợp đồng HTTP.
 * Chỉ lưu TÊN key ở đây; giá trị môi trường và secrets do Settings đọc lúc khởi động.
 * Routes/Regex dùng hằng compile-time để annotation Spring/Validation tham chiếu trực tiếp.
 */
public final class ApplicationConstants {

    public static final String NAME = "VietRoute";

    private ApplicationConstants() {}

    /** Mỗi biến môi trường khai báo đúng một lần; không tạo toString chứa giá trị cấu hình. */
    public enum Env {
        // Môi trường, JWT và tài khoản bootstrap.
        ENV("VIETROUTE_ENV"),
        COOKIE_SECURE("VIETROUTE_COOKIE_SECURE"),
        JWT_SECRET("VIETROUTE_JWT_SECRET"),
        BOOTSTRAP_ADMIN_USERNAME("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME"),
        BOOTSTRAP_ADMIN_PASSWORD("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD"),

        // Database và pool kết nối.
        DB_INIT_ENABLED("VIETROUTE_DB_INIT_ENABLED"),
        DB_URL("VIETROUTE_DB_URL"),
        DB_USER("VIETROUTE_DB_USER"),
        DB_PASSWORD("VIETROUTE_DB_PASSWORD"),
        DB_SCHEMA("VIETROUTE_DB_SCHEMA"),
        DB_POOL_SIZE("VIETROUTE_DB_POOL_SIZE"),
        DB_ACQUIRE_TIMEOUT_MS("VIETROUTE_DB_ACQUIRE_TIMEOUT_MS"),
        DB_CONNECT_TIMEOUT_SECONDS("VIETROUTE_DB_CONNECT_TIMEOUT_SECONDS"),
        DB_SOCKET_TIMEOUT_SECONDS("VIETROUTE_DB_SOCKET_TIMEOUT_SECONDS"),

        // Quota, request và proxy đáng tin.
        RATE_REQUESTS("VIETROUTE_RATE_REQUESTS"),
        RATE_LOGINS("VIETROUTE_RATE_LOGINS"),
        RATE_MAX_KEYS("VIETROUTE_RATE_MAX_KEYS"),
        HTTP_IN_FLIGHT("VIETROUTE_HTTP_IN_FLIGHT"),
        LOGIN_IN_FLIGHT("VIETROUTE_LOGIN_IN_FLIGHT"),
        HTTP_MAX_BODY_BYTES("VIETROUTE_HTTP_MAX_BODY_BYTES"),
        REDIS_URL("VIETROUTE_REDIS_URL"),
        TRUSTED_PROXY_CIDRS("VIETROUTE_TRUSTED_PROXY_CIDRS"),

        // Worker và thời hạn lưu receipt.
        WORKER_ENABLED("VIETROUTE_WORKER_ENABLED"),
        WORKER_THREADS("VIETROUTE_WORKER_THREADS"),
        WORKER_POLL_SECONDS("VIETROUTE_WORKER_POLL_SECONDS"),
        WORKER_LEASE_SECONDS("VIETROUTE_WORKER_LEASE_SECONDS"),
        JOB_RETENTION_DAYS("VIETROUTE_JOB_RETENTION_DAYS"),

        // Gmail/Workspace hoặc SMTP.
        MAIL_ENABLED("VIETROUTE_MAIL_ENABLED"),
        MAIL_PROVIDER("VIETROUTE_MAIL_PROVIDER"),
        MAIL_FROM("VIETROUTE_MAIL_FROM"),
        GMAIL_CLIENT_ID("VIETROUTE_GMAIL_CLIENT_ID"),
        GMAIL_CLIENT_SECRET("VIETROUTE_GMAIL_CLIENT_SECRET"),
        GMAIL_REFRESH_TOKEN("VIETROUTE_GMAIL_REFRESH_TOKEN"),
        SMTP_HOST("VIETROUTE_SMTP_HOST"),
        SMTP_PORT("VIETROUTE_SMTP_PORT"),
        SMTP_USER("VIETROUTE_SMTP_USER"),
        SMTP_PASSWORD("VIETROUTE_SMTP_PASSWORD"),

        // Ảnh công khai Cloudinary và cổng chạy local.
        MEDIA_ENABLED("VIETROUTE_MEDIA_ENABLED"),
        CLOUDINARY_CLOUD_NAME("VIETROUTE_CLOUDINARY_CLOUD_NAME"),
        CLOUDINARY_API_KEY("VIETROUTE_CLOUDINARY_API_KEY"),
        CLOUDINARY_API_SECRET("VIETROUTE_CLOUDINARY_API_SECRET"),
        CLOUDINARY_FOLDER("VIETROUTE_CLOUDINARY_FOLDER"),
        HTTP_PORT("VIETROUTE_HTTP_PORT");

        private final String key;

        Env(String key) {
            this.key = key;
        }

        public String key() {
            return key;
        }
    }

    /** Route đầy đủ dùng chung cho mapping, security, filter và OpenAPI; phần con ghép từ cùng gốc. */
    public static final class Routes {

        public static final String HOME = "/";

        public static final String HEALTH = "/health";

        public static final String LOGIN = "/login";

        public static final String ACCOUNT = "/account";

        public static final String ADMIN = "/admin";

        public static final String DOCS = "/docs";

        public static final String MEDIA_PAGE = "/media";

        public static final String OPENAPI = "/v3/api-docs";

        public static final String API_ROOT = "/api/";

        public static final String API = API_ROOT + "v1";

        public static final String ADMIN_API = API + ADMIN;

        public static final String AUTH = API + "/auth";

        public static final String LOGIN_ACTION = "/login";

        public static final String CSRF_ACTION = "/csrf";

        public static final String ME_ACTION = "/me";

        public static final String PASSWORD_ACTION = "/change-password";

        public static final String LOGOUT_ACTION = "/logout";

        public static final String AUTH_LOGIN = AUTH + LOGIN_ACTION;

        public static final String AUTH_CSRF = AUTH + CSRF_ACTION;

        public static final String AUTH_LOGOUT = AUTH + LOGOUT_ACTION;

        public static final String CATALOGS = API + "/catalogs";

        public static final String CATALOG_PREFIX = CATALOGS + "/";

        public static final String CATALOG_BY_TYPE = CATALOGS + "/{type}";

        public static final String ADMIN_CATALOGS = ADMIN_API + "/catalogs";

        public static final String BY_ID = "/{id}";

        public static final String ADMIN_CATALOG_BY_ID = ADMIN_CATALOGS + BY_ID;

        public static final String ACCESS = ADMIN_API + "/access";

        public static final String PERMISSIONS_ACTION = "/permissions";

        public static final String ROLES_ACTION = "/roles";

        public static final String ROLE_PERMISSIONS_ACTION = ROLES_ACTION + "/{role}" + PERMISSIONS_ACTION;

        public static final String USER_ROLES_ACTION = "/users" + BY_ID + ROLES_ACTION;

        public static final String JOBS = ADMIN_API + "/jobs";

        public static final String JOB_RETRY_ACTION = BY_ID + "/retry";

        public static final String MAIL = ADMIN_API + "/mail";

        public static final String MEDIA = API + MEDIA_PAGE;

        public static final String SYSTEM_STATUS = ADMIN_API + "/system/status";

        public static final String ASSETS = "/assets/";

        public static final String WEBJARS = "/webjars/";

        public static final String ASSET_MATCHER = ASSETS + "**";

        public static final String WEBJAR_MATCHER = WEBJARS + "**";

        public static final String DOCS_MATCHER = DOCS + "/**";

        public static final String CATALOG_MATCHER = CATALOG_PREFIX + "**";

        public static final String ADMIN_API_MATCHER = ADMIN_API + "/**";

        public static final String ADMIN_MATCHER = ADMIN + "/**";

        private Routes() {}
    }

    /** Regex chỉ mô tả từng loại dữ liệu cụ thể; không áp quy tắc mã cho tên người/địa chỉ/mật khẩu. */
    public static final class Regex {

        public static final String BUSINESS_CODE = "^[A-Z][A-Z0-9]{2,39}$";

        public static final String USERNAME = "^[A-Za-z][A-Za-z0-9]{2,31}$";

        public static final String CODE_PREFIX = "[A-Z]{2,6}";

        public static final String SCHEMA = "[a-z][a-z0-9_]{0,62}";

        public static final String TRACE_ID = "[A-Za-z0-9-]{8,64}";

        public static final String MESSAGE_TEMPLATE = "\\{[A-Za-z0-9.]+}";

        public static final String IDEMPOTENCY_KEY = "[A-Za-z0-9_-]{8,64}";

        public static final String JOB_DEDUP_KEY = "[A-Za-z0-9:_-]{8,128}";

        public static final String JOB_ERROR_CODE = "[A-Z][A-Z0-9_]{2,39}";

        public static final String SINGLE_LINE = "[^\\r\\n]+";

        public static final String IP_LITERAL = "[0-9a-fA-F:.]+";

        public static final String CLOUDINARY_FOLDER = "[a-zA-Z][a-zA-Z0-9_-]{0,39}";

        public static final String CLOUDINARY_NAME = "[a-zA-Z0-9_-]{1,80}";

        public static final String CLOUDINARY_KEY = "[0-9]{1,40}";

        public static final String IMAGE_READER_FORMAT = "jpeg|png";

        public static final String IMAGE_STORAGE_FORMAT = "jpg|png";

        public static final String SAFE_HTTP_METHOD = "GET|HEAD|OPTIONS";

        private Regex() {}
    }

    /** Tên cookie/header/attribute là hợp đồng HTTP, không phải key Environment. */
    public static final class Http {

        public static final String AUTH_COOKIE = "VIETROUTE_ACCESS";

        public static final String REQUEST_ID = "X-Request-ID";

        public static final String TRACE_ATTRIBUTE = "vietroute.traceId";

        public static final String LOCALE_HEADER = "X-Locale";

        public static final String LANGUAGE_PARAMETER = "lang";

        public static final String FORWARDED_FOR = "X-Forwarded-For";

        public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

        public static final String BEARER_PREFIX = "Bearer ";

        public static final String SAME_SITE_LAX = "Lax";

        public static final String JSON_UTF8 = "application/json;charset=UTF-8";

        public static final String TEXT_UTF8 = "text/plain;charset=UTF-8";

        public static final String NO_STORE = "no-store";

        private Http() {}
    }

    /** Key của file classpath không chứa secrets; giữ tách biệt với tên biến môi trường. */
    public static final class DatabaseProperties {

        public static final String RESOURCE = "/database.properties";

        public static final String URL = "db.url";

        public static final String USERNAME = "db.username";

        public static final String SCHEMA = "db.schema";

        private DatabaseProperties() {}
    }

    public enum LocaleCode {
        vi,
        en,
    }

    public enum CatalogType {
        VEHICLE_TYPE,
        BOOKING_SOURCE,
    }
}
