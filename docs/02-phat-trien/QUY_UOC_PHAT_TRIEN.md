# Quy ước nền tảng VietRoute

## Phạm vi và thứ tự triển khai

Người mới theo [mục lục và lộ trình đọc](../README.md), bắt đầu từ [tổng quan hệ thống](../01-bat-dau/TONG_QUAN_HE_THONG.md). Package job/mail đã chia controller, DTO, service và phần thực thi theo trách nhiệm; root wiring tách DatabaseConfig, IntegrationConfig, TrafficConfig, MonitoringConfig. Theo [giám sát/vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md) khi thêm metrics hoặc handler. Không bật Kafka/Redis trong giai đoạn hiện tại; quota memory có phạm vi từng node.

Lượt nền tảng: common → auth/JWT/bootstrap → danh mục mẫu → layout/Swagger → kiểm thử và CI. Sau đó mới triển khai lần lượt các nghiệp vụ theo `PHAN_CONG.md`. Không coi các module đặt vé, tuyến/chuyến hoặc Maps là đã hoàn thành khi mới có package.

Giữ Spring MVC 6, Thymeleaf, JDBC thuần, PostgreSQL và WAR/Tomcat 10.1. Spring Security xử lý bảo mật; Hibernate Validator chỉ dùng Bean Validation, không sử dụng Hibernate ORM/JPA. Không chuyển sang Spring Boot.

Đọc [nền tảng mở rộng](NEN_TANG_MO_RONG.md) trước khi thêm permission, job hoặc email. Quyền chức năng kiểm ở service bằng authority; scope nhà xe/owner kiểm riêng ở SQL/service khi module có dữ liệu. Email đi qua outbox cùng transaction nghiệp vụ; worker chỉ nhận việc khi có slot, không tự tạo thread cho từng request. Giữ stack theo quy ước hiện tại, không tự đổi major để lấy nhãn mới nhất.

## Cấu trúc module

```text
vn.vietroute
  common/
    base/          BaseController, BaseEntity, BaseDTO, BaseRequest, BaseResponse, PageRequest/PageResponse
    constant/      ApplicationConstants, ErrorCode, SuccessCode: key, route, regex và mã ổn định
    exception/     BusinessException, xử lý exception và trả lỗi thống nhất
    validation/    Annotation và quy tắc dùng chung
    util/          Tạo mã, chuẩn hóa dữ liệu dùng chung
    jdbc/          Connection, transaction và repository JDBC cơ sở
    config/        Cấu hình root/web, locale, JSON, bootstrap và OpenAPI
    web/           controller, response, filter, locale, advice, openapi, routing
    monitoring/    controller, dto/response, service: trạng thái DB/pool/worker
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

Key Environment dùng `ApplicationConstants.Env` qua `ConfigValues`; URL/matcher dùng `ApplicationConstants.Routes`; regex dùng `ApplicationConstants.Regex`. Header chuẩn dùng `HttpHeaders`, header/cookie riêng dùng `ApplicationConstants.Http`. Không gõ lại các literal này ở Settings/controller/filter. `WebRoutePolicy` gom chính sách public/JWT/quota, không tự cấp quyền chỉ vì có route mới. Đọc [quy hoạch constants và web](CONSTANTS_VA_CAU_TRUC_WEB.md) trước khi thêm key hoặc endpoint.

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

IntelliJ dùng Project scheme tại `.idea/codeStyles/Project.xml` cùng EditorConfig; chỉ chia sẻ code style, không chia sẻ workspace/env cá nhân. Đọc [thiết lập IntelliJ và nút Run Format](../01-bat-dau/FORMAT_INTELLIJ.md). Native formatter hỗ trợ lúc soạn thảo; trước commit/bàn giao chạy `npm run format` và `npm run format:check` để có đầu ra chung, kể cả khi người hỗ trợ tự sửa code. Format không tự bổ sung comment nghiệp vụ hoặc sửa tên DTO/Instant.

## Kế thừa, audit và validation

Entity kế thừa BaseEntity; response có audit kế thừa BaseResponse. BaseRequest không cho client ghi các trường audit. ID dùng Long; `createdAt`, `updatedAt`, `createdBy`, `updatedBy`, `version` do server quản lý. `version` dùng optimistic locking khi sửa dữ liệu. Actor hệ thống là `ApplicationConstants.SystemActor.ID` (0, username system), không dùng NULL; luồng người dùng ghi ID principal. Đọc [audit và system](AUDIT_VA_SYSTEM.md) trước khi thêm seed/job/repository.

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

Ảnh công khai dùng adapter Cloudinary và lõi dùng chung `common/media`, public controller mỏng tại `common/media/controller`; không lưu file bền vững trên Render hoặc đưa secret vào UI. Chạy IDE bằng profile local và cấu hình Run chia sẻ, production vẫn WAR. Đọc [Cloudinary và IntelliJ](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md) trước khi tích hợp mediaId hoặc đổi cấu hình chạy.

Hạ tầng/common/auth/schema và tích hợp khó hiện do hieuv526034 thực hiện, commit dưới định danh Git đã cấu hình. Bảng phân công chỉ là người phụ trách công việc tương lai; không tạo commit rỗng hoặc gán tác giả cho người chưa thực hiện. Mỗi phần code có PR và bằng chứng nghiệm thu.

Nhánh theo mẫu `<người-phụ-trách>/<mã-việc-hoặc-chức-năng>` trong `PHAN_CONG.md`, không dùng tiền tố `codex/`. Người thực hiện commit trên nhánh được giao, tạo PR vào `main`, kiểm tra và review trước khi merge. Nhánh `main` chỉ nhận thay đổi qua PR. Dùng merge commit để giữ tác giả và ranh giới phần việc.

Tiêu đề và nội dung commit mới, merge commit và PR viết bằng tiếng Việt có dấu. Nêu rõ phần thay đổi, lý do và kết quả kiểm tra thực tế; giữ nguyên định danh kỹ thuật. Mẫu và hướng dẫn nằm trong [quy ước đóng góp](../../CONTRIBUTING.md#viết-commit-và-pr-bằng-tiếng-việt).

Test Java nằm trong src/test. Production dùng `-Pproduction` và chỉ copy src/main, không biên dịch/chạy/đóng gói test. `mvnw verify` local vẫn chạy các test hữu ích. CI kiểm tra container và auth bằng DB tạm; không seed hoặc sửa Neon khi build. Thay đổi schema chạy qua migration có phiên bản, không sửa SQL đã áp dụng.

## Quy ước tab và modal

Trang nhiều nhóm chức năng tách tab; thêm/sửa/gán và upload dùng modal theo [component tab/modal](../03-giao-dien/QUY_UOC_UI.md). Dùng workspace.js và HTTP/CSRF/lỗi chung, giữ focus/bàn phím/ARIA, phản hồi lỗi ngay trong modal và bản nháp khi 409. Không đóng trong lúc gửi request hoặc tự retry mutation.

## Rà query và ghi theo tập — PERF02

Rà ngày 27/10/2026 qua các vòng for/while/stream và lời gọi JDBC trong 34 repository, 40 service, startup và worker. Đọc sâu luồng gán role/quyền, xóa tài khoản, chuyến/booking/chỗ, list/report và các vòng có SQL. Vòng đọc ResultSet, kiểm dữ liệu hoặc bind `addBatch` không tự tạo N+1; cần đếm `execute*` thực tế và các lời gọi DAO/service bên trong, không chỉ `prepareStatement`.

| Luồng | Chi phí trước | Đã sửa và hợp đồng giữ lại |
|---|---|---|
| Kiểm mapping role/quyền | SELECT từng mã | Một SELECT với mảng tham số cho cả tập; vẫn từ chối mã thiếu/inactive/tombstone. |
| Trần ủy quyền khi gán nhiều role | Khoảng 4–5 query mỗi role để lấy marker/target/permission | Một snapshot tập role; giữ native rỗng là deny, fallback legacy đúng nguồn, kiểm cả permission tắt và target cụ thể theo actor. |
| Lịch sử nhiều membership khi gán role/xóa user | Mỗi record INSERT và hai bước cleanup | Một JDBC batch + một lần cleanup cho tập membership của cùng transaction; lỗi một entry rollback mutation, không queue ghi sau. Các dòng vẫn được INSERT ở PostgreSQL, batch không biến N dòng thành O(1) công việc server. |
| Hủy chuyến | Một SELECT tải DTO/PII mọi đơn rồi khoảng 2 lượt/đơn PENDING, 4 lượt/đơn CONFIRMED | Năm lượt execute JDBC khi có CONFIRMED: khóa booking theo ID, cộng nhu cầu trả theo đoạn, khóa đoạn theo thứ tự, cập nhật đoạn, CTE CAS + history cả tập. Giữ tiền/hoàn, actor, version và số lần tăng version đoạn như trước. Không nạp DTO của toàn bộ đơn lên heap. |
| Tài xế rời/đến điểm | Đọc toàn bộ đơn active rồi UPDATE/INSERT từng đơn phù hợp | Một CTE khóa/lọc đúng tập, UPDATE và INSERT history; so đủ số dòng, thiếu history bất kỳ rollback toàn bộ. Đến điểm trả vẫn giữ chỗ lịch sử. |
| Xác nhận/hủy một đơn theo chặng | SELECT khóa đoạn rồi batch UPDATE từng đoạn | Giữ kiểm đủ đoạn/tồn chỗ và khóa tăng dần; một range UPDATE sau khóa, không execute từng đoạn. |

Các list catalog/nhà xe/xe/tài xế/tuyến/chuyến/booking dùng count + page ở SQL, mapping ResultSet không gọi DAO theo từng dòng. Báo cáo tổng hợp một statement với CTE/cohort, vòng enum chỉ dựng cột/map response. Validate địa điểm dùng mảng; điểm tuyến/snapshot chuyến/mapping quyền đã dùng batch và giới hạn số phần tử. Giữ các refresh guard DB ở HTTP/service để thu hồi quyền tại check tiếp theo; không cache authority để giảm query.

Còn các vòng có DB có chủ đích: bootstrap bốn role cố định, migration/kiểm metadata chỉ lúc startup enabled; DEMO dùng bộ JSON tĩnh có parent/identity/idempotency/collision, chỉ gọi opt-in, không nạp khi chuyển trang; worker tối đa 32 việc/lượt, mỗi claim/ACK có transaction ngắn riêng và gửi mạng ngoài transaction. Không gom các job khác nhau vào một transaction lớn chỉ để bỏ for.

Tìm kiếm chứa từ khóa và tổng hợp báo cáo vẫn có thể đọc nhiều dòng bên trong một statement. Cần EXPLAIN/metrics trên phân bố dữ liệu đại diện trước khi thêm index hoặc đổi SQL; kiểm số query không chứng minh mọi query nhanh trên Neon. Không load test, seed hoặc chạy mutation production để đo. Lỗi tối ưu phải được kiểm rollback/CAS/lock order/guard/scope bằng PostgreSQL cô lập; giữ regression kích thước tập tăng mà số lượt execute không tăng. Kết quả và PR ở [tiến độ PERF02](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md#perf02--đang-kiểm-ci-rà-query-trong-vòng-lặp-và-ghi-theo-tập).

