# Quy ước phát triển VietRoute

## Stack và cấu trúc

Java 17+ (JDK 21 khi phát triển), Spring MVC 6/Security, Thymeleaf, PostgreSQL; WAR/Tomcat 10.1. CRUD đơn giản dùng Spring Data JPA/Hibernate, JDBC dành cho batch/query phức tạp/khóa nguyên tử. Không Spring Boot. PostgreSQL là DB chạy ứng dụng; [profile JDBC Oracle 26ai](../01-bat-dau/CHAY_LOCAL.md#chọn-postgresql-hoặc-chuẩn-bị-oracle-26ai) mới chuẩn bị cấu hình, runtime chặn trước SQL đến khi có migration/query và regression Oracle.

```text
vn.vietroute/
  auth/               Tài khoản và phân quyền
  business/
    catalog/          Danh mục, tỉnh, địa điểm
    carrier/          Nhà xe, phương tiện, tài xế
    trip/             Tuyến, chuyến, tìm chuyến, tồn chỗ, tiến độ
    booking/          Đặt chỗ, thu/hoàn, sự kiện đơn
    report/           Báo cáo và dashboard
    bootstrap/        Khởi tạo dữ liệu nền
  common/             Nền MVC/JPA/JDBC, lỗi, validation, nhật ký
```

## Package theo nhóm nghiệp vụ

| Nhóm | Bảng | File Java | Trách nhiệm chính |
|---|---:|---:|---|
| catalog | 3 | 25 | CatalogItem, Province, Location |
| carrier | 3 | 23 | Carrier, Vehicle, Driver |
| trip | 6 | 42 | Route/RouteStop, Trip/TripStop/TripSegment/TripEvent, Journey |
| booking | 2 | 15 | Booking/BookingEvent |
| report | 0 | 10 | Query báo cáo và DashboardPageController |
| bootstrap | 0 | 6 | BusinessDataRepo/Service/API/PageController/Response/Initializer |
| **Tổng business** | **14** | **121** | Người phụ trách tại [phân công](../05-ke-hoach/PHAN_CONG.md) |

Trong mỗi nhóm: controller/ cho PageController, controller/api/ cho REST; entity/, repo/, service/, dto/request/, dto/response/, dto/search/. constant/config/initializer chỉ có khi cần. Initializer ngang service. Không tạo thêm cấp package theo tài nguyên hoặc wrapper rỗng. Không sinh service/API chỉ để mỗi bảng nối có đủ bộ lớp.

CatalogItemRepo/Service/API/PageController/Request/Response/Search bám tài nguyên CatalogItem. LocationRepo/Service xử lý cả Province và Location. TripSegmentRepo cùng package trip với entity, BookingRepo gọi trên connection caller để giữ khóa trip → booking → segments. Journey/PublicCarrier giữ projection public riêng; Dashboard không tự cần REPORT grant chỉ vì ở package report. Bootstrap dùng opt-in/idempotency; giữ URL/template hiện hành trong [API](API_BASE.md).

## Auth gọn: 40 class, tám bảng

| Thư mục trong auth | Class hiện hành | Số file |
|---|---|---:|
| entity | User, Role, UserRole, Function, RoleFunction, FunctionResource, AccountChallenge, AccessChange | 8 |
| repo | UserRepo, RoleRepo, UserRoleRepo, FunctionRepo, RoleFunctionRepo, FunctionResourceRepo, AccountChallengeRepo, AccessChangeRepo | 8 |
| service | UserService, RoleService, FunctionService, JwtService | 4 |
| controller/api | UserAPI, RoleAPI, FunctionAPI | 3 |
| controller | UserPageController | 1 |
| dto/request | UserRequest, RoleRequest, FunctionRequest | 3 |
| dto | UserDTO, RoleDTO, FunctionDTO | 3 |
| dto/search | UserSearch, RoleSearch, FunctionSearch | 3 |
| config | SecurityConfig, SecurityInitializer | 2 |
| security | UserPrincipal, EndpointAccess, JwtAuthenticationFilter, AuthCookies | 4 |
| initializer | AuthInitializer | 1 |
| **Tổng** | Mỗi file một class top-level, không giấu class DTO/ID bên trong | **40** |


UserDTO/RoleDTO/FunctionDTO nằm trực tiếp auth.dto. Request trong dto/request, Search trong dto/search, tên *Search. Tám entity JPA đủ cột, identity/version/audit theo schema; không hậu tố Entity. Role là nhóm quyền duy nhất, code do DB quản lý. User → Role → Function → Menu/API kiểm bằng method + route template. Hợp đồng đầy đủ tại [schema/phân quyền](SCHEMA_VA_PHAN_QUYEN.md).

## Request dùng chung và constant

Mỗi tài nguyên dùng một *Request cho tạo/sửa và các thao tác liên quan, không Create/Update/Write/Delete DTO riêng. Controller chọn RequestGroups Create/Update/Delete/State/Payment/Notice/Progress; client không chọn group. Field bắt buộc và Swagger required theo operation; Default/cascade @Valid giữ ràng buộc chung. Version bắt buộc khi sửa theo contract; ID/code/quan hệ cha gửi lại phải khớp DB, không lợi dụng DTO chung để đổi identity/scope.

| Nhóm | Request |
|---|---|
| catalog | CatalogItemRequest, ProvinceRequest, LocationRequest |
| carrier | CarrierRequest, VehicleRequest, DriverRequest |
| trip | RouteRequest, RouteStopRequest (điểm con), TripRequest |
| booking | BookingRequest |

Response dùng class, không entity; DTO HTTP nằm riêng, không giấu nested class để giảm số file. Search/lookup/picker thuộc dto/search. Enum/hằng nghiệp vụ ở module/constant, không entity/DTO. Env/Routes/Regex/Http ở ApplicationConstants, đọc env qua ConfigValues, dùng HttpHeaders chuẩn. WebRoutePolicy giữ public/JWT/self-service; thêm route constant không cấp quyền.

## SQL trong repo

SQL dài/nhiều mệnh đề dùng text block, SELECT/FROM/JOIN/WHERE/CTE/CASE/khóa/phân trang xuống dòng rõ, hướng tới 120 ký tự. Query ngắn có thể dùng string thường. Fragment/định danh động chỉ từ whitelist server; dữ liệu tiếp tục bind PreparedStatement. Không nối input hoặc sửa literal để đạt giới hạn dòng.

```java
String sql = """
    SELECT segment_order, capacity, reserved_seats
    FROM trip_segments
    WHERE trip_id = ?
        AND segment_order >= ?
        AND segment_order < ?
    ORDER BY segment_order
    FOR UPDATE
    """;
```

Repo giữ query/count/page/scope/owner/lock/CAS; service giữ nghiệp vụ, principal, transaction và mapping. Không query/save từng phần tử; dùng mảng/CTE/batch. Vòng ResultSet/bind batch/xử lý dữ liệu đã tải không là N+1; đếm execute thực tế khi kiểm hiệu năng. List phân trang DB, sort từ whitelist. Lỗi history rollback mutation; hủy chuyến và tiến độ cập nhật tập booking/event nguyên tử, không tải toàn bộ PII để loop update.

## Entity, transaction và audit

Entity ánh xạ đủ @Column/@Id/@Version/@Index theo migration. Generated column chỉ đọc; projection JOIN là @Transient. Không schema cứng, không hậu tố Entity. Hibernate không tự DDL, không Open Session in View. Timestamp nghiệp vụ dùng Instant/TIMESTAMPTZ; JDBC Timestamp chỉ chuyển ở repo boundary, không Date hoặc Timestamp trong entity/DTO/service.

JpaTransactionManager + TransactionAwareDataSourceProxy giữ JPA/JDBC cùng connection/rollback. Caller bắt đầu transaction trước LOCAL search_path đã validate, pg_temp cuối, không public/$user fallback, không bật autocommit trên connection đã mượn. Không dựa session state qua pooler. JdbcTransactions chịu commit/rollback; repo mutation không commit riêng. Không gọi dịch vụ mạng trong transaction đang giữ khóa. Journal có pool/transaction ghi riêng theo chuyên đề.

Audit lấy từ principal; startup/bảo trì dùng system=0, không NULL. Request không nhận audit/actor/tenant đáng tin. Entity có version đúng model, stale trả 409. [Audit](AUDIT_VA_SYSTEM.md), [baseline](SCHEMA_VA_PHAN_QUYEN.md#baseline-theo-cụm-bảng) và @JdbcTable/@JdbcIndex giữ ràng buộc DB. Khi thay schema, thêm bước mới và snapshot/checksum phù hợp, không sửa file đã áp dụng.

## API, locale, bảo mật và UI

API trả ApiResult<T>, extends BaseController, dùng execute/try-catch; body là ApiResponse<T>. ApiExceptionMapper dùng chung với GlobalExceptionHandler cho lỗi trước controller; giữ HTTP status, locale, traceId và lỗi đã lọc. Mọi API có @Operation; request/response có @Schema, đủ path/query/header/body và pagination kế thừa. Thêm message key vào cả vi/en. Lỗi nghiệp vụ dùng BusinessException(ErrorCode,args).

WAR có root/servlet context riêng, @EnableMethodSecurity ở SecurityConfig và WebConfig. EndpointAccess kiểm trước handler; service kiểm principal/context/nghiệp vụ và transaction; SQL kiểm scope/owner/parent/driver. Menu ẩn không thay bảo vệ URL/API; regression quyền dựng hai context và nhập URL trực tiếp. Không cache authority hoặc bypass lúc DB lỗi; all_scopes phải thuộc chính role cấp endpoint.

Spring Security/Nimbus JWT/BCrypt, CSRF cho cookie mutation, không localStorage token. RequestGuardFilter giữ JSON 64 KiB và tối đa hai BCrypt đồng thời; khóa đăng nhập sai vẫn hoạt động. Không phục hồi GPS/upload/mail/job/metrics/online khi chưa có nghiệp vụ mới được yêu cầu. [UI](../03-giao-dien/QUY_UOC_UI.md) dùng tab/modal chung, server paging, ARIA/focus, giữ draft/lỗi trong modal và không tự gửi lại mutation.

Common có 61 file: jdbc chín file cho connection/transaction/schema/metadata/dialect, gồm cấu hình Oracle 26ai; BaseRepository trong common.base; DatabaseInitializer/MigrationCatalog trong common.initializer. Chuẩn bị schema/history là helper private của DatabaseInitializer, không tách lớp chỉ cho một caller. Chi tiết [cấu trúc common](CONSTANTS_VA_CAU_TRUC_WEB.md).

## Đặt tên và format source

Không var/record production, import tường minh, không wildcard hoặc fully qualified class name trong code. Tên DTO viết hoa DTO; package nhiều từ dùng dấu gạch dưới khi cần. Class dưới 1000 dòng; không chuyển chức năng sang common chỉ để giấu số file. Comment/Javadoc tiếng Việt giải thích luồng, method service và transaction. Method reference cho callback chỉ chuyển tiếp nguyên invocation.

Bốn spaces, mỗi statement/annotation riêng dòng, dòng trống giữa field/method/khối. npm ci → npm run format → format:check; chạy check:rules/check:docs và test phù hợp. Stage file mới trước checker đọc Git. Không sửa rule/formatter để xanh, không format SQL đã áp dụng. Node/formatter chỉ phát triển, không thêm vào production Docker.

## Git và bàn giao

Mỗi thành viên dùng <member>/<task> từ dev theo [phân công](../05-ke-hoach/PHAN_CONG.md), PR/check/review → dev, không commit trực tiếp dev/main. Chỉ phát hành dev → main/build production/deploy khi có lệnh mới. Commit/merge/PR tiếng Việt có dấu, nêu thay đổi/lý do/kết quả/giới hạn; không đổi lịch sử tác giả.

Test Java trong src/test/java, fixture src/test/resources; production -Pproduction chỉ đóng src/main, không compile/test/đóng gói fixture. .env gốc chỉ giữ local/ignore Git, .env.example không có credential thật; không đưa secret vào chat/docs/log/image/file khác. Local launcher chủ động đọc file, OS/IDE ưu tiên; WAR/Render chỉ nhận env, test/build không tự đọc .env hoặc ghi Neon. Đọc [AI và bàn giao](../06-ai-va-ban-giao/AI_VA_BAN_GIAO.md) trước mỗi phiên; trạng thái/bằng chứng chỉ ghi [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).
