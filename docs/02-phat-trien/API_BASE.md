# Hợp đồng API nền tảng

## ACCESS24 — Tài nguyên cụ thể và quản trị User

- GET `/api/v1/admin/access/resource-catalog`: inventory source hiện tại, ACCESS_READ; public/self-service chỉ xem, không cấp quyền từ cờ DB.
- GET/PUT `/api/v1/admin/access/roles/{role}/resources`: GET ACCESS_READ, PUT ACCESS_ASSIGN. PUT `{targets:["GET /operations/vehicles","GET /api/v1/admin/vehicles"],version:0}` thay toàn bộ, tập rỗng thu hồi. Target phải nằm trong nguồn hiện tại và private có guard; không sửa ADMIN. Version cũ 409; public/self-service/target lạ 400; gán vượt tập target/projection actor 403. History failure rollback toàn bộ.
- PUT `/api/v1/admin/accounts/{id}`: ADMIN + ACCOUNT_UPDATE, `{displayName,version}`; username/email/password không thay trong CRUD này.
- DELETE `/api/v1/admin/accounts/{id}?version=...`: ADMIN + ACCOUNT_DELETE; xóa mềm, thu hồi phiên/challenge/role/membership, giữ ID/FK/lịch sử; bảo vệ system/bootstrap/self/ADMIN. Không tự xử lý chuyến/booking đang mở.
- GET/menu/capabilities/preview kiểm cùng target DB; cùng action không đồng nghĩa cùng API. HEAD theo GET. Source service semantic/ANY/ALL/adminOnly và scope/owner luôn bắt buộc. API tương thích permission còn giữ nhưng không là màn cấu hình chính. Xem [schema/quyền](SCHEMA_VA_PHAN_QUYEN.md#access24--cấu-hình-user--role--menuapi) về migration V022 và yêu cầu drain node V021 trước gán native.


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

MAIL02: thư kích hoạt/xác minh và reset có template HTML Việt/Anh theo locale yêu cầu, kèm bản chữ thuần. Template là source server, mọi biến được escape; mã 256 bit/thời hạn không đổi, HTML cùng mã được mã hóa trong outbox auth. API không nhận field html/link/sender. Xem [hợp đồng template](NEN_TANG_MO_RONG.md#template-email-tài-khoản--mail02); nghiệm thu provider/inbox thực là bước riêng.

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


## Danh mục riêng và nhà xe/phương tiện/tài xế — BUSINESS02

Mỗi danh mục có trang riêng dưới menu Danh mục: `/catalogs/vehicle-types`, `/catalogs/booking-sources`, `/catalogs/provinces`, `/catalogs/locations`. `/admin` là tổng quan. URL cũ `/operations/locations` và API `/api/v1/admin/catalogs` giữ hợp đồng tương thích; không còn là nơi chọn mọi loại mới. VIEW độc lập READ và CREATE/UPDATE/DELETE. Tập function hiện có 20 định nghĩa, bao gồm nhóm cha.

| API dưới `/api/v1/admin` | Hành động/quyền | Hợp đồng |
|---|---|---|
| `/vehicle-types`, `/booking-sources` | GET list/id READ, POST CREATE, PUT id UPDATE, DELETE id DELETE của `VEHICLE_TYPE`/`BOOKING_SOURCE` | Lọc query/active, count/page PostgreSQL; cập nhật active + version; DELETE nhận JSON version và ngừng sử dụng. ID/type khác endpoint trả 404/400. Giữ alias CATALOG tương ứng trong phạm vi hai loại cũ. |
| `/provinces`, `/locations` | PROVINCE hoặc LOCATION VIEW/READ/CREATE/UPDATE, ADMIN-only như nền địa điểm | Giữ alias LOCATION cho tỉnh cũ; public lookup vẫn chỉ active. |
| `/carriers` | CARRIER_VIEW/READ/CREATE/UPDATE | ADMIN tạo scope+nhà xe trong một giao dịch qua auth, cùng quota/khóa gán role và lịch sử SCOPE_CREATED; chỉ ADMIN đổi active. Thành viên có scope sửa thông tin; scope security không bị tắt theo business active. |
| `/vehicles` | VEHICLE_VIEW/READ/CREATE/UPDATE | Tạo cần carrierId được kiểm scope; code/carrier/scope bất biến khi sửa. Biển số chuẩn hóa chữ hoa, DB unique theo ký tự chữ/số, capacity 1–200, loại VEHICLE_TYPE active. |
| `/drivers` | DRIVER_VIEW/READ/CREATE/UPDATE | Hồ sơ có thể chưa có account. Khi gắn account: account ready, có global DRIVER và scoped DRIVER đúng nhà xe. Không suy membership từ tên role. |
| `/vehicles/carriers`, `/drivers/carriers` | CREATE tương ứng | Picker tìm/phân trang tại DB bằng đúng quyền scoped của tài nguyên con, chỉ trả id/code/name; không đòi CARRIER_READ hoặc lộ hồ sơ đầy đủ. |
| `/demo` POST | ADMIN + DEMO_CREATE + mọi CREATE của bộ dữ liệu | Thao tác chủ động từ `/operations/demo`; không tự seed lúc startup. Một transaction/advisory lock, mã DEMO cố định, chỉ INSERT phần thiếu, giữ nguyên bản đã có, collision nhãn ngoài DEMO rollback toàn bộ. |

Danh sách carrier/vehicle/driver nhận query/active/carrierId/page/size; cả count lẫn trang dữ liệu kiểm account/tokenVersion/scope/membership/role/permission hiện hành. Truy cập ID ngoài scope trả 404; filter carrierId không cấp quyền. CREATE/UPDATE suy scope từ cha trong DB, khóa cha trước con, audit lấy principal; stale version 409 và không tự gửi lại. Mỗi dòng trả canUpdate từ guard SQL cùng scope; UI giao cờ này với capabilities/endpoint policy chung. Ngừng sử dụng thay xóa vật lý. Ảnh logo/photo chỉ nhận ID ảnh chung READY thuộc actor; xóa media bị chặn khi carrier/vehicle còn tham chiếu, kể cả inactive. Không gọi provider trong transaction này.

Bộ dữ liệu `src/main/resources/demo/business.json`: 5 catalog, 3 tỉnh, 3 địa điểm, 2 nhà xe, 4 xe, 4 hồ sơ tài xế (21 bản ghi nghiệp vụ + 2 scope). Không tạo account, membership, grant hoặc email; mã/biển số/điện thoại/tọa độ minh họa. Chạy lại trả created=0/reused=21 nếu bộ đã đủ và còn nhãn DEMO; không đặt lại trạng thái/nội dung người dùng đã sửa. Không xóa hoặc ghi đè để ép seed thành công. DEMO chưa có tuyến/chuyến/booking; không bán vé thật. Mặc định role thường không được tự cấp quyền source mới; quản trị gán global role/quyền rồi membership đúng carrier scope qua màn Phạm vi dữ liệu.

V017 thêm carriers/carrier_vehicles/carrier_drivers và FK composite vào scope/parent/catalog; giữ V001–V016. Triển khai source cùng migration qua startup init đã bật, sync registry/ADMIN khi enabled; không chạy migration/seed trong Maven. Email đang tạm hoãn không miễn xác minh cho người dùng thường hoặc tài xế. AUTH03 cấp account nhân sự, ROUTE01/TRIP01/BOOKING01/TRACK01 vẫn là các phần riêng theo đặc tả, chưa được coi hoàn thành bởi CRUD này.

Regression `BusinessIntegrationTest` chỉ chạy với `-Dbusiness.test.database=jdbc:postgresql://127.0.0.1:15439/vietroute_business_test`, PostgreSQL test user ciadmin, database dành riêng không có app schema khác; tạo/drop schema random `business02_it_*` sau mỗi ca. Không đọc .env; CI dựng PostgreSQL 17 cô lập và chạy Maven verify trước image build. Kiểm hai context WAR, VIEW/READ/type độc lập, scope count/ID/picker/revoke, stale/concurrent version, unique plate, parent inactive, account DRIVER, rollback/idempotence seed và media reference. Preview phải dùng DB khác (ví dụ vietroute_business_preview) vì startup chủ động chặn nhiều app history trong cùng DB.

## Tuyến và điểm dừng — ROUTE01 trong BUSINESS04

V018 bổ sung carrier_routes/carrier_route_stops, FK composite route/carrier/scope và địa điểm danh mục. Giữ V001–V017. GET `/api/v1/admin/routes` nhận query/active/carrierId/page/size, GET `/{id}` trả toàn bộ điểm; ROUTE_READ và predicate scope trong SQL count/trang/ID. GET `/carriers` chỉ trả picker nhà xe active có ROUTE_CREATE, không đòi quyền CARRIER_READ.

POST tuyến cần ROUTE_CREATE, PUT `/{id}` cần ROUTE_UPDATE. JSON gồm carrierId/code/nameVi/nameEn/description/active/stops; PUT thêm version và giữ carrierId/code cũ. Mỗi điểm gồm locationId, arrivalOffsetMinutes, departureOffsetMinutes, cumulativeFare, pickupAllowed, dropoffAllowed. 2–32 địa điểm khác nhau, thứ tự theo mảng; phút đến tăng qua mốc rời trước, 0–43200 phút, giá cộng dồn không giảm và tối đa 2 chữ số thập phân. Điểm đầu arrival=0/fare=0/pickup=true; điểm cuối dropoff=true. Hai chiều là hai tuyến riêng; không tự đảo điểm/giá.

Transaction khóa nhà xe → tuyến → danh mục, thay điểm nguyên tử và CAS version. Lỗi ROUTE001/400 giữ draft, version cũ 409, parent ngừng dùng 409; ngoài scope 404. Chuyến sẽ dùng snapshot độc lập khi module TRIP01 được triển khai, không tham chiếu ID mẫu điểm để sửa lịch sử. `/operations/routes` đòi ROUTE_VIEW riêng với READ. Không auto cấp các quyền này cho role thường; mapping vận hành được gán qua quản trị theo yêu cầu chủ dự án.

## Điều hành chuyến — TRIP01 trong BUSINESS04

V019 thêm `carrier_trips`, `trip_stops`, `trip_segments`, `trip_events`; giữ V001–V018. Chuyến lưu snapshot tên tuyến, loại xe/biển số, tài xế và điểm/địa chỉ/tọa độ/giờ/giá. Sửa mẫu/danh mục không sửa lịch sử đã chụp. Tồn chỗ có một dòng cho mỗi đoạn liên tiếp, capacity 1–200 và reserved_seats 0..capacity; module booking dùng các dòng này trong transaction riêng theo thứ tự khóa chung.

| API | Quyền và hợp đồng |
|---|---|
| GET `/api/v1/admin/trips` và `/{id}` | TRIP_READ + scope; query/carrierId/status/date/page/size lọc/count tại DB. date là ngày xuất phát ở Asia/Ho_Chi_Minh. Chi tiết có stops và tối đa 100 sự kiện mới nhất. ID ngoài quyền 404. |
| GET `/api/v1/admin/trips/choices` | kind CARRIER/ROUTE/VEHICLE/DRIVER, carrierId (cần cho con), query/page/size; tripId=0 dùng TRIP_CREATE, tripId dương dùng TRIP_UPDATE và buộc nhà xe của trip. Projection ID/mã/tên/capacity; không cần quyền CRUD danh mục phụ thuộc. |
| POST `/api/v1/admin/trips` | TRIP_CREATE; code/routeId/vehicleId/driverId/departureAt/capacity. Tạo DRAFT, giờ UTC ISO-8601 trong tương lai tối đa 730 ngày, chuẩn hóa giây. Source active, cùng carrier/scope, DRIVER ready có global/scoped DRIVER. |
| PUT `/{id}/assignment` | TRIP_UPDATE; vehicleId/driverId/capacity/departureAt nullable/version/note. DRAFT/OPEN; chỉ DRAFT dịch lịch. Không giảm dưới mức reserved cao nhất hoặc vượt xe. Không sửa snapshot điểm/giá. |
| PUT `/{id}/state` | TRIP_UPDATE; status OPEN/CANCELLED, version và note. DRAFT→OPEN; DRAFT/OPEN→CANCELLED, hủy cần lý do. Không cho staff tự gửi IN_PROGRESS/COMPLETED hoặc đi ngược trạng thái. |

Mở bán khóa trip → nhà xe → source → xe → tài xế/account; khóa xe/tài xế tuần tự hóa cạnh tranh lịch. Đổi phân công dùng trip → nhà xe → xe → tài xế/account → segments tăng dần. Lịch chồng dùng `start < other.end AND end > other.start` cho OPEN/IN_PROGRESS, gồm qua đêm/nhiều ngày; hai chuyến tiếp giáp được phép. Không khóa trip khác khi kiểm lịch để tránh vòng chờ. Mutation tăng version/CAS và ghi trip_events cùng transaction; lỗi lịch sử rollback cả thay đổi. Snapshot địa điểm cũ cũng phải active trước mở bán, kể cả mẫu đã thay điểm.

`/operations/trips` có TRIP_VIEW riêng, modal thông tin/điểm/lịch sử, picker tìm và phân trang server, lỗi giữ draft. TRIP001 là dữ liệu không hợp lệ (400), TRIP002 lịch trùng và TRIP003 trạng thái không cho phép (409); version cũ COM008/409. Không auto grant role thường. Đây là điều hành staff; luồng tài xế tiến độ, booking/giải phóng chỗ khi hủy, tìm công khai, GPS và báo cáo được tích hợp ở các bước BUSINESS04 kế tiếp, chưa suy từ CRUD chuyến là đã hoàn tất các luồng đó.

## Chuyến được giao và tiến độ — DRIVER02

GET `/api/v1/driver/trips` và `/{id}` dùng DRIVER_TRIP_READ; PUT `/{id}/progress` dùng DRIVER_TRIP_UPDATE. Trang `/driver/trips` dùng DRIVER_TRIP_VIEW riêng. SQL count/page/ID đồng thời yêu cầu user là driver_user_id, hồ sơ driver vẫn active/liên kết user và global + scoped DRIVER còn hiệu lực trong đúng scope. Không trả DRAFT hoặc chuyến người khác; ADMIN không tự đóng vai tài xế. Thu hồi liên kết/membership/role có hiệu lực tại lần kiểm tiếp theo, không hứa hủy request đã chạy.

Progress JSON gồm action START/ARRIVE/DEPART/COMPLETE, stopOrder, version, note. START chỉ OPEN tại điểm 1 và từ 30 phút trước departure; ghi actual đến/rời điểm đầu cùng thời gian server, kiểm lại assets/driver ready/lịch xe đang chạy. ARRIVE cần điểm trước đã rời; DEPART cần đã đến điểm giữa; COMPLETE cần đã đến điểm cuối. Không bỏ điểm/đi lùi/tự gửi actual timestamp. Khóa trip trước mọi thay đổi, CAS version, actual/state/events cùng connection; lỗi event rollback toàn bộ và hai START cùng version chỉ một thành công. Đây là tiến độ do tài xế xác nhận, không phải GPS.

PUT `/api/v1/admin/trips/{id}/notice` dùng TRIP_UPDATE trong scope, JSON note không rỗng + version. Chỉ OPEN/IN_PROGRESS; tăng version, giữ trạng thái và lưu thông báo + NOTICE nguyên tử. Không cho hủy IN_PROGRESS hoặc tự hoàn tiền/chuyển khách. UI staff cho ghi chú/thông báo trong modal ngay cả khi chuyến đang chạy; không mở lại form phân công.

BOOKING01 bổ sung hooks cùng connection ở mục dưới: đóng PENDING tại điểm rời, hoàn thành CONFIRMED tại điểm trả và cascade hủy chuyến. Tiến độ xác nhận vẫn tách khỏi GPS.

## Đặt chỗ theo chặng và tìm công khai — BOOKING01/SEARCH01

V020 thêm `bookings`/`booking_events`, giữ V001–V019. Đơn gắn FK composite trip/carrier/scope và hai điểm snapshot. WEB phải có customer; PHONE không cần tài khoản khách. Giá chốt bằng giá cộng dồn của snapshot tại lúc gửi, không nhận owner/source/giá/audit từ client. Partial unique chỉ một WEB PENDING/CONFIRMED mỗi customer; khi tạo WEB khóa user sau trip để tuần tự hóa cả hai chuyến khác nhau. PENDING không giữ chỗ.

| API | Quyền và dữ liệu |
|---|---|
| GET `/api/v1/trips` | Công khai; fromLocationId hoặc fromProvinceId, toLocationId hoặc toProvinceId, date ngày pickup Việt Nam, seats 1–200, carrierId/vehicleTypeId tùy chọn, page/size. SQL lọc hướng/điểm chưa rời/nhà xe và scope active/MIN chỗ trên từng range; count/trang cùng predicate. Chỉ chuyến từng OPEN, đang IN_PROGRESS hoặc OPEN chưa quá giờ đầu. |
| GET `/api/v1/trips/{id}` và `/{id}/quote` | Công khai, projection tường minh không customer/account/driver PII; chi tiết snapshot/tiến độ/thông báo vẫn đọc được sau hủy/hoàn thành, DRAFT 404. Quote nhận pickupStopId/dropoffStopId/seats và tính tại server; chỉ báo khả năng nhận hiện tại, không giữ chỗ. |
| GET `/api/v1/my-bookings` và `/{id}` | MY_BOOKING_READ + CUSTOMER ready hiện hành, chỉ owner; query/tripId/status/page/size lọc tại DB. ID người khác 404. |
| POST `/api/v1/my-bookings` | MY_BOOKING_CREATE + CUSTOMER ready; tripId/pickupStopId/dropoffStopId/seats/contactName/contactPhone/requestKey UUID. Actor/source/giá lấy tại server. |
| PUT `/api/v1/my-bookings/{id}/state` | MY_BOOKING_UPDATE, owner; chỉ CANCELLED trước actual departure pickup, version/note bắt buộc. |
| GET `/api/v1/admin/bookings` và `/{id}` | BOOKING_READ + scope trong SQL count/page/ID; không dùng CUSTOMER owner để mở dữ liệu nhà xe khác. |
| GET `/api/v1/admin/bookings/trips` | BOOKING_CREATE trong scope, picker PHONE query/page/size độc lập TRIP_READ. |
| POST `/api/v1/admin/bookings` | BOOKING_CREATE + scope; cùng payload đặt nhưng server cố định PHONE và customer NULL. Dùng chung thuật toán chỗ/giá. |
| PUT `/api/v1/admin/bookings/{id}/state` | BOOKING_UPDATE + scope; PENDING→CONFIRMED/REJECTED/CANCELLED, CONFIRMED→CANCELLED trước pickup đã rời. Version, lý do từ chối/hủy. |
| PUT `/api/v1/admin/bookings/{id}/payment` | BOOKING_UPDATE + scope; kind PAYMENT/REFUND, amount, note, version, requestKey. Ghi nhận thủ công đã diễn ra ngoài hệ thống: chỉ thu CONFIRMED/COMPLETED, hoàn CANCELLED; tổng thu ≤ giá đơn, tổng hoàn ≤ đã thu. Không gọi provider. |
| GET `/api/v1/driver/trips/{id}/passengers` | DRIVER_TRIP_READ, guard đúng assigned/global+scoped DRIVER/link hiện hành trong cùng connection; phân trang CONFIRMED/COMPLETED theo đón/trả. Chỉ mã/chặng/số chỗ/tên/điện thoại cần phục vụ; không email/user ID/giá/audit. |

Mutation khóa trip → user (chỉ tạo WEB) → booking → segments tăng dần. `TripCapacityRepository` là một thuật toán dùng chung; confirm tăng đúng [pickup,dropoff), hủy CONFIRMED giảm đúng một lần, kiểm liên tục và đủ mọi chặng. CAS và booking_events cùng transaction; history lỗi rollback. UUID tạo/tiền replay cùng actor và nội dung trả kết quả cũ, khác nội dung bị chặn; không tự retry mutation. Hủy trip cascade PENDING/CONFIRMED và giải phóng chỗ trong transaction trip. START/DEPART đóng PENDING tại điểm đã rời; ARRIVE hoàn thành CONFIRMED ở điểm trả, giữ reserved lịch sử. Hoàn thành/hủy kết thúc ràng buộc một WEB active.

BOOKING001 dữ liệu sai/400, BOOKING002 thiếu chỗ/409, BOOKING003 trạng thái/409, BOOKING004 đã có WEB active/409, BOOKING005 UUID khác nội dung/409, BOOKING006 thu/hoàn vượt giới hạn/400; version cũ COM008/409. Tiền hủy chưa hoàn có refundDue, không tự ghi đã hoàn. Trang `/journeys` công khai, `/my-bookings` MY_BOOKING_VIEW và `/operations/bookings` BOOKING_VIEW độc lập READ. Startup sync registry/ADMIN khi bật; CUSTOMER/OPERATOR vẫn cần mapping DB tường minh. Maps/GPS và báo cáo tiếp tục riêng; không suy từ chi tiết text là đã có GPS thật.

## Tra cứu nhà xe và bộ lọc chuyến — DETAIL01/SEARCH02

GET `/api/v1/carriers` công khai nhận query/page/size; count/trang trong SQL, từ khóa nguyên văn không phân biệt hoa/thường, thứ tự tên/id ổn định. GET `/{id}` chỉ nhà xe và scope còn active, còn lại 404. DTO chỉ id/code/name/phone/description/logoUrl, không scope/audit/version/nhân sự/customer. Đây là thông tin và liên hệ nhà xe đã công bố; không dùng staff membership để lọc lựa chọn của khách. Các API quản trị `/api/v1/admin/carriers` và metadata `/api/v1/media/{id}` vẫn có guard riêng, không mở theo matcher public.

Logo được lấy từ FK server sau kiểm công bố, dùng MediaService đọc URL READY theo lô tối đa 100 ID trên cùng connection, không gọi CDN/N+1 hoặc trả provider ID/metadata thư viện. Ảnh không READY không công bố URL. Trang `/carriers` có tìm/phân trang, modal liên hệ và link `/journeys?carrierId=...`; URL chỉ là bộ lọc, không là quyền/scope.

GET `/api/v1/trips` thêm minPrice/maxPrice nullable: giá một chỗ VND trên đúng cặp điểm, gồm hai cận, không âm, tối đa 10 chữ số phần nguyên/2 thập phân và min≤max. Khoảng đảo trả 400/COM001 với field priceRangeValid, VAL015/validation.priceRange; OpenAPI gồm cả from/to, date/seats/carrierId/vehicleTypeId/minPrice/maxPrice/page/size kế thừa. Picker nhà xe dùng public projection trên; loại xe dùng catalog lookup active, đều có tìm/trang và giữ lựa chọn ngoài trang. Giá và count vẫn tính tại DB, không lọc sau phân trang ở JavaScript. Không migration hoặc ordinary role grant mới.

## Báo cáo vận hành — REPORT01

`GET /api/v1/admin/reports` dùng REPORT_READ scoped; `GET /api/v1/admin/reports/carriers` là picker query/page/size cùng quyền, không đòi CARRIER_READ. VIEW riêng bảo vệ `/operations/reports`. ADMIN tổng hợp mọi nhà xe trong scope active; người dùng khác chỉ các membership/role hiện hành có REPORT_READ, không union quyền từ scope khác. Carrier ID ngoài phạm vi trả 404. Picker chỉ có id/code/name/active, không xuất scope/audit/nhân sự. Nhà xe ngừng kinh doanh vẫn có lịch sử khi scope bảo mật còn active.

`fromDate`/`toDate` là ngày khởi hành tại Việt Nam, bao gồm hai cận, tối đa 366 ngày (VAL016). Mặc định 30 ngày đến hôm nay; carrierId tùy chọn. PostgreSQL đổi cận thành [đầu ngày, đầu ngày kế tiếp) và tổng hợp bằng một statement snapshot, tách aggregate chuyến/đơn để không nhân trùng số chuyến. DTO xuất thời điểm asOf, số chuyến/đơn cùng 5 trạng thái hữu hạn mỗi loại, lượt chỗ và giá chốt của CONFIRMED/COMPLETED, tổng đã thu/đã hoàn, thu trừ hoàn và tiền của đơn CANCELLED còn phải hoàn. Không xuất PII hoặc chi tiết đơn qua báo cáo.

Tiền là số hiện tại của đơn thuộc nhóm chuyến khởi hành trong khoảng, kể cả đơn hủy; không phải dòng tiền theo ngày ghi nhận payment. PENDING không là chỗ đã bán hoặc doanh thu; confirmedValue không là paidAmount. Mỗi chỗ của một đơn là một lượt, không phải ghế vật lý duy nhất. Không migration, không tự cấp REPORT cho role thường; vận hành gán mapping bằng API quản trị sau triển khai. Không phát sinh thanh toán hoặc hoàn tiền khi đọc báo cáo.

## Cấp tài khoản tài xế theo nhà xe — AUTH03

`POST /api/v1/admin/drivers/{id}/account` dùng DRIVER_ASSIGN toàn cục và trong scope của hồ sơ đã đọc từ DB; không yêu cầu hoặc cấp ACCOUNT_*/ACCESS_ASSIGN cho OPERATOR. Request chỉ username/email/password/version hồ sơ. Không nhận role, scope, trạng thái xác minh hoặc hash. Username chuẩn hóa chữ thường, BCrypt trước transaction; chặn tên bootstrap và xung đột username/email, không sửa tài khoản có sẵn.

Khóa nhà xe → hồ sơ, kiểm version/chưa liên kết/cha active và không có trip DRAFT/OPEN/IN_PROGRESS. Auth callback kiểm lại actor/scope, khóa gán role, tạo duy nhất DRIVER toàn cục + membership DRIVER đúng scope. Ghi ACCOUNT_CREATED và SCOPE_MEMBERSHIP cùng connection/history nguyên tử; lỗi lịch sử trả 503 và rollback cả credential/mapping/link. Hai request đồng thời chỉ một account được commit. Không migration hoặc auto-grant role thường.

Luồng cấp nhân sự chỉ chấp nhận registry DRIVER có quyền thuộc DRIVER_TRIP_VIEW/READ/UPDATE hiện đã khai báo, kể cả kiểm mapping quyền đang tắt; cấu hình mạnh hơn trả DRIVER001/409 để ADMIN rà soát. Đây là hợp đồng nghiệp vụ cấp tài xế, không quyền ủy quyền role bất kỳ hoặc bypass guard. Account mới active, must_change_password=true, email chưa xác minh; không tạo challenge/outbox hoặc cấp ngoại lệ AUTH011/AUTH17. Hồ sơ được liên kết nhưng active=false; tài xế hoàn tất đổi mật khẩu/email qua auth rồi nhà xe bật lại hồ sơ mới phân chuyến. Gmail tạm hoãn nghĩa là bước xác minh thư chưa dùng được; ba account cấp sẵn trước không bị đổi.

Response chỉ userId/username/driverId/driverVersion và trạng thái cần thiết lập, không email/hash/password. UI giao endpoint capabilities với canAssign scoped từ SQL. Đổi userId qua CRUD hồ sơ thông thường cũng bị chặn khi còn chuyến DRAFT/OPEN/IN_PROGRESS; khóa cha cùng thứ tự tạo/phân chuyến. Chuyến cũ giữ driver_user_id snapshot, tài khoản mới không kế thừa danh sách/chặng khách của tài xế cũ.

## Trang tổng quan và điều hướng — BUSINESS05

`GET /dashboard` là trang HTML cần xác thực, không có endpoint aggregate hoặc bảng mới. Home/login đưa phiên sẵn sàng tới dashboard; cờ mustChangePassword/emailVerificationRequired vẫn đưa tới account. Dashboard chỉ GET các API hiện hành theo capabilities: my-bookings của CUSTOMER, driver/trips của DRIVER, reports và admin/bookings theo quyền hành động. Guard/scope/owner/version vẫn do các service cũ kiểm, không lấy role/tenant từ query. Mỗi danh sách lấy tối đa 5 bản ghi và tổng PostgreSQL; báo cáo dùng nhóm ngày khởi hành từ đầu tháng tới hết tháng kế tiếp tại Việt Nam, tiền thực thu không phải giá chốt. Menu/breadcrumb giữ locale/context và lọc VIEW; role owner/phân công là điều kiện nghiệp vụ bổ sung, không thay quyền READ. Chi tiết trình bày ở [quy ước UI](../03-giao-dien/QUY_UOC_UI.md#breadcrumb-và-khoảng-cách-khối--business05).

## Chia sẻ vị trí riêng tư — TRACK01

V021 thêm trip_tracking (consent/snapshot) và trip_locations (mẫu thiết bị) có FK trip/carrier/scope/actor, audit Instant/TIMESTAMPTZ, giữ V001–V020 nguyên vẹn. API riêng tư dưới `/api/v1/driver/trips/{id}/tracking` (GET READ, POST start/stop/samples UPDATE), `/api/v1/admin/trips/{id}/tracking` (TRIP_READ scoped) và `/api/v1/my-bookings/{id}/tracking` (MY_BOOKING_READ, CUSTOMER ready, owner). Không đặt GPS dưới matcher public trips; chỉ đơn CONFIRMED hiện hành được nhận vị trí, hủy/hoàn tất/chờ trả UNAVAILABLE. ADMIN không phát mẫu thay tài xế; assigned driver/profile/membership vẫn kiểm hiện hành. Việc công bố còn kiểm quyền cập nhật hiệu lực của tài xế từ các role trong đúng scope, không bắt quyền chỉ nằm ở role DRIVER và không union role nhà xe khác; giữ ngữ nghĩa ADMIN hiện hành khi chính người đó cũng là tài xế được giao.

Bật chia sẻ cần version chuyến và chủ động của tài xế, trước lịch khởi hành tối đa 30 phút hoặc khi đang chạy. Server cấp nonce mới, xóa snapshot cũ; nonce không thay JWT/quyền và chỉ trả qua POST bật, không có trong DTO xem. Mẫu WGS84/accuracy/source BROWSER với measuredAt UTC độ phân giải millisecond; server gán actor/receivedAt. Giới hạn tuổi 120 giây, tương lai 15 giây, tối thiểu 10 giây mỗi mẫu mới. Mẫu sai thứ tự hoặc cùng timestamp khác dữ liệu bị chặn; replay đúng không thêm trace/gia hạn lease. Lease 120 giây, stale sau 90 giây; hết lease ẩn tọa độ và trace. Stop cùng nonce lặp lại an toàn; nonce cũ không tắt lần consent mới.

Khóa trip trước state/sample; snapshot và trace cùng transaction, lỗi insert rollback snapshot. Hủy, đổi phân công hoặc hoàn tất trip xóa snapshot/tắt chia sẻ cùng transaction. Tọa độ không tự đóng điểm, đổi trạng thái trip hoặc đơn. Viewer được kiểm owner/assigned/scope, không trả actor/scope/nonce. Google Maps giữ UNCONFIGURED theo lựa chọn chủ dự án, chưa gửi tọa độ sang provider; không tạo mẫu GPS DEMO trên Neon.

`VIETROUTE_TRACKING_ENABLED` mặc định true chỉ cho phép tính năng, không tự xin quyền thiết bị hoặc bật chia sẻ. Trace trả tối đa 100 mẫu cùng consent, bỏ mẫu nhận quá 7 ngày ngay trong query. Khi nhận mẫu mới, cleanup PostgreSQL theo lô 500 với advisory lock, tối đa 2000 mẫu/trip và mục tiêu 10000 toàn app; không có hàng đợi lịch sử RAM. Việc xóa vật lý quá hạn chạy theo hoạt động gửi mẫu, không cam kết job xóa đúng hạn khi toàn hệ thống ngừng tracking. TRACK001 invalid/400, TRACK002 consent/trạng thái/409, TRACK003 tần suất/429, TRACK004 feature tắt/503. Bốn ca PostgreSQL cô lập kiểm quyền/owner/scope, thu hồi quyền tài xế ẩn vị trí, lease/time/replay/race, rollback/retention và kết thúc. UI có tab vị trí riêng và watchPosition opt-in; 8 ca Node adapter thiết bị giả kiểm từ chối/timeout/mất mạng/race đóng trong lúc bật, không thay nghiệm thu GPS vật lý. Cấu hình an toàn có trackingEnabled/mapStatus, không secret.
