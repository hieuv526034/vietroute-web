# Hợp đồng API nền tảng

Quản trị hệ thống bổ sung: `GET /api/v1/admin/system/configuration` dùng CONFIGURATION_READ và DTO whitelist chỉ đọc; `GET /api/v1/admin/access/users` dùng ACCESS_READ, prefix username/page/size; list jobs có query status tùy chọn. UI và hợp đồng chi tiết tại [QUAN_TRI_HE_THONG.md](../04-van-hanh/QUAN_TRI_HE_THONG.md). Không có API ghi env/credential.

Giám sát bổ sung: `GET /api/v1/admin/system/runtime` dùng SYSTEM_READ và envelope chung; `GET /monitoring` cần SYSTEM_VIEW, dữ liệu đọc bằng SYSTEM_READ. `GET /internal/metrics` là Prometheus text, token vận hành riêng, không bọc envelope/JWT và không truy vấn DB. Token rỗng đóng endpoint. Xem [giám sát và vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md) và [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md).

## Endpoint đang có

| Method | Đường dẫn | Quyền | Hành vi |
|---|---|---|---|
| GET | `/health` | Công khai | Web availability, trả text; không truy vấn DB |
| GET | `/api/v1/auth/csrf` | Công khai | Phát cookie XSRF-TOKEN và data.token/headerName, không cache |
| POST | `/api/v1/auth/login` | Công khai + CSRF khi không dùng Bearer | Nhận username/password; trả JWT và đặt cookie HttpOnly |
| GET | `/api/v1/auth/me` | Đăng nhập | Response tài khoản có audit, không có hash/tokenVersion |
| POST | `/api/v1/auth/change-password` | Đăng nhập | Nhận currentPassword/newPassword; thu hồi token cũ, phát token mới |
| POST | `/api/v1/auth/logout` | Cookie/Bearer; anonymous vẫn được xóa cookie | Thu hồi mọi token của tài khoản nếu còn xác thực hợp lệ |
| GET | `/api/v1/catalogs/{type}` | Công khai | Danh mục đang hoạt động theo locale; page/size |
| POST | `/api/v1/admin/catalogs` | CATALOG_CREATE hoặc CATALOG_WRITE cũ | Tạo mục danh mục, HTTP 201; code null tự sinh |
| GET | `/api/v1/admin/catalogs/{id}` | CATALOG_READ | Đọc cả bản đã xóa mềm |
| PUT | `/api/v1/admin/catalogs/{id}` | CATALOG_UPDATE hoặc CATALOG_WRITE cũ | Sửa tên/thứ tự, bắt buộc version |
| DELETE | `/api/v1/admin/catalogs/{id}` | CATALOG_DELETE | Xóa mềm, body chứa version; giữ audit |
| GET | `/v3/api-docs` | Công khai | OpenAPI JSON tạo từ Spring mapping và Swagger annotations |
| GET | `/docs` | Công khai | Swagger UI được đóng gói cùng WAR |

View Thymeleaf: `/`, `/login`, `/account` (đăng nhập), `/admin` (CATALOG_VIEW). Trên Tomcat deploy WAR theo tên, thêm context `/vietroute`; Docker deploy `ROOT.war` nên dùng đường dẫn ở bảng trực tiếp.

API mở rộng: quản lý `/api/v1/admin/access/**`, theo dõi/retry `/api/v1/admin/jobs/**`, readiness `/api/v1/admin/system/status`, mail queue `/api/v1/admin/mail` (202, cần Idempotency-Key). Mỗi API có authority tương ứng tại service; `/auth/me` trả permissions. Catalog CRUD dùng quyền hành động trong bảng trên. COM012/429 là quota (khác AUTH005 khóa tài khoản), COM013/503 là quá tải/Redis lỗi, COM014/413 là body lớn; MAIL001/503 là mail tắt. Đọc [hợp đồng và luồng đầy đủ](NEN_TANG_MO_RONG.md).

ACCESS08: `@AccessResource`/`@AccessOperation` thay các hằng AccessRules theo từng quyền; meta-PreAuthorize chung gọi `accessGuard.operation` đọc trạng thái/mapping quyền hiện hành từ DB. HTTP AccessFunction khai báo actions để tự sinh RESOURCE_ACTION. ANY/ALL và ADMIN kèm quyền được gộp trong một SELECT; role/permission tắt, tài khoản khóa/chờ kích hoạt, tokenVersion cũ hoặc mật khẩu tạm đều không có quyền hành động. DB lỗi trả COM007/503 và dừng method, thiếu quyền trả 403. Không đổi payload/đường dẫn API. Đọc [guard chung và đăng ký quyền tự động](SCHEMA_VA_PHAN_QUYEN.md#guard-chung-theo-metadata-hàm--access08).

## Danh mục địa lý — LOCATION01

Danh mục dùng chung được ADMIN quản trị, không thuộc membership của một nhà xe. Khai báo LOCATION_* theo annotation/cây chức năng; các mã mới không nằm trong Permission.values(). Quản trị yêu cầu đồng thời ADMIN và đúng quyền ở HTTP lẫn service. `/operations/locations` cần LOCATION_VIEW; VIEW độc lập READ/CREATE/UPDATE. Chính sách bổ sung không nới giới hạn này.

| Method/path | Quyền | Hành vi |
|---|---|---|
| GET `/api/v1/provinces` | Công khai | query/page/size; chỉ tỉnh/thành active; trả id/code/name theo locale |
| GET `/api/v1/locations` | Công khai | query/provinceId/page/size; cả địa điểm và tỉnh/thành phải active; trả id/code/name/provinceId/provinceName/address/latitude/longitude |
| GET `/api/v1/admin/provinces`, GET `/api/v1/admin/locations` | ADMIN + LOCATION_READ | Tìm/đếm/phân trang tại PostgreSQL; active bỏ trống lấy cả hai trạng thái, locations thêm provinceId. Xem cả địa điểm khi tỉnh tắt |
| GET `/api/v1/admin/provinces/{id}`, GET `/api/v1/admin/locations/{id}` | ADMIN + LOCATION_READ | Đọc bản mới nhất kèm tên vi/en, audit/version; địa điểm thêm provinceActive và tên tỉnh vi/en |
| POST `/api/v1/admin/provinces` | ADMIN + LOCATION_CREATE | code nội bộ bắt buộc/bất biến, nameVi/nameEn bắt buộc tối đa 120, sortOrder 0–10000 mặc định 0, active mặc định true; trả 201 |
| POST `/api/v1/admin/locations` | ADMIN + LOCATION_CREATE | Các trường tên/mã/thứ tự/trạng thái như tỉnh, thêm provinceId đang active, address tối đa 255, latitude -90…90, longitude -180…180; tọa độ bắt buộc, tối đa 6 chữ số thập phân; trả 201 |
| PUT `/api/v1/admin/provinces/{id}`, PUT `/api/v1/admin/locations/{id}` | ADMIN + LOCATION_UPDATE | Bắt buộc version >=0 và các trường biên tập; code giữ nguyên, location không nhận provinceId khi sửa. Có thể bật/ngừng sử dụng, không có DELETE hoặc xóa vật lý |

Mã nội bộ theo BusinessCode, không giả định là mã hành chính chính thức hoặc cố định số tỉnh/thành. Public lookup không nhận cờ nới điều kiện active và không trả audit/version/trạng thái nội bộ. Request JSON không nhận field lạ/audit. Từ khóa nguyên văn tối đa 160, wildcard được escape; trang 0-based/size tối đa 100. API không gọi Google Maps hoặc chứng thực tọa độ thực địa; không seed danh mục giả.

Tắt tỉnh ẩn con khỏi tra cứu, giữ nguyên active riêng của con; bật lại tỉnh không tự bật con đã ngừng sử dụng. Tạo/bật địa điểm kiểm tỉnh trong cùng transaction với khóa FOR SHARE, không nhận quan hệ tỉnh mới qua PUT. LOC001/409 là tỉnh đã tắt; COM004/404 không tồn tại; COM005/409 mã trùng; COM008/409 version cũ. VAL012/VAL013/VAL014 lần lượt là giới hạn vĩ độ/kinh độ/độ chính xác. Dữ liệu/tọa độ có thể sửa cho lần sử dụng sau; ROUTE01/TRIP01 phải snapshot thông tin cần giữ lịch sử, không dựa vào tên/tọa độ mutable cho chuyến đã chốt.

## Locale và envelope

Ưu tiên: `?lang=vi|en` → `X-Locale` → `Accept-Language` → vi. Accept-Language en-US/vi-VN được chọn về en/vi; locale tường minh khác trả HTTP 400. Header Content-Language và field locale thể hiện ngôn ngữ đã dùng.

```json
{
  "success": false,
  "code": "COM001",
  "messageKey": "error.validation",
  "message": "Vui lòng kiểm tra dữ liệu đã nhập.",
  "data": null,
  "errors": [{
    "field": "code",
    "code": "VAL003",
    "messageKey": "validation.code",
    "message": "Mã phải bắt đầu bằng chữ in hoa, chỉ chứa chữ in hoa và số, dài 3–40 ký tự."
  }],
  "timestamp": "2026-10-21T09:00:00Z",
  "path": "/api/v1/admin/catalogs",
  "traceId": "caeae4ef-f5a4-4b16-9a34-06e50d6be073",
  "locale": "vi"
}
```

Đây là ví dụ hợp đồng, không phải lỗi thực tế ở production. Khi thành công, `data` có nội dung, `errors` rỗng; messageKey/code giữ ổn định khi đổi locale. API client xử lý theo HTTP status và code, không so sánh câu dịch. X-Request-ID tương ứng traceId; không gửi hoặc lưu password/token vào log.

JSON input từ chối field không khai báo, key trùng, object/token thừa ở cuối và `null` cho kiểu primitive. Lỗi binding trả COM002/HTTP 400 trước khi gọi service. Lỗi HTTP 406 vẫn trả envelope JSON; HTTP 405 giữ header `Allow` của MVC. Swagger lấy lại CSRF hiện hành trước mỗi mutation để sử dụng được tiếp sau login/logout; không lưu token qua reload.

## Nhóm mã lỗi

| Mã | HTTP | Ý nghĩa |
|---|---|---|
| COM001 | 400 | Validation DTO/phân trang |
| COM002 | 400 | JSON, enum, kiểu dữ liệu hoặc trường lạ không hợp lệ |
| COM003 | 400 | Locale tường minh không hỗ trợ |
| COM004 | 404 | Không tìm thấy |
| COM005 | 409 | UNIQUE/trùng dữ liệu |
| COM006 | 500 | Lỗi nội bộ, liên hệ bằng traceId |
| COM007 | 503 | Dữ liệu không sẵn sàng hoặc thao tác DB không hoàn thành |
| COM008 | 409 | Phiên bản audit đã cũ |
| COM009 / COM010 / COM011 | 405 / 415 / 406 | Method/media type/Accept không hỗ trợ |
| VAL001–VAL010 | 400 | Mã field validation trong ValidationCode: bắt buộc, không hợp lệ, mã, username, password, độ dài, page, size, thứ tự |
| AUTH001 | 401 | Credentials sai |
| AUTH002 | 401 | Chưa đăng nhập |
| AUTH003 | 403 | Không có quyền |
| AUTH004 | 401 | Token sai chữ ký, hết hạn, user bị khóa/ngừng hoạt động hoặc tokenVersion đã bị thu hồi |
| AUTH005 | 429 | Tạm khóa do sai mật khẩu 5 lần, thời gian 15 phút |
| AUTH006 | 403 | CSRF không hợp lệ |
| AUTH007 / AUTH008 | 400 | Mật khẩu hiện tại sai / mật khẩu mới trùng mật khẩu cũ |

Enum `ErrorCode` và `ValidationCode` là nguồn chính thức. Khi thêm lỗi mới phải bổ sung cả hai bundle và Swagger nếu thêm status mới. Không viết message trực tiếp ở controller; không trả exception.getMessage() của lỗi SQL ra client.

## Dùng auth trên giao diện hoặc API client

Website gọi GET `/api/v1/auth/csrf`, giữ cookie và gửi token ở header do data.headerName chỉ ra. Sau đó POST login bằng JSON. Axios client chung đã thực hiện việc này và tự gửi cookie; không lưu accessToken trong localStorage/sessionStorage.

API client cũng lấy CSRF/cookie để đăng nhập, rồi có thể dùng JWT trả về với `Authorization: Bearer <token>` cho request tiếp theo. Các request có Bearer được miễn CSRF vì credential không tự được trình duyệt gửi. Trong Swagger UI, token CSRF được lấy tự động; có thể thử login rồi dùng cookie, hoặc nhập accessToken vào Authorize.

JWT hết hạn sau 30 phút, chưa có refresh token; hết hạn thì đăng nhập lại. Decoder chỉ nhận HS256 đúng issuer/audience, exp/iat/jti; filter đọc lại DB để lấy role/active/tokenVersion. Logout và đổi mật khẩu vô hiệu hóa tất cả token trước đó, kể cả token còn thời hạn. Dữ liệu tài khoản không nằm trong HTTP session. Tài khoản bị khóa tạm chỉ chặn đăng nhập mới, không thay cho thao tác vô hiệu hóa tài khoản `active=false`.

Quyền ADMIN/OPERATOR/DRIVER/CUSTOMER là nền. Các module đa nhà xe sau này phải thêm companyId/ownership checks tại service; role đơn thuần không đủ bảo vệ tenant. Đăng ký CUSTOMER, xác minh email và quên/đặt lại mật khẩu được mô tả ở phần FOUNDATION04 bên dưới; tạo tài khoản nhà xe và scope/tenant vẫn là task nghiệp vụ riêng.

## Audit, transaction và module mẫu

API nghiệp vụ/auth kế thừa `common/base/BaseController` và dùng `execute(...)` để bọc kết quả trong `ApiResult<T>` (kế thừa `ResponseEntity<ApiResponse<T>>`). Base có try/catch tại biên HTTP; `ApiExceptionMapper` phân loại lỗi và tạo phản hồi. `GlobalExceptionHandler` dùng cùng mapper cho lỗi JSON/validation/binding xảy ra trước khi vào controller. Controller không sao chép catch hoặc đổi lỗi thành HTTP 200. Xem [luồng và ví dụ học source](LUONG_XU_LY_BASE.md).

- BaseEntity: id, createdAt/updatedAt, createdBy/updatedBy, version. DTO response kế thừa BaseResponse; request kế thừa BaseRequest và không nhận audit từ client.
- Audit actor 0 tham chiếu định danh kỹ thuật system; không trả NULL cho seed/tác vụ nền. Actor người dùng giữ ID thật; xem [quy tắc audit](AUDIT_VA_SYSTEM.md). System không được đăng nhập/gán role/cấp JWT.
- `CatalogCreateRequest` có type VEHICLE_TYPE hoặc BOOKING_SOURCE, code tùy chọn, nameVi/nameEn bắt buộc, sortOrder 0–10000. Response trả `name` theo locale cùng hai bản dịch và audit.
- `PageRequest`: page 0–1000000, size 1–100; `PageResponse<T>` gồm content/page/size/totalElements/totalPages/hasNext/hasPrevious.
- Service dùng `JdbcTransactions.execute(connection -> ...)`; mọi DAO trong giao dịch nhận cùng connection, không tự commit/close. SQLException hoặc RuntimeException rollback, cuối cùng đóng connection.
- `BaseRepository.read` tự đóng connection cho truy vấn đơn. Không mở connection mới giữa một luồng thay tồn chỗ/đơn cần atomic.
- `@BusinessCode` không bắt buộc null; kết hợp @NotBlank khi trường phải có. Tên tiếng Việt không áp regex mã. Code tự sinh vẫn có UNIQUE của DB.
- Thêm API: đặt @Operation, request/response @Schema; dùng request/response typed, không trả entity hoặc map tùy tiện. OpenAPI generator hiện hỗ trợ JSON body, PathVariable, RequestParam, PageRequest, generic envelope/PageResponse, ApiResult và ResponseEntity. API mới có multipart hoặc binding phức tạp cần mở rộng generator và kiểm tra schema tương ứng.
- API tạo mới gắn `@ResponseStatus(HttpStatus.CREATED)` và gọi base với `HttpStatus.CREATED`; lỗi vẫn trả status thực tế của `ResponseEntity`, không bị ép thành 201. `OpenApiModels` tách model Swagger khỏi annotation trùng tên.

## Khởi động DB và kiểm thử

`VIETROUTE_DB_INIT_ENABLED=true` bật migration trong `src/main/resources/db/migration`. Startup lấy PostgreSQL advisory transaction lock, kiểm tra checksum/history, thực thi DDL và seed trong cùng transaction. Chạy lại hoặc có hai instance cùng khởi động không tạo trùng admin/danh mục. Mật khẩu admin cũ không bị reset sau khi đổi. Chưa có seeding nghiệp vụ chuyến/đơn giả.

Schema lấy từ VIETROUTE_DB_SCHEMA (mặc định vietroute), history là bảng vietroute_schema_history trong schema đó. Query/entity không cố định schema; raw checksum V001–V003 giữ nguyên. Registry quyền trả thêm resource/actions; ACCESS_ASSIGN là quyền gán mới, WRITE cũ chỉ tương thích trong phạm vi đã mô tả. Chi tiết [SCHEMA_VA_PHAN_QUYEN.md](SCHEMA_VA_PHAN_QUYEN.md).

Render sinh JWT secret cố định từ Blueprint, yêu cầu nhập bootstrap password qua Environment. Local `.env` của chủ dự án đã chứa admin/password được yêu cầu; nó không được commit. Việc bật initializer là thao tác runtime có thay đổi schema/dữ liệu seed, không nằm trong Maven build. Nếu schema đã có migration cùng version nhưng checksum khác, startup dừng; tạo migration mới thay vì sửa file cũ.

`mvnw verify` chạy test local không DB production. CI build image với `-Pproduction` (không compile/chạy test Java), rồi dùng PostgreSQL container tạm kiểm tra concurrent bootstrap, seed lặp, login/CSRF/quyền, audit, mã danh mục, đổi mật khẩu, revoke token, khóa tạm và OpenAPI. Production WAR không chứa test/framework test hoặc credentials.

## Tài liệu kỹ thuật

- [Spring Security JWT](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security CSRF](https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html)
- [Swagger core models/annotations](https://github.com/swagger-api/swagger-core)
- [PostgreSQL explicit locking](https://www.postgresql.org/docs/current/explicit-locking.html)
## Ảnh công khai và chạy local

API ảnh ở `/api/v1/media` có upload multipart, đọc/phân trang và xóa qua worker; quyền MEDIA_READ/CREATE/DELETE cùng kiểm owner. Cấu hình, giới hạn file, luồng phục hồi và hướng dẫn Run/Debug IntelliJ nằm trong [Cloudinary và IntelliJ](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md).

## Vòng đời tài khoản — FOUNDATION04

| Method/path dưới `/api/v1/auth` | Quyền/hợp đồng |
|---|---|
| GET `/options` | Public; công bố emailFlowsEnabled và chính sách khóa/history, không có secret |
| POST `/register` | Public + CSRF/quota, username/displayName/email/password; 202 chung, role CUSTOMER do server gán, active=false chờ xác minh |
| POST `/activation/request` | Public + CSRF/quota, email; 202 chung, không xác nhận email tồn tại |
| POST `/activate` | Public + CSRF/quota, email/token; token một lần 24h, không tự phát JWT |
| POST `/password/forgot` | Public + CSRF/quota, email đã xác minh; 202 chung kể cả không có email |
| POST `/password/reset` | Public + CSRF/quota, email/token/newPassword; token 15 phút, đổi hash/history/thu hồi JWT trong cùng transaction |
| POST `/email` | Đăng nhập + currentPassword/email; gắn email cho tài khoản cũ, email mới chưa dùng reset trước xác minh |

UI `/account-access` có tab đăng ký, xác minh, quên mật khẩu và đặt mật khẩu mới; link từ login. Trang tài khoản có modal gắn email; AUTH17 chỉ cấp sẵn xác minh cho bootstrap; account khác chưa xác minh phải gắn email, kể cả ADMIN cũ. Trang /account-access mở form xác minh cả khi đang đăng nhập, không chuyển vòng về account. Đăng ký mới không nhận role/audit/user ID. Không nhận token qua URL; mã trong email được nhập vào form/JSON, không storage. Kích hoạt không phục hồi tài khoản đã bị vô hiệu hóa, chỉ tài khoản mới có activation_pending hoặc user đang active được xác minh.

Theo lựa chọn chủ dự án ngày 24/10/2026, sai 5 lần liên tiếp **khóa 15 phút**, không ép reset để tránh người khác cố tình nhập sai khóa vĩnh viễn tài khoản. Chủ tài khoản có thể dùng email đã xác minh để đặt lại khi khóa tạm. Mật khẩu mới không trùng hiện tại và **hai mật khẩu trước đó**; sau ba lần đổi mới được dùng lại mật khẩu đã ra khỏi cửa sổ. V011 chỉ bắt đầu giữ lịch sử từ lúc áp dụng, không thể suy lại hash cũ. Reset xong phải login lại; không tự kích hoạt user đã bị khóa bởi admin.

Để mở luồng: `VIETROUTE_AUTH_SELF_SERVICE_ENABLED=true`, `VIETROUTE_MAIL_ENABLED=true`, `VIETROUTE_WORKER_ENABLED=true`, cấu hình Gmail/Workspace theo [nền mail](NEN_TANG_MO_RONG.md). `.run` chia sẻ vẫn tắt mail/worker để không tự gửi thật khi chạy thử. `/options` cho biết cấu hình khả dụng, không chứng minh provider/inbox đang hoạt động. Cần startup áp dụng V011 trước. Thiếu cấu hình trả AUTH009/503; mã sai/hết hạn/đã dùng trả AUTH010/400; mật khẩu lặp trả AUTH008/400.

Challenge DB chỉ giữ SHA-256 của token ngẫu nhiên 256 bit. Mỗi user/mục đích tối đa một dòng, cooldown 60 giây, tối đa 10 email auth/user/24h theo outbox; đăng ký mặc định tối đa 100 user có email/24h toàn hệ thống (key AUTH_REGISTRATIONS_PER_DAY, 1–1000). Khóa transaction/user và advisory lock chống đua giữa node. Đăng ký duplicate/cap trả cùng 202 nhưng không tạo user/job mới. Worker gửi sau commit, retry có thể khiến thư tới lặp; mã vẫn chỉ dùng một lần. Outbox auth mã hóa AES-GCM bằng khóa dẫn xuất tách miền từ JWT secret; mọi node cần cùng secret. Đổi secret làm JWT và outbox auth cũ không dùng được, cần cấp mã mới. Job UI không có recipient/payload/token.

SMS/phone OTP **chưa triển khai** vì chưa chọn provider/ngân sách. Đối chiếu 24/10/2026: [Twilio trial](https://www.twilio.com/docs/usage/trials) giới hạn 30 ngày/recipient đã xác minh và quota, không phải free production; [Firebase](https://firebase.google.com/docs/auth/limits) chỉ gửi SMS verification trên gói Blaze. Chưa bật dịch vụ trả phí hoặc hứa SMS miễn phí lâu dài; hiện dùng email làm kênh xác minh/khôi phục.


## Hồ sơ tự phục vụ — FOUNDATION05

PUT `/api/v1/auth/profile` yêu cầu đăng nhập, CSRF với cookie; body `{ "displayName": "Tên hiển thị", "avatarMediaId": null, "version": 0 }`. Version bắt buộc từ auth/me; null avatarMediaId bỏ ảnh, không nhận URL/user ID/role/email/audit. ProfileService khóa ảnh trước user, kiểm READY/owner (cả ADMIN cũng chỉ gắn ảnh của mình), active/tokenVersion/version và cập nhật audit principal trong một transaction. Mật khẩu/email vẫn đi luồng riêng. Xung đột 409 giữ bản nháp, không tự retry; đọc lại profile rồi chủ động mở lại form để lấy version mới.

POST `/api/v1/auth/avatar` nhận multipart field `file`, dùng cùng MediaService/ImageValidator/storage/quota/semaphore/cleanup. Mọi tài khoản đã xác thực được upload avatar của mình, không tự có MEDIA_READ/UPDATE/DELETE hoặc quyền thư viện người khác. Upload trả MediaResponse rồi UI gắn ID qua profile; lỗi gắn không xóa ảnh READY đã upload, UI giữ ID để không upload lặp. Đây là ảnh công khai, Cloudinary phải cấu hình/bật. Ảnh đang làm avatar bị chặn xóa COM015/409; gỡ/thay liên kết trước. Không thử provider thật trong CI, chỉ metadata ảnh giả ở DB tạm.

V012 thêm avatar FK/index, tombstone role và metadata annotation. Principal được dựng từ DB có tên hiển thị/thumbnail; header không giữ JWT trong storage hoặc phải gọi thêm DB riêng cho từng menu. Sau đổi hồ sơ, UI cập nhật header, request sau đọc lại DB. Shared Run vẫn tắt provider/mail theo mặc định; runtime phải chủ động áp dụng V012 trước khi chạy source mới.

Upload avatar thành công trả HTTP 201/OK004, OpenAPI có response 201; lỗi vẫn giữ status chung. Triển khai xong tất cả node trước khi dùng liên kết avatar vì phiên bản cũ chưa có guard xóa ảnh đang tham chiếu.

## Cây quyền UI/API — ACCESS03

Hợp đồng hiện hành và ví dụ nằm ở [cây chức năng](SCHEMA_VA_PHAN_QUYEN.md#cây-chức-năng-và-quyền-hành-động--access03). VIEW mở trang, API đòi hành động riêng, user nhiều role nhận hợp quyền DB. Annotation AccessFunction thực thi guard HTTP; service dùng AccessResource/AccessOperation để tự sinh cùng mã, không bỏ scope/version. Registry/user/role management có thể ủy quyền bằng ACCESS_*; ADMIN/system/last-admin và dữ liệu journal/online giữ giới hạn riêng.

GET `/api/v1/admin/access/tree` (ACCESS_READ) trả code/parent/name/path/viewPermission/actions + endpoints; GET `/api/v1/auth/capabilities` trả capability của chính principal; GET `/api/v1/admin/access/users/{id}/effective` (ACCESS_READ) trả nguồn role của từng permission. PUT role permissions/user roles bắt buộc version; giới hạn 512 permission và 32 role. Response role thêm active (gồm role tắt chưa xóa) và assignedUsers (snapshot gồm cả user tắt). POST/PUT registry/policy chỉ trả tài nguyên vừa ghi; DELETE trả danh sách rỗng, status giữ nguyên. Không nhận route/public/grant metadata từ client để thay annotation. Triển khai V013 phải dừng/drain code trước VIEW; xem chuyên đề trước khi nâng môi trường đang có dữ liệu.

## Resource và Swagger — ACCESS04

GET `/api/v1/admin/access/resources` cần ACCESS_READ; query gồm `kind` (rỗng/MENU/ENDPOINT), `query` (rỗng hoặc tối đa 160 ký tự không chứa control), `page`, `size`. Count và danh sách cùng lọc ở PostgreSQL trước phân trang. Tìm không phân biệt hoa/thường theo route/controller/mô tả/group/function/mã quyền; `%` và `_` là ký tự thường, không phải wildcard. Loại sai/từ khóa quá dài trả 400/COM001. UI giữ bộ lọc khi đổi trang/size, tìm mới về trang 0.

OpenAPI xuất từ mapping thật + Operation/Parameter/Schema. Path/query/header có mô tả/ví dụ; ModelAttribute và mọi lớp con PageRequest trải phẳng field kế thừa thành query parameters, giữ default/min/max/maxLength/enum và phân biệt required với giá trị mặc định của DTO. Body JSON và multipart có schema riêng, response vẫn là ApiResponse với đúng status. Tên alias Spring `value`/`name` được resolve, giữ đúng Idempotency-Key; principal/servlet request do server tiêm không thành input của client. Regression đối chiếu toàn bộ route API với JSON spec, payload/response fields và query để tránh thêm controller nhưng quên Swagger.

Permission là value object JSON string từ registry; constants/values() chỉ là từ vựng bootstrap, không khóa client vào enum role/quyền cố định. Các mã như BOOKING_LIST/BOOKING_DETAIL hợp lệ, chưa có nghĩa booking đã triển khai hoặc ai đã được cấp. Guard của từng endpoint phải khai báo đúng mã hành động; có mã trong registry chưa tự mở API. Bộ quyền hệ thống là mẫu chọn/gán các mapping cụ thể, xem hợp đồng ở [schema và phân quyền](SCHEMA_VA_PHAN_QUYEN.md#bộ-quyền-hệ-thống-và-quyền-db--access04).

## API quản trị danh tính — ACCESS05

| API | Quyền bắt buộc ngoài JWT/CSRF | Hợp đồng |
|---|---|---|
| GET `/api/v1/admin/accounts` | ADMIN + ACCOUNT_READ | username prefix, role, active, page/size; loại system, không credential |
| POST `/api/v1/admin/accounts` | ADMIN + ACCOUNT_CREATE + ACCESS_ASSIGN (ALL) | username/displayName/password write-only/roles active; 201, trùng 409; tạo cùng transaction |
| PUT `/api/v1/admin/accounts/{id}/state` | ADMIN + ACCOUNT_UPDATE | active + version; thu hồi JWT/challenge, stale 409, bảo vệ ADMIN/system/self/pending activation |
| GET `/api/v1/admin/accounts/{id}/access` | ADMIN + ACCOUNT_READ | metadata và capabilities đã xét policy user đích; không token/credential |
| PUT `/api/v1/admin/access/users/{id}/roles` | ACCESS_ASSIGN hoặc ACCESS_WRITE | Cơ chế gán hiện có: scope ủy quyền, version, chống tự sửa/nâng quyền; JWT cũ bị thu hồi |

Mọi payload/tham số/projection được mô tả OpenAPI. ACCOUNT_* khai báo tập trung tại AccessConstants và Permission; sync enabled bổ sung registry/ADMIN khi startup. Không cần thay migration hiện hữu. POST cấp tài khoản và đổi mật khẩu đi qua quota/semaphore credential để giới hạn BCrypt đồng thời; GET tìm tài khoản chỉ dùng quota request thông thường, không làm cạn quota đăng nhập.

AUTH011 (403) yêu cầu đổi mật khẩu tạm. Filter xác minh token/DB rồi bỏ toàn bộ authority trong phiên hạn chế; chỉ mở GET hồ sơ/me, capabilities rỗng, đổi mật khẩu/logout, trang account và dữ liệu công khai. AUTH17 cấp sẵn email đã xác minh và bỏ cờ mật khẩu tạm riêng cho danh tính bootstrap khi init/sync bật; tài khoản ADMIN khác vẫn phải hoàn tất cả đổi mật khẩu và xác minh email. Đừng hiểu accessToken nhận từ login mật khẩu tạm là đã có quyền quản trị. Nhật ký mặc định bỏ payload/response các DTO quản trị mới, không thêm password/PII vào projection.

## API lịch sử phân quyền — ACCESS09

GET `/api/v1/admin/access/history` chỉ ADMIN + ACCESS_READ; query `event` theo enum sự kiện, `target` tiền tố nguyên văn tối đa 120 ký tự, `actorId` không âm, `page/size` theo PageRequest (size tối đa 100). Count và page lọc trong PostgreSQL, `%`, `_`, `!` được escape thành ký tự thường. Sai enum/giới hạn trả 400. Không có endpoint sửa/xóa lịch sử.

Response PageResponse chứa ID, event, targetKind/targetKey, actorId/actorUsername, traceId, createdAt và before/after có schema tường minh. Với `delta=true`, các set trước/sau lần lượt là mã bị bỏ/thêm, mappingCount/version thể hiện tổng thực trước/sau; không hiểu danh sách delta là toàn bộ quyền tại thời điểm đó. Không password/hash/token/email/body/header. Đọc lịch sử không tải vào cache/storage; các giới hạn retention/rollback tại [chuyên đề](SCHEMA_VA_PHAN_QUYEN.md#lịch-sử-quản-trị-quyền--access09).

## Registry phân trang — ACCESS10

GET `/api/v1/admin/access/registry/{kind}/page` cần ACCESS_READ, kind là roles/permissions, query nguyên văn tối đa 160 ký tự trong code/nameVi/nameEn không phân biệt hoa thường, active tùy chọn, page/size kế thừa (size tối đa 100). Response có page, capacity và allocated chưa xóa của toàn loại. `%`, `_`, `!` là ký tự thường; sai loại/query/size trả 400. Hai query count/page dùng projection registry rõ ràng, không credential. API danh sách cũ giữ shape tương thích; lưu registry/audit đọc trực tiếp theo code, không tải toàn danh sách để tìm một mã.

COM016/409 báo đạt quota cấu hình, khác COM005 trùng mã và COM008 stale version. Quota/tombstone/giới hạn mở rộng tại [chuyên đề](SCHEMA_VA_PHAN_QUYEN.md#tìm-role-và-quota-registry--access10). Tăng quota không tự tăng permission trên role đã lưu hoặc nới guard.

## API phạm vi bảo mật — SCOPE01

| API | Guard | Input/kết quả |
|---|---|---|
| GET /api/v1/admin/access/scopes | ADMIN + ACCESS_READ | query/active/page/size theo registry search; PageResponse metadata |
| POST /api/v1/admin/access/scopes | ADMIN + ACCESS_CREATE | code/nameVi/nameEn/active/version=0, 201; code bất biến/duy nhất, quota COM016 |
| PUT /api/v1/admin/access/scopes/{id} | ADMIN + ACCESS_UPDATE | tên/trạng thái, giữ code/version; stale 409 |
| GET /api/v1/admin/access/scopes/{id}/members | ADMIN + ACCESS_READ | userId tùy chọn/page/size; projection userId/username/role/trạng thái/version, không credential |
| PUT /api/v1/admin/access/scopes/{id}/members/{userId} | ADMIN + ACCESS_ASSIGN | roles là tập con role toàn cục của user, active, version=0 tạo/phiên bản hiện hành để sửa; không ADMIN/system/self; audit nguyên tử |
| GET /api/v1/auth/scopes | JWT hợp lệ, không mật khẩu tạm | danh sách scope của chính user, không có userId đầu vào; không thay tìm nhà xe công khai |
| GET /api/v1/auth/scopes/{id}/permissions | JWT + membership/quyền ADMIN DB hiện hành | scopeId và tập permission riêng trong scope, 403 nếu scope/user/membership không hợp lệ; UI còn kết hợp policy/capabilities chung |

Không endpoint xóa hoặc nhận audit/tenant claim tin cậy từ client. Membership có thể tắt bằng roles rỗng/active=false; bật cần ít nhất một role active thuộc user. Tắt role/quyền/account/scope hoặc thu hồi global role có hiệu lực lần DB check tiếp theo; không hủy transaction đang chạy. Nền guard/DAO integration, giới hạn và bắt buộc drain V015 tại [thiết kế](SCHEMA_VA_PHAN_QUYEN.md#nền-phạm-vi-dữ-liệu--scope01). Swagger bao phủ query/path/body/response; không tạo API nghiệp vụ giả để minh chứng đã có nhà xe/trip.

### Khôi phục quyền admin gốc — ACCESS15

POST /api/v1/auth/admin-recovery, JSON currentPassword (write-only, tối đa 72 byte UTF-8); không có userId/username/role/permission phía client. Chỉ principal của username bootstrap cấu hình server; ADMIN khác trả AUTH003/403, mật khẩu tạm AUTH011/403, sai password AUTH007/400, 5 lần sai hoặc đang khóa AUTH005/429. Cookie cần CSRF; đi cùng quota/semaphore credential. Kết quả data gồm userId, permissionCount (số mapping ADMIN), loginRequired=true. Thành công thu hồi phiên; đăng nhập lại bằng mật khẩu hiện tại. Role/mapping/registry/history cùng transaction; lỗi DB/history trả 503 và rollback, không tự gửi lại mutation. Không reset mật khẩu hoặc bỏ guard DB. Luồng và giới hạn xem [cứu hộ bootstrap](SCHEMA_VA_PHAN_QUYEN.md#cứu-hộ-tài-khoản-bootstrap--access15).

### AUTH17 — bootstrap và trạng thái email

Login/me trả thêm data.user.emailVerificationRequired (login) hoặc data.emailVerificationRequired (me). AUTH012/403 nghĩa là email chưa xác minh: private API bị chặn, trang chức năng chuyển /account và capabilities không có quyền. Phiên này vẫn gọi me, capabilities, change-password, POST auth/email và logout; kích hoạt email vẫn qua mã một lần. ADMIN được gán cho user thường không được miễn. Service AccessOperation và DataScopeGuard kiểm cùng điều kiện DB, không tin cờ từ client.

Bootstrap admin lấy email từ VIETROUTE_BOOTSTRAP_ADMIN_EMAIL hoặc bootstrap.properties đã cấu hình mặc định theo chủ hệ thống. Init/sync cấp sẵn trạng thái xác minh, active và mustChangePassword=false, giữ hash/khóa chống dò; không gọi provider. Email gốc không sửa bằng POST auth/email (AUTH003 khi luồng mail bật), dùng cấu hình vận hành. Trạng thái thay đổi thu hồi phiên cũ; đăng nhập lại bằng mật khẩu hiện có. Các node cũ phải được dừng khi triển khai; account khác chưa xác minh cần mail/worker thật đã cấu hình. Chi tiết ở [hợp đồng bootstrap và email](SCHEMA_VA_PHAN_QUYEN.md#tài-khoản-gốc-sẵn-sàng-và-xác-minh-email--auth17).

### AUTH18 — trạng thái quyền và actor tự phục vụ

ManagedAccountResponse thêm boolean emailVerificationRequired ở danh sách/chi tiết/tạo account; chỉ cho biết cần xác minh, không trả địa chỉ email. GET access/users/{id}/effective giữ active là cờ DB, sources rỗng nếu account chưa sẵn sàng (tắt/pending/mật khẩu tạm/email chưa xác minh), không xóa mapping đã gán. Đây là nguồn quyền chung, không thay capabilities/policy/scope. Me/đổi mật khẩu/profile/gắn email và logout có principal chỉ chấp nhận actor cùng phiên server; lời gọi bean nội bộ truyền user khác trả AUTH003 trước đọc/ghi. Logout anonymous vẫn xóa cookie.
