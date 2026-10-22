# Quy ước nền tảng VietRoute

## Phạm vi và thứ tự triển khai

Lượt nền tảng: common → auth/JWT/bootstrap → danh mục mẫu → layout/Swagger → kiểm thử và CI. Sau đó mới triển khai lần lượt các nghiệp vụ theo `PHAN_CONG.md`. Không coi các module đặt vé, tuyến/chuyến hoặc Maps là đã hoàn thành khi mới có package.

Giữ Spring MVC 6, Thymeleaf, JDBC thuần, PostgreSQL và WAR/Tomcat 10.1. Spring Security xử lý bảo mật; Hibernate Validator chỉ dùng Bean Validation, không sử dụng Hibernate ORM/JPA. Không chuyển sang Spring Boot.

Đọc [nền tảng mở rộng](NEN_TANG_MO_RONG.md) trước khi thêm permission, job hoặc email. Quyền chức năng kiểm ở service bằng authority; scope nhà xe/owner kiểm riêng ở SQL/service khi module có dữ liệu. Email đi qua outbox cùng transaction nghiệp vụ; worker chỉ nhận việc khi có slot, không tự tạo thread cho từng request. Giữ stack theo quy ước hiện tại, không tự đổi major để lấy nhãn mới nhất.

## Cấu trúc module

```text
vn.vietroute
  common/
    base/          BaseController, BaseEntity, BaseDTO, BaseRequest, BaseResponse, PageRequest/PageResponse
    constant/      Constants, ErrorCode, SuccessCode: mã ổn định và key dịch
    exception/     BusinessException, xử lý exception và trả lỗi thống nhất
    validation/    Annotation và quy tắc dùng chung
    util/          Tạo mã, chuẩn hóa dữ liệu dùng chung
    jdbc/          Connection, transaction và repository JDBC cơ sở
    config/        Cấu hình root/web, locale, JSON, bootstrap và OpenAPI
    web/           Envelope, locale, trace ID và controller hạ tầng
  auth/
    config/        Spring Security và JWT
    controller/    API auth và trang đăng nhập/tài khoản
    dto/request/   LoginRequest, ChangePasswordRequest
    dto/response/  AuthResponse, UserResponse
    entity/        UserAccount, Role
    repository/    SQL tài khoản/quyền
    service/       Đăng nhập, đổi mật khẩu, thu hồi token và seed
    security/      Principal, filter JWT, lỗi bảo mật
  business/
    catalog/       controller, dto/request, dto/response, entity, repository, service
    carrier/       Nhà xe, phương tiện, tài xế (triển khai sau)
    network/       Địa điểm, tuyến, điểm dừng, chuyến (triển khai sau)
    booking/       Đặt/xác nhận/hủy chỗ (triển khai sau)
    tracking/      Vị trí, Maps, tiến độ (triển khai sau)
```

Controller chỉ nhận/validate input và gọi service. Service giữ transaction và quy tắc nghiệp vụ; repository dùng PreparedStatement/ResultSet. Entity không trả thẳng ra API; response thực hiện mapping rõ ràng, không kéo password hash hoặc token version ra client. Không tạo generic CRUD bỏ qua quyền và tenant.

API auth/nghiệp vụ kế thừa `BaseController`, dùng `execute(...)` có try/catch chung và `ApiExceptionMapper`. Advice dùng cùng mapper cho lỗi trước controller. Đọc [luồng xử lý và cách học base](LUONG_XU_LY_BASE.md) trước khi thêm module.

## Hợp đồng API và locale

API có tiền tố `/api/v1`. Envelope gồm `success`, `code`, `messageKey`, `message`, `data`, `errors`, `timestamp`, `path`, `traceId`, `locale`. HTTP status phải đúng ý nghĩa; không trả HTTP 200 cho lỗi. Không đưa stacktrace, SQL, thông tin kết nối hoặc rejected password vào response.

Ưu tiên ngôn ngữ: `?lang=vi|en` → `X-Locale: vi|en` → `Accept-Language` → `vi`. Query/header locale tường minh không hợp lệ trả lỗi; Accept-Language không hỗ trợ dùng mặc định. Tất cả key API/validation/UI có bản vi và en. Mã lỗi và messageKey giữ nguyên giữa hai ngôn ngữ.

Thêm lỗi vào `ErrorCode`, thêm key ở cả `messages_vi.properties` và `messages_en.properties`, rồi dùng `throw new BusinessException(ErrorCode..., args)`. Chi tiết field có `field`, `code`, `messageKey`, `message`; không parse câu thông báo để xác định lỗi. Security filter dùng cùng envelope với controller advice.

Mỗi request có `X-Request-ID` và `traceId` trong response để tra log. Thời gian audit lưu UTC bằng Instant/TIMESTAMPTZ; giao diện hiển thị theo múi giờ Việt Nam. Tiền dùng BigDecimal/NUMERIC, không dùng float/double.

## Đặt tên và format source

Tên lớp có chữ viết tắt DTO phải viết hoa đầy đủ: `BaseDTO`, `CustomerDTO`; không viết `BaseDto` hoặc `CustomerDto`. Package Java vẫn viết thường `dto/request`, `dto/response`. Các tên thể hiện hợp đồng như `LoginRequest`, `UserResponse`, `PageResponse` giữ nguyên, không cần thêm hậu tố DTO lặp nghĩa.

Mọi thời điểm trong entity, DTO, request/response và hợp đồng service dùng `java.time.Instant`; JSON trả ISO-8601 UTC, ví dụ `2026-10-21T08:30:00Z`. Không dùng `java.util.Date`, `java.sql.Date` hoặc `LocalDateTime` thay cho thời điểm. JDBC chuyển `Timestamp` sang/từ `Instant` ngay tại repository; không đưa kiểu JDBC ra ngoài lớp truy cập dữ liệu. JavaScript chỉ dùng `Date` khi định dạng chuỗi thời gian để hiển thị theo múi giờ, không thay đổi hợp đồng API.

Thụt lề 4 dấu cách, không dùng tab; mỗi lệnh và annotation một dòng. Có dòng trống giữa các field, các method và các khối xử lý có ý nghĩa. CSS mỗi thuộc tính một dòng, các selector tách nhau; HTML xuống dòng rõ theo cấu trúc. Giới hạn dòng khoảng 120 ký tự, không ép cắt chuỗi SQL/URL gây đổi giá trị.

Import class tường minh ở đầu file, không dùng wildcard hoặc tên package đầy đủ trong thân code. Nếu trùng tên thư viện, tách trách nhiệm sang class riêng như `OpenApiModels`. Dùng method reference khi lambda chỉ gọi chuyển tiếp; giữ callback nhiều bước/có tham số capture để người đọc hiểu luồng. Không tắt inspection để giấu cảnh báo.

Cài Node.js khi cần dùng bộ format ở máy phát triển:

```powershell
npm ci
npm run format
npm run format:check
```

Phiên bản Prettier cùng plugin Java/XML được khóa trong `package-lock.json`; cấu hình nằm ở `.prettierrc.json` và `.editorconfig`. Formatter áp dụng Java (kể cả test), HTML, CSS, JavaScript, `pom.xml` và cấu hình format. Không sửa SQL migration đã áp dụng vì có checksum. Node/npm chỉ phục vụ format; Maven WAR và Docker/Render vẫn build Java trực tiếp, không cài Node hay chạy test trong production.

## Kế thừa, audit và validation

Entity kế thừa BaseEntity; response có audit kế thừa BaseResponse. BaseRequest không cho client ghi các trường audit. ID dùng Long; `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version` do server quản lý. `version` dùng optimistic locking khi sửa dữ liệu; user ID hệ thống có thể null trong lúc bootstrap.

Schema lấy từ `VIETROUTE_DB_SCHEMA`, fallback `db.schema` trong database.properties, mặc định vietroute. Query runtime chỉ ghi tên bảng; Factory đặt lại search_path khi mượn pool, không public/$user fallback. Entity khai báo `@JdbcTable(name,primaryKey,indexes)`; validator dùng schema connection, không có tên schema trong annotation. DDL qua migration, không sinh bằng ORM. Không sửa V001–V003; adapter chỉ render V002/V003 lịch sử trong RAM và giữ checksum nguồn. Đọc [schema và quyền theo hành động](SCHEMA_VA_PHAN_QUYEN.md) trước khi đổi môi trường hoặc thêm CRUD/quyền.

PageRequest dùng page từ 0, size 1–100; repository chỉ nhận sort đã qua whitelist, không nối tên cột do client gửi vào SQL. PageResponse có tổng bản ghi/số trang và cờ trang trước/sau. API collection không tự trả entity.

Mã nghiệp vụ chỉ có chữ IN HOA và số, bắt đầu bằng chữ, dài 3–40, không dấu/khoảng trắng/ký tự đặc biệt. `@BusinessCode` kiểm tra định dạng; kết hợp `@NotBlank` nếu bắt buộc. Mã tự sinh dùng prefix đã validate và UUID, DB vẫn có UNIQUE để chốt tính duy nhất. Username dùng chữ/số, bắt đầu bằng chữ, dài 3–32. Tên hiển thị cho phép tiếng Việt; không áp quy tắc mã cho tên/địa chỉ hoặc mật khẩu.

Request có Jakarta Validation và Swagger @Schema tiếng Việt. Service kiểm tra tồn tại, trạng thái, quyền và quan hệ giữa các trường; validation DTO không thay thế kiểm tra nghiệp vụ. Comment/Javadoc tiếng Việt giải thích mục đích, transaction và các nhánh quan trọng.

## Auth và khởi tạo dữ liệu

JWT HS256 có issuer, audience, subject, jti, thời hạn và tokenVersion. Mỗi request có xác minh chữ ký/thời hạn và kiểm tra lại trạng thái/quyền/tokenVersion trong DB. Logout/đổi mật khẩu tăng tokenVersion, thu hồi token cũ trên mọi thiết bị. Không lưu JWT trong localStorage. Website dùng cookie HttpOnly, SameSite=Lax, Secure ở production; API ngoài trình duyệt có thể dùng Authorization Bearer.

Cookie auth dùng CSRF cho request thay đổi dữ liệu; Axios tự gửi X-XSRF-TOKEN sau khi lấy `/api/v1/auth/csrf`. Request dùng Authorization Bearer không phụ thuộc cookie được miễn CSRF. Đăng nhập sai có đếm và khóa tạm thời. Mật khẩu hash BCrypt; không log mật khẩu hoặc JWT.

`VIETROUTE_DB_INIT_ENABLED=true` bật migration và seed lúc app khởi động. Migration có checksum/history và khóa transaction PostgreSQL để chống hai instance khởi tạo đồng thời. Chỉ tạo admin nếu chưa tồn tại; không reset mật khẩu đã đổi. Username/password bootstrap, JWT secret lấy từ môi trường; password ban đầu của chủ dự án lưu ở `.env` local, không nằm trong Git. Seed vai trò và danh mục hệ thống; không tự tạo nhà xe/chuyến/giá giả.

Production cần secret JWT cố định, không sinh lại mỗi lần restart. Bootstrap được bật trên Render và tắt trong smoke web không DB; CI tích hợp dùng PostgreSQL tạm riêng, tuyệt đối không dùng Neon production.

## Swagger và giao diện

Swagger UI ở `/docs`; tài liệu OpenAPI JSON ở `/v3/api-docs`. @Operation mô tả endpoint, @Schema mô tả request/response/audit và ví dụ; hợp đồng API mới phải xuất hiện trong OpenAPI trước khi bàn giao. Thư viện UI được đóng gói bằng WebJars để không phụ thuộc CDN: Bootstrap, Axios và Swagger UI. Thymeleaf render layout chung, component thông báo, locale switch và form đăng nhập/đổi mật khẩu.

## Git và kiểm tra

Hạ tầng/common/auth/schema và tích hợp khó hiện do hieuv526034 thực hiện, commit dưới định danh Git đã cấu hình. Bảng phân công chỉ là người phụ trách công việc tương lai; không tạo commit rỗng hoặc gán tác giả cho người chưa thực hiện. Mỗi phần code có PR và bằng chứng nghiệm thu.

Nhánh theo mẫu `<người-phụ-trách>/<mã-việc-hoặc-chức-năng>` trong `PHAN_CONG.md`, không dùng tiền tố `codex/`. Người thực hiện commit trên nhánh được giao, tạo PR vào `main`, kiểm tra và review trước khi merge. Nhánh `main` chỉ nhận thay đổi qua PR. Dùng merge commit để giữ tác giả và ranh giới phần việc.

Test Java nằm trong src/test. Production dùng `-Pproduction` và chỉ copy src/main, không biên dịch/chạy/đóng gói test. `mvnw verify` local vẫn chạy các test hữu ích. CI kiểm tra container và auth bằng DB tạm; không seed hoặc sửa Neon khi build. Thay đổi schema chạy qua migration có phiên bản, không sửa SQL đã áp dụng.
