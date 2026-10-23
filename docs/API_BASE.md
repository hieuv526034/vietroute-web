# Hợp đồng API nền tảng

Quản trị hệ thống bổ sung: `GET /api/v1/admin/system/configuration` dùng SYSTEM_READ và DTO whitelist chỉ đọc; `GET /api/v1/admin/access/users` dùng ADMIN + ACCESS_READ, prefix username/page/size; list jobs có query status tùy chọn. UI và hợp đồng chi tiết tại [QUAN_TRI_HE_THONG.md](QUAN_TRI_HE_THONG.md). Không có API ghi env/credential.

Giám sát bổ sung: `GET /api/v1/admin/system/runtime` dùng SYSTEM_READ và envelope chung; `GET /monitoring` là trang chỉ đọc cùng quyền. `GET /internal/metrics` là Prometheus text, token vận hành riêng, không bọc envelope/JWT và không truy vấn DB. Token rỗng đóng endpoint. Xem [giám sát và vận hành](GIAM_SAT_VA_VAN_HANH.md) và [tổng quan](TONG_QUAN_HE_THONG.md).

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

View Thymeleaf: `/`, `/login`, `/account` (đăng nhập), `/admin` (ADMIN). Trên Tomcat deploy WAR theo tên, thêm context `/vietroute`; Docker deploy `ROOT.war` nên dùng đường dẫn ở bảng trực tiếp.

API mở rộng: quản lý `/api/v1/admin/access/**`, theo dõi/retry `/api/v1/admin/jobs/**`, readiness `/api/v1/admin/system/status`, mail queue `/api/v1/admin/mail` (202, cần Idempotency-Key). Mỗi API có authority tương ứng tại service; `/auth/me` trả permissions. Catalog CRUD dùng quyền hành động trong bảng trên. COM012/429 là quota (khác AUTH005 khóa tài khoản), COM013/503 là quá tải/Redis lỗi, COM014/413 là body lớn; MAIL001/503 là mail tắt. Đọc [hợp đồng và luồng đầy đủ](NEN_TANG_MO_RONG.md).

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

Quyền ADMIN/OPERATOR/DRIVER/CUSTOMER là nền. Các module đa nhà xe sau này phải thêm companyId/ownership checks tại service; role đơn thuần không đủ bảo vệ tenant. Chưa có API đăng ký, tạo tài khoản nhà xe hoặc quên mật khẩu; đây là các task riêng, không giả định đã có trong base.

## Audit, transaction và module mẫu

API nghiệp vụ/auth kế thừa `common/base/BaseController` và dùng `execute(...)` để bọc kết quả trong `ResponseEntity<ApiResponse<T>>`. Base có try/catch tại biên HTTP; `ApiExceptionMapper` phân loại lỗi và tạo phản hồi. `GlobalExceptionHandler` dùng cùng mapper cho lỗi JSON/validation/binding xảy ra trước khi vào controller. Controller không sao chép catch hoặc đổi lỗi thành HTTP 200. Xem [luồng và ví dụ học source](LUONG_XU_LY_BASE.md).

- BaseEntity: id, createdAt/updatedAt, createdBy/updatedBy, version. DTO response kế thừa BaseResponse; request kế thừa BaseRequest và không nhận audit từ client.
- `CatalogCreateRequest` có type VEHICLE_TYPE hoặc BOOKING_SOURCE, code tùy chọn, nameVi/nameEn bắt buộc, sortOrder 0–10000. Response trả `name` theo locale cùng hai bản dịch và audit.
- `PageRequest`: page 0–1000000, size 1–100; `PageResponse<T>` gồm content/page/size/totalElements/totalPages/hasNext/hasPrevious.
- Service dùng `JdbcTransactions.execute(connection -> ...)`; mọi DAO trong giao dịch nhận cùng connection, không tự commit/close. SQLException hoặc RuntimeException rollback, cuối cùng đóng connection.
- `BaseRepository.read` tự đóng connection cho truy vấn đơn. Không mở connection mới giữa một luồng thay tồn chỗ/đơn cần atomic.
- `@BusinessCode` không bắt buộc null; kết hợp @NotBlank khi trường phải có. Tên tiếng Việt không áp regex mã. Code tự sinh vẫn có UNIQUE của DB.
- Thêm API: đặt @Operation, request/response @Schema; dùng request/response typed, không trả entity hoặc map tùy tiện. OpenAPI generator hiện hỗ trợ JSON body, PathVariable, RequestParam, PageRequest, generic envelope/PageResponse và ResponseEntity. API mới có multipart hoặc binding phức tạp cần mở rộng generator và kiểm tra schema tương ứng.
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

API ảnh ở `/api/v1/media` có upload multipart, đọc/phân trang và xóa qua worker; quyền MEDIA_READ/CREATE/DELETE cùng kiểm owner. Cấu hình, giới hạn file, luồng phục hồi và hướng dẫn Run/Debug IntelliJ nằm trong [Cloudinary và IntelliJ](CLOUDINARY_VA_INTELLIJ.md).
