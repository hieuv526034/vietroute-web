package vn.vietroute.common.constant;

/**
 * Danh mục hằng số ứng dụng: key cấu hình, route/matcher, regex và hợp đồng HTTP.
 * Chỉ lưu TÊN key ở đây; giá trị môi trường và secrets do Settings đọc lúc khởi động.
 * Routes/Regex dùng hằng compile-time để annotation Spring/Validation tham chiếu trực tiếp.
 */
public final class ApplicationConstants {

    public static final String NAME = "VietRoute";

    private ApplicationConstants() {}

    /** Actor kỹ thuật của baseline: chỉ dùng audit, không đăng nhập hoặc gán role. */
    public static final class SystemActor {

        public static final long ID = 0L;

        public static final String USERNAME = "system";

        private SystemActor() {}
    }

    /** Ngày nghiệp vụ hiển thị theo giờ Việt Nam; timestamp lưu UTC bằng Instant. */
    public static final class Business {

        public static final String TIME_ZONE = "Asia/Ho_Chi_Minh";

        private Business() {}
    }

    /** Mỗi biến môi trường khai báo đúng một lần; không tạo toString chứa giá trị cấu hình. */
    public enum Env {
        ENV("VIETROUTE_ENV"),
        LOCAL_ENV_FILE("VIETROUTE_LOCAL_ENV_FILE"),
        COOKIE_SECURE("VIETROUTE_COOKIE_SECURE"),
        JWT_SECRET("VIETROUTE_JWT_SECRET"),
        BOOTSTRAP_ADMIN_USERNAME("VIETROUTE_BOOTSTRAP_ADMIN_USERNAME"),
        BOOTSTRAP_ADMIN_EMAIL("VIETROUTE_BOOTSTRAP_ADMIN_EMAIL"),
        BOOTSTRAP_ADMIN_PASSWORD("VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD"),
        DEFAULT_ACCOUNTS_ENABLED("VIETROUTE_DEFAULT_ACCOUNTS_ENABLED"),
        BUSINESS_INIT_ENABLED("VIETROUTE_BUSINESS_INIT_ENABLED"),
        DB_INIT_ENABLED("VIETROUTE_DB_INIT_ENABLED"),
        ACCESS_SYNC_ENABLED("VIETROUTE_ACCESS_SYNC_ENABLED"),
        ACCESS_REGISTRY_CAPACITY("VIETROUTE_ACCESS_REGISTRY_CAPACITY"),
        ACCESS_SCOPE_CAPACITY("VIETROUTE_ACCESS_SCOPE_CAPACITY"),
        DB_VENDOR("VIETROUTE_DB_VENDOR"),
        DB_URL("VIETROUTE_DB_URL"),
        DB_USER("VIETROUTE_DB_USER"),
        DB_PASSWORD("VIETROUTE_DB_PASSWORD"),
        DB_SCHEMA("VIETROUTE_DB_SCHEMA"),
        DB_POOL_SIZE("VIETROUTE_DB_POOL_SIZE"),
        DB_ACQUIRE_TIMEOUT_MS("VIETROUTE_DB_ACQUIRE_TIMEOUT_MS"),
        DB_CONNECT_TIMEOUT_SECONDS("VIETROUTE_DB_CONNECT_TIMEOUT_SECONDS"),
        DB_SOCKET_TIMEOUT_SECONDS("VIETROUTE_DB_SOCKET_TIMEOUT_SECONDS"),
        HTTP_PORT("VIETROUTE_HTTP_PORT"),
        API_JOURNAL_ENABLED("VIETROUTE_API_JOURNAL_ENABLED"),
        API_JOURNAL_CAPACITY("VIETROUTE_API_JOURNAL_CAPACITY"),
        API_JOURNAL_MINUTES("VIETROUTE_API_JOURNAL_MINUTES"),

        DEFAULT_CUSTOMER_EMAIL("VIETROUTE_DEFAULT_CUSTOMER_EMAIL"),
        DEFAULT_CUSTOMER_PASSWORD("VIETROUTE_DEFAULT_CUSTOMER_PASSWORD"),
        DEFAULT_DRIVER_EMAIL("VIETROUTE_DEFAULT_DRIVER_EMAIL"),
        DEFAULT_DRIVER_PASSWORD("VIETROUTE_DEFAULT_DRIVER_PASSWORD"),
        DEFAULT_OPERATOR_EMAIL("VIETROUTE_DEFAULT_OPERATOR_EMAIL"),
        DEFAULT_OPERATOR_PASSWORD("VIETROUTE_DEFAULT_OPERATOR_PASSWORD");

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

        public static final String AUTH_PROFILE = "/profile";

        public static final String ACCESS_ROLE_ITEM = "/registry/roles/{code}";

        public static final String ACCESS_REGISTRY = "/registry/roles";

        public static final String ACCESS_REGISTRY_PAGE = "/registry/roles/page";

        public static final String ACCESS_SCOPES = "/scopes";

        public static final String ACCESS_SCOPE_ITEM = "/scopes/{id}";

        public static final String ACCESS_SCOPE_MEMBERS = "/scopes/{id}/members";

        public static final String ACCESS_SCOPE_MEMBER = "/scopes/{id}/members/{userId}";

        public static final String ACCESS_SCOPE_PERMISSIONS = "/scopes/{id}/permissions";
        public static final String ACCESS_REGISTRY_ITEM = "/registry/roles/{code}";
        public static final String ACCESS_TARGETS = "/targets";
        public static final String ACCESS_RESOURCES = "/resources";

        public static final String ACCESS_FUNCTIONS = "/functions";

        public static final String ACCESS_FUNCTION_ITEM = "/functions/{code}";

        public static final String ACCESS_ROLE_FUNCTIONS = "/roles/{role}/functions";

        public static final String AUTH_CAPABILITIES = "/capabilities";

        public static final String HOME = "/";

        public static final String HEALTH = "/health";

        public static final String LOGIN = "/login";

        public static final String ACCOUNT = "/account";

        public static final String DASHBOARD = "/dashboard";

        public static final String ADMIN = "/admin";

        public static final String DOCS = "/docs";

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

        public static final String CATEGORIES = API + "/categories";

        public static final String ADMIN_CATEGORIES = ADMIN_API + "/categories";

        public static final String ADMIN_CATEGORY_GROUPS = ADMIN_CATEGORIES + "/groups";

        public static final String CATEGORY_PAGE = "/operations/categories";

        public static final String BY_ID = "/{id}";

        public static final String CARRIERS_PAGE = "/operations/carriers";
        public static final String PUBLIC_CARRIERS_PAGE = "/carriers";
        public static final String PUBLIC_CARRIERS = API + PUBLIC_CARRIERS_PAGE;
        public static final String PUBLIC_CARRIERS_MATCHER = PUBLIC_CARRIERS + "/**";

        public static final String VEHICLES_PAGE = "/operations/vehicles";

        public static final String DRIVERS_PAGE = "/operations/drivers";

        public static final String ADMIN_CARRIERS = ADMIN_API + "/carriers";

        public static final String ADMIN_VEHICLES = ADMIN_API + "/vehicles";

        public static final String ADMIN_DRIVERS = ADMIN_API + "/drivers";

        public static final String ROUTES_PAGE = "/operations/routes";

        public static final String ADMIN_ROUTES = ADMIN_API + "/routes";

        public static final String TRIPS_PAGE = "/operations/trips";

        public static final String ADMIN_TRIPS = ADMIN_API + "/trips";

        public static final String DRIVER_TRIPS_PAGE = "/driver/trips";

        public static final String DRIVER_TRIPS = API + DRIVER_TRIPS_PAGE;

        public static final String TRIP_CHOICES = "/choices";

        public static final String TRIP_ASSIGNMENT = BY_ID + "/assignment";

        public static final String TRIP_STATE = BY_ID + "/state";

        public static final String TRIP_PROGRESS = BY_ID + "/progress";
        public static final String TRIP_NOTICE = BY_ID + "/notice";
        public static final String TRIP_PASSENGERS = BY_ID + "/passengers";
        public static final String BOOKINGS_PAGE = "/operations/bookings";
        public static final String REPORTS_PAGE = "/operations/reports";
        public static final String ADMIN_REPORTS = ADMIN_API + "/reports";
        public static final String ADMIN_REPORT_CARRIERS = ADMIN_REPORTS + "/carriers";

        public static final String DRIVER_ACCOUNT = BY_ID + "/account";
        public static final String ADMIN_BOOKINGS = ADMIN_API + "/bookings";
        public static final String MY_BOOKINGS_PAGE = "/my-bookings";
        public static final String MY_BOOKINGS = API + MY_BOOKINGS_PAGE;
        public static final String BOOKING_STATE = BY_ID + "/state";
        public static final String BOOKING_PAYMENT = BY_ID + "/payment";
        public static final String BOOKING_TRIPS = "/trips";
        public static final String PUBLIC_TRIPS = API + "/trips";
        public static final String PUBLIC_TRIPS_MATCHER = PUBLIC_TRIPS + "/**";
        public static final String JOURNEYS_PAGE = "/journeys";
        public static final String JOURNEY_QUOTE = BY_ID + "/quote";

        public static final String CARRIER_CHOICES = "/carriers";

        public static final String DEMO_PAGE = "/operations/demo";

        public static final String ADMIN_DEMO = ADMIN_API + "/demo";

        public static final String ACCOUNTS = ADMIN_API + "/accounts";

        public static final String ACCESS = ADMIN_API + "/access";

        public static final String USERS_ACTION = "/users";

        public static final String ROLES_ACTION = "/roles";

        public static final String USER_ROLES_ACTION = USERS_ACTION + BY_ID + ROLES_ACTION;

        public static final String OPERATIONS = "/operations";

        public static final String ACCOUNTS_PAGE = OPERATIONS + "/accounts";

        public static final String ACCESS_PAGE = OPERATIONS + "/access";

        public static final String ACCESS_RESOURCES_PAGE = ACCESS_PAGE + "/resources";

        public static final String API_JOURNAL_PAGE = OPERATIONS + "/api-journal";

        public static final String API_JOURNAL = ADMIN_API + "/system/api-journal";

        public static final String API_JOURNAL_DETAIL = API_JOURNAL + BY_ID;

        public static final String ASSETS = "/assets/";

        public static final String WEBJARS = "/webjars/";

        public static final String ASSET_MATCHER = ASSETS + "**";

        public static final String WEBJAR_MATCHER = WEBJARS + "**";

        public static final String ADMIN_API_MATCHER = ADMIN_API + "/**";

        public static final String ADMIN_MATCHER = ADMIN + "/**";

        private Routes() {}
    }

    /** Regex chỉ mô tả từng loại dữ liệu cụ thể; không áp quy tắc mã cho tên người/địa chỉ/mật khẩu. */
    public static final class Regex {

        public static final String EMAIL_ADDRESS =
            "[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?)+";

        public static final String MIGRATION_RESOURCE =
            "postgresql/(foundation|auth|catalog|carrier|trip|booking|common)/(([0-9]{3})_[a-z0-9_]+[.]sql)";

        public static final String FUNCTION_CODE = "^[A-Z][A-Za-z0-9_]{2,79}$";

        public static final String PHONE_NUMBER = "^[+0-9(). -]{6,30}$";

        public static final String VEHICLE_PLATE = "(?i)^(?=(?:[.-]*[A-Z0-9]){5})[A-Z0-9.-]{5,20}$";

        public static final String ACCESS_RESOURCE_SEARCH = "[^\\p{Cntrl}]{0,160}";

        public static final String ENV_KEY = "[A-Z][A-Z0-9_]*";

        public static final String ROLE_CODE = "[A-Z][A-Z0-9_]{1,23}";

        public static final String BUSINESS_CODE = "^[A-Z][A-Z0-9]{2,39}$";

        public static final String USERNAME = "^[A-Za-z][A-Za-z0-9]{2,31}$";

        public static final String CODE_PREFIX = "[A-Z]{2,6}";

        public static final String SCHEMA = "[a-z][a-z0-9_]{0,62}";

        public static final String ORACLE_SCHEMA = "[A-Z][A-Z0-9_]{0,127}";

        public static final String TRACE_ID = "[A-Za-z0-9-]{8,64}";

        public static final String MESSAGE_TEMPLATE = "\\{[A-Za-z0-9.]+}";

        public static final String API_METHOD = "GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS";

        public static final String JOURNAL_TRACE = "[A-Za-z0-9-]{0,64}";

        public static final String JOURNAL_ENDPOINT = "[A-Za-z0-9/{}/_.-]{0,180}";

        public static final String JOURNAL_CODE = "[A-Z0-9]{0,16}";

        public static final String SQL_STATE = "[A-Z0-9]{5}";

        public static final String JOURNAL_FIELD = "[A-Za-z][A-Za-z0-9.]{0,60}";

        public static final String JOURNAL_ENUM = "[A-Z][A-Z0-9_]{0,39}";

        public static final String JOURNAL_PATH_VALUE = "[0-9]{1,19}|[0-9a-fA-F-]{36}|[A-Z][A-Z0-9_]{0,39}";

        public static final String JOURNAL_QUERY_NUMBER = "[0-9]{1,5}";

        private Regex() {}
    }

    /** Tên cookie/header/attribute là hợp đồng HTTP, không phải key Environment. */
    public static final class Http {

        public static final int MAX_JSON_BYTES = 65536;

        public static final int CREDENTIAL_CONCURRENCY = 2;

        public static final String VISITOR_COOKIE = "VIETROUTE_VISITOR";
        public static final String JOURNAL_USER_ATTRIBUTE = "vietroute.requestActorId";

        public static final String VERIFIED_USER_ATTRIBUTE = "vietroute.verifiedUserId";
        public static final String ACCESS_POLICY_ATTRIBUTE = "vietroute.accessPolicies";

        public static final String JOURNAL_STATE = "vietroute.apiJournalState";

        public static final String AUTH_COOKIE = "VIETROUTE_ACCESS";

        public static final String REQUEST_ID = "X-Request-ID";

        public static final String SERVER_TIMING = "Server-Timing";

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

        public static final String PROMETHEUS = "text/plain;version=0.0.4;charset=utf-8";

        private Http() {}
    }

    /** Key của file classpath không chứa secrets; giữ tách biệt với tên biến môi trường. */
    public static final class BootstrapProperties {

        public static final String RESOURCE = "/bootstrap.properties";

        public static final String EMAIL = "bootstrap.admin.email";

        private BootstrapProperties() {}
    }

    /** Key JDBC trong classpath, không chứa mật khẩu kết nối. */
    public static final class DatabaseProperties {

        public static final String RESOURCE = "/database.properties";

        public static final String PROFILE_DIRECTORY = "/database/";

        public static final String VENDOR = "db.vendor";

        public static final String URL = "db.url";

        public static final String USERNAME = "db.username";

        public static final String PASSWORD = "db.password";

        public static final String SCHEMA = "db.schema";

        private DatabaseProperties() {}
    }

    public enum LocaleCode {
        vi,
        en,
    }
}
