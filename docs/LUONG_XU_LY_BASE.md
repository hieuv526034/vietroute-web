# Học và mở rộng source base

## Đọc code theo luồng request

1. `RequestTraceFilter` gắn `X-Request-ID` để đối chiếu response với log.
2. Spring Security kiểm CSRF, JWT và quyền truy cập. `JwtAuthenticationFilter` ưu tiên Bearer; nếu không có header mới lấy cookie. Sau xác minh chữ ký/thời hạn, filter đọc lại user/role/tokenVersion từ DB.
3. Spring MVC đọc JSON, chuyển enum/kiểu dữ liệu và chạy `@Valid` trước khi gọi controller. Lỗi bước này đi vào `GlobalExceptionHandler`.
4. Controller auth/catalog kế thừa `BaseController`, gọi `execute(...)` và truyền phần việc dưới dạng `Supplier<T>`. Base gọi callback đúng một lần trong cùng thread, tạo envelope thành công hoặc bắt `Exception` và chuyển qua `ApiExceptionMapper`.
5. Service kiểm tra nghiệp vụ/quyền và quyết định transaction. Repository chỉ bind tham số SQL, thực hiện câu lệnh rồi map dữ liệu; mọi bảng ứng dụng nằm trong `vietroute`.
6. `ApiExceptionMapper` là nơi duy nhất phân loại lỗi MVC/nghiệp vụ thành HTTP status, code, key dịch và lỗi field. Response luôn dùng factory `ApiResponses`.

Lỗi từ filter xảy ra trước MVC nên được ghi bằng `ApiErrorWriter`, vẫn dùng cùng enum/factory. `/health` trả text và `/v3/api-docs` trả đặc tả OpenAPI thuần theo hợp đồng của hai endpoint; không bọc JSON thành công của chúng vào envelope nghiệp vụ.

## Base API và try/catch

Xem `common/base/BaseController.java`, `auth/controller/AuthController.java` và `business/catalog/controller/CatalogController.java` làm mẫu thực tế.

- API đọc/cập nhật: `execute(SuccessCode.OK, request, callback)` trả HTTP 200 khi thành công.
- API tạo mới: `execute(HttpStatus.CREATED, SuccessCode.CREATED, request, callback)` trả HTTP 201 khi thành công. Gắn `@ResponseStatus(HttpStatus.CREATED)` để Swagger ghi đúng status; `ResponseEntity` vẫn mang status lỗi 400/403/409/500 khi callback thất bại.
- Đặt cả phần việc có thể ném lỗi của endpoint bên trong callback. Không tự trả `null`, HTTP 200 hoặc `exception.getMessage()` sau khi bắt lỗi.
- Lỗi nghiệp vụ: ném `BusinessException(ErrorCode, args)` ở service. Thêm mã mới vào enum và cả hai bundle vi/en trước khi sử dụng.
- Lỗi chưa biết: trả COM006/HTTP 500 và traceId. Chi tiết chỉ ghi phía server. Base không bắt `Error` của JVM.
- Không viết `try/catch(Exception)` lặp lại trong mỗi controller. Chỉ bắt tại nơi có hành động cụ thể như rollback, đóng resource, giải mã token hoặc chuyển lỗi ở biên HTTP.

`ApiExceptionMapper` được dùng cả ở base lẫn advice nên lỗi sau khi vào controller và lỗi trước controller có cùng cấu trúc. API mới vẫn cần `@Operation`, DTO có `@Schema` và kiểu response cụ thể để OpenAPI suy ra generic.

## Transaction và đăng nhập

`JdbcTransactions.execute` mượn connection từ HikariCP → đặt schema → tắt autocommit → chạy callback → commit → trả connection về pool. `SQLException`, lỗi nghiệp vụ/runtime hoặc `Error` làm rollback trước khi trả connection; `Error` vẫn ném tiếp lên JVM, không chuyển thành lỗi nghiệp vụ. DAO trong callback dùng cùng connection và không tự commit/đóng connection được truyền vào. Spring đóng pool khi ứng dụng dừng; pool tạo lười và không mở DB chỉ vì khởi tạo context. Các giới hạn pool/timeout nằm trong `DatabaseSettings`, xem `database/README.md`.

Luồng đăng nhập khóa dòng user để các lần sai đồng thời không làm mất bộ đếm. Nếu sai mật khẩu, callback trả `LoginOutcome` để **commit bộ đếm sai trước**, sau đó service mới ném `BusinessException`. Ném lỗi ngay trong transaction sẽ rollback bộ đếm và làm cơ chế khóa sai. Khi đổi mật khẩu, service kiểm tra mật khẩu cũ/tokenVersion dưới khóa, đổi hash và tăng tokenVersion trong cùng transaction; hoàn tất mới phát cookie/token mới.

## Schema, table và index trong entity

Schema cấu hình qua `VIETROUTE_DB_SCHEMA` (mặc định vietroute), database production vẫn neondb. JdbcConnectionFactory đặt lại search_path khi mượn connection; repository dùng tên bảng không gắn schema, không fallback public/$user. Xem [cấu hình schema và quyền](SCHEMA_VA_PHAN_QUYEN.md).

Entity JDBC dùng `@JdbcTable(name = ..., primaryKey = ..., indexes = ...)`. Mỗi `@JdbcIndex` ghi tên, thứ tự cột và unique. UserAccount/CatalogItem khai báo cả index PK/UNIQUE và index đọc/audit. Validator dùng schema thực tế trên connection; roles được repository nạp từ auth_user_roles trong schema cấu hình, không phải cột JSON/ORM relationship.

Metadata này giúp đọc code và kiểm chứng cấu trúc, **không tự sinh DDL**. Migration SQL là nguồn tạo/sửa bảng. `JdbcSchemaValidator` kiểm tra primary key và index thực tế trước seed; mismatch làm startup thất bại và transaction rollback. Khi thêm entity/module, tạo migration version mới, khai báo metadata rồi đưa class vào danh sách kiểm chứng lúc startup.

Startup được bật bằng `VIETROUTE_DB_INIT_ENABLED=true`:

1. Lấy advisory transaction lock để hai instance không migrate/seed cùng lúc.
2. SchemaBootstrap kiểm một history trong DB, tạo schema cấu hình; nếu history legacy public thì chuyển sang schema đó. History nơi khác/nhiều history hoặc legacy thiếu history bị chặn trước seed.
3. Kiểm tra/chạy V001–V003. DB mới tạo bảng ở schema cấu hình; legacy public được V002 chuyển vào đó, giữ ID/FK/sequence/index/dữ liệu. V001–V003 nguyên byte/checksum; adapter chỉ ánh xạ schema SQL lịch sử V002/V003 trong RAM. Query/DDL mới dùng search_path cấu hình.
4. Đối chiếu metadata entity với DB, seed role/admin/catalog theo khóa duy nhất, rồi commit. Admin có sẵn không bị reset mật khẩu.

Chạy Maven build không khởi tạo DB. Muốn chạy app và có dữ liệu ngay, đặt các biến DB/JWT/bootstrap và bật initializer trong cấu hình chạy IDE/Tomcat hoặc Render; `.env` không được Java tự nạp. CI dùng PostgreSQL tạm để kiểm DB mới, khởi động đồng thời, restart và nâng cấp từ `public`.

## Lambda, import và cách viết dễ học

Nếu lambda chỉ chuyển tiếp nguyên lời gọi, dùng method reference: `cache -> cache.disable()` đổi thành `AbstractHttpConfigurer::disable`. Giữ lambda khi cần truyền thêm tham số hoặc có nhiều bước nghiệp vụ, ví dụ `() -> auth.me(user)`; không tách hàm vô nghĩa chỉ để xóa mọi dấu `->`.

Class được import tường minh ở đầu file. Không viết `vn.vietroute.common.web.ApiResponse` hoặc `java.util.UUID` trong thân hàm; dùng `ApiResponse`, `UUID` sau khi import. Khi hai thư viện trùng tên, tách phần trách nhiệm sang class riêng; `OpenApiModels` minh họa cách tách Swagger model khỏi annotation mà không viết tên package dài trong code. Chuỗi package cấu hình component scan hoặc tên driver dùng reflection vẫn là chuỗi cấu hình hợp lệ.

Comment/Javadoc tiếng Việt mô tả mục đích, thứ tự, điều kiện và lý do transaction/quyền; không chỉ diễn đạt lại từng lệnh hiển nhiên. Dùng format 4 spaces, mỗi lệnh/annotation một dòng, có dòng trống giữa method/field và các khối xử lý. Chạy `npm run format`, `npm run format:check`, rồi `mvnw verify` trước khi mở PR; production Docker vẫn bỏ compile/chạy/đóng gói test.
