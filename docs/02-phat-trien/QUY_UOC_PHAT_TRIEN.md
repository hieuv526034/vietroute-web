# Quy ước nền tảng VietRoute

## AUTHDB04 — Auth gọn: 40 class, tám bảng (01/11/2026)

Theo xác nhận của chủ dự án, giới hạn 30–40 class tính **toàn bộ package auth**, ưu tiên cắt chức năng phụ. Hợp đồng này thay hợp đồng lifecycle/action/DTO AUTHDB02–AUTHDB03 cũ. Trạng thái nghiệm thu thật chỉ ghi tại [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

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

- Mỗi class dưới 1000 dòng. Không dùng `var` trong source production. Không record trong auth. UserDTO/RoleDTO/FunctionDTO nằm trực tiếp trong auth.dto theo xác nhận mới 01/11/2026; không đặt class hậu tố DTO vào dto.response. Request ở dto/request, Search chỉ ở dto/search và tên `*Search`, không `*SearchRequest`; không chia thêm package feature. Nếu có package nhiều từ, dùng dấu gạch dưới. Initializer ngang cấp service. Không tạo API/service riêng cho bảng nối, challenge hoặc history khi không có hành vi công khai cần thiết.
- Tám entity JPA đủ cột, Instant/audit/version; không hậu tố Entity, không schema cứng. V026 thêm identity kỹ thuật duy nhất cho bốn bảng nối/challenge để tránh bốn class composite ID; PK/FK ghép của DB vẫn giữ nguyên. Giữ V001–V025, dừng node cũ trước nâng cấp. CRUD dùng Spring Data SimpleJpaRepository qua repo cụ thể theo tên bảng; JDBC trong chính repo cho aggregate, batch và atomic SQL. Không thêm Spring Boot.
- Ba request tái sử dụng cho các thao tác trên cùng tài nguyên; các field bắt buộc theo endpoint được nêu tại @Operation và kiểm trong service. Field password WRITE_ONLY, không toString; response là whitelist, không entity/hash/tokenVersion. Swagger xuất path/query/header/body và @Schema, có phân trang DB. Không query/save mỗi phần tử; vòng lặp chỉ bind batch hoặc xử lý dữ liệu đã tải.
- Giữ đăng nhập/JWT 30 phút/BCrypt/CSRF/quota, khóa 5 lần sai trong 15 phút, đổi mật khẩu tránh hash hiện tại và hai hash trước, profile/avatar, CRUD User/Role/Function, gán role/function/endpoint, phạm vi nhà xe và lịch sử mutation nguyên tử. Đổi mật khẩu và logout thu hồi tokenVersion; sau đổi mật khẩu cần đăng nhập lại.
- Bỏ tự đăng ký, gửi/xác minh email tự phục vụ, quên/reset mật khẩu, auth mail outbox, recovery quản trị riêng và màn/API xem history. Không chuyển những chức năng này sang common để che số class. AccountChallenge giữ bảng/entity/repo để quản lý và vô hiệu hóa dữ liệu đã tồn tại; không có API phát challenge mới. AccessChange vẫn ghi cùng transaction và cleanup có giới hạn, không queue RAM.
- Tài khoản do người quản trị tạo được coi đã xác minh và phải đổi mật khẩu khởi tạo. Bootstrap admin cấu hình riêng; ba tài khoản mặc định trong JSON/env được tạo một lần, verified/active theo yêu cầu. Account đã có giữ password/hash/lock/trạng thái. Tài khoản cũ chưa xác minh không tự nhận quyền; người vận hành phải xử lý dữ liệu trước chuyển đổi vì luồng xác minh email đã cắt.

### Luồng auth và quyền endpoint

[Schema auth](SCHEMA_VA_PHAN_QUYEN.md) giữ một hợp đồng chính cho tám bảng, login/JWT/interceptor, service/transaction, scope, bootstrap và phát triển tiếp. [API base](API_BASE.md#api-auth-hiện-hành) ghi đủ 36 REST mapping, request/DTO/search và ví dụ gán. Không dùng lại instruction action/permission/lifecycle từ lịch sử Git.

## Phạm vi và thứ tự triển khai

Người mới theo [mục lục và lộ trình đọc](../README.md), bắt đầu từ [tổng quan hệ thống](../01-bat-dau/TONG_QUAN_HE_THONG.md). Package job/mail đã chia controller, DTO, service và phần thực thi theo trách nhiệm; root wiring tách DatabaseConfig, IntegrationConfig, TrafficConfig, MonitoringConfig. Theo [giám sát/vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md) khi thêm metrics hoặc handler. Không bật Kafka/Redis trong giai đoạn hiện tại; quota memory có phạm vi từng node.

Lượt nền tảng: common → auth/JWT/bootstrap → danh mục mẫu → layout/Swagger → kiểm thử và CI. Sau đó mới triển khai lần lượt các nghiệp vụ theo `PHAN_CONG.md`. Không coi các module đặt vé, tuyến/chuyến hoặc Maps là đã hoàn thành khi mới có package.

Giữ Spring MVC 6, Thymeleaf, PostgreSQL và WAR/Tomcat 10.1. Auth CRUD dùng Spring Data JPA/Hibernate; JDBC phục vụ batch/query phức tạp và module nghiệp vụ hiện có. Spring Security xử lý bảo mật; không dùng Spring Boot.

Đọc [nền tảng mở rộng](NEN_TANG_MO_RONG.md) trước khi thêm endpoint được cấp quyền, job hoặc email. Quyền endpoint kiểm ở EndpointAccess theo DB; service kiểm principal đã duyệt/nghiệp vụ và transaction; scope nhà xe/owner kiểm riêng ở SQL/service khi module có dữ liệu. Email đi qua outbox cùng transaction nghiệp vụ; worker chỉ nhận việc khi có slot, không tự tạo thread cho từng request. Giữ stack theo quy ước hiện tại, không tự đổi major để lấy nhãn mới nhất.

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
    config/        SecurityConfig/SecurityInitializer
    controller/    UserPageController và controller/api/{UserAPI,RoleAPI,FunctionAPI}
    dto/request/   Class request theo nhóm chức năng, có validation/Schema
    dto/           UserDTO, RoleDTO, FunctionDTO: dữ liệu tài nguyên/projection whitelist
    dto/search/    UserSearch, RoleSearch, FunctionSearch: query/phân trang
    entity/        Tám class JPA theo bảng, bắt đầu User/Role/Function
    repo/          JPA CRUD và SQL mapping/batch/aggregate theo tên bảng
    service/       UserService, RoleService, FunctionService, JwtService
    initializer/   AuthInitializer: khởi tạo theo JSON/env khi bật cờ
    security/      UserPrincipal, EndpointAccess, JwtAuthenticationFilter, AuthCookies
  business/
    catalog/       controller, dto/request, dto/response, entity, repository, service
    carrier/       Nhà xe, phương tiện, tài xế, scope và quan hệ account
    location/, route/, trip/  Địa điểm, tuyến, điểm dừng và chuyến
    booking/       Đặt/xác nhận/hủy chỗ theo chặng
    tracking/      Chia sẻ vị trí riêng tư; Maps theo cấu hình provider
```

Controller chỉ nhận/validate input và gọi service. Service giữ transaction và quy tắc nghiệp vụ; repository auth dùng Spring Data JPA CRUD và JDBC cho batch/query phức tạp; repository nghiệp vụ JDBC giữ PreparedStatement/ResultSet. Entity không trả thẳng ra API; response thực hiện mapping rõ ràng, không kéo password hash hoặc token version ra client. Không tạo generic CRUD bỏ qua quyền và tenant.

API auth/nghiệp vụ kế thừa `BaseController`, dùng `execute(...)` có try/catch chung và `ApiExceptionMapper`. Advice dùng cùng mapper cho lỗi trước controller. Đọc [luồng xử lý và cách học base](LUONG_XU_LY_BASE.md) trước khi thêm module.

### Quy hoạch auth DTO theo chức năng

Cấu trúc hiện hành nằm trong [AUTHDB04](#authdb04--auth-gọn-40-class-tám-bảng-01112026). Ba DTO nằm trực tiếp ở auth.dto; dto/request và dto/search mỗi nhóm ba class theo User/Role/Function. Thư mục dto/response đã bỏ theo xác nhận mới 01/11/2026. Source cũ đã gộp phải clean build/restart, không để bytecode cũ trong WAR.

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

[Schema auth](SCHEMA_VA_PHAN_QUYEN.md#khởi-tạo-và-bảo-vệ-bootstrap) mô tả riêng DB_INIT/ACCESS_SYNC/DEFAULT_ACCOUNTS/BUSINESS_INIT, hash/lock/idempotence và phần đã cắt. Không tự gán quyền bằng tên role, không đổi password cũ từ seed. `.env` gốc được chủ dự án cho phép commit/push tới repo hiện tại; không sao chép giá trị vào docs/log/chat/WAR/image. Test không đọc `.env` và không thử ghi Neon.

## Swagger và giao diện

Swagger UI ở `/docs`; tài liệu OpenAPI JSON ở `/v3/api-docs`. @Operation mô tả endpoint, @Schema mô tả request/response/audit và ví dụ; hợp đồng API mới phải xuất hiện trong OpenAPI trước khi bàn giao. Thư viện UI được đóng gói bằng WebJars để không phụ thuộc CDN: Bootstrap, Axios và Swagger UI. Thymeleaf render layout chung, component thông báo, locale switch và form đăng nhập/đổi mật khẩu.

## Hướng hỗ trợ nhiều hệ DB — DB01

Quyết định 31/10/2026: giữ Spring MVC/JDBC và hợp đồng service/API; chuẩn bị Oracle qua adapter theo hệ DB, auth CRUD đã chuyển sang JPA/Hibernate theo AUTHDB03; không thay SQL khác hệ bằng chuỗi replace. Hiện chỉ **PostgreSQL** được hỗ trợ và nghiệm thu. Oracle chưa có driver trong WAR, migration, SQL repository hoặc môi trường kiểm thử. URL Oracle bị từ chối trước tạo pool với lỗi nêu phần thiếu, không in URL/user/password. Một lần chạy ứng dụng dùng một hệ DB; chưa có routing nhiều datasource hoặc đồng bộ PostgreSQL↔Oracle.

`common.jdbc.dialect.JdbcDialect` là ranh giới đầu tiên: chọn adapter từ URL, tên driver, thuộc tính timeout/application name và chọn schema. `PostgreSqlDialect` phục vụ cả pool nghiệp vụ và pool quan sát, giữ nguyên giới hạn/timeout, không mở kết nối khi tạo settings. `JdbcSchema` nhận adapter; PostgreSQL vẫn transaction trước LOCAL search_path với pg_temp cuối, rollback/commit của caller, không fallback public. Không thêm cờ Oracle cho phép đi tiếp khi repository vẫn là PostgreSQL. Adapter này **chưa phải bộ dịch SQL**.

Các đợt tiếp theo, theo thứ tự phụ thuộc:

| Đợt | Phạm vi cần triển khai/kiểm |
|---|---|
| DB02 | Chốt phiên bản Oracle/edition và môi trường test riêng; JDBC driver tương thích Java 17/21, schema/user/quyền DDL, pool/timeout và metadata. Không nhận credential trong Git/chat. |
| DB03 | Migration riêng theo vendor và history/checksum/khóa khởi tạo. Giữ V001–V023 PostgreSQL byte-for-byte; không chạy chúng trên Oracle hoặc sửa history để giả đã chuyển DB. DDL/seed/audit/sequence/Instant/NUMERIC phải có quy tắc tương đương. |
| DB04 | Tách SQL repository theo module, bắt đầu catalog/location, tiếp auth/access/scope, rồi trip/booking/jobs/monitoring. Dùng interface/adapter ở nơi SQL khác; service giữ transaction/quyền, không rải if Oracle vào từng phương thức nghiệp vụ. |
| DB05 | Chạy cùng ca hợp đồng trên từng DB thật cô lập: quyền trực tiếp/hai context/token revocation, CAS, rollback history, race giữ/trả chỗ, lock order, paging/count/literal search/vi-en và kế hoạch query/index. Chỉ bật Oracle sau khi ca này đạt. |

Điểm phụ thuộc đã thấy trong source: LOCAL search_path/pg_catalog; advisory lock; ON CONFLICT; RETURNING/CTE ghi theo tập; mảng JDBC/array_agg/ANY, JSONB, ép kiểu `::`, FILTER/LATERAL; LIMIT/OFFSET; SKIP LOCKED; hàm PL/pgSQL activity; metadata/SQLState/index GIN pg_trgm. Không hạ regression số query của PERF02/PERF03 hoặc giữ khóa bằng for query/save để có bản Oracle dễ viết. Index tìm chứa từ khóa phải chọn theo plan mỗi DB, không giả GIN có bản tương đương tự động.

Khác biệt chuỗi rỗng/null, boolean, timestamp/timezone, khóa và DDL cần kiểm theo phiên bản Oracle thực tế. Ví dụ [Oracle 19c coi chuỗi dài 0 là null](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Nulls.html), nên các cột NOT NULL/default chuỗi rỗng hiện hành phải thiết kế lại có chủ đích. Thuộc tính timeout ở adapter PostgreSQL theo [pgJDBC](https://jdbc.postgresql.org/documentation/use/), không chép tên/đơn vị sang driver Oracle. Chưa đổi database Render/Neon, chưa migration dữ liệu thật và chưa chứng nhận Oracle qua mock/H2.

## Git và kiểm tra

Ảnh công khai dùng adapter Cloudinary và lõi dùng chung `common/media`, public controller mỏng tại `common/media/controller`; không lưu file bền vững trên Render hoặc đưa secret vào UI. Chạy IDE bằng profile local và cấu hình Run chia sẻ, production vẫn WAR. Đọc [Cloudinary và IntelliJ](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md) trước khi tích hợp mediaId hoặc đổi cấu hình chạy.

Hạ tầng/common/auth/schema và tích hợp khó hiện do hieuv526034 thực hiện, commit dưới định danh Git đã cấu hình. Bảng phân công chỉ là người phụ trách công việc tương lai; không tạo commit rỗng hoặc gán tác giả cho người chưa thực hiện. Mỗi phần code có PR và bằng chứng nghiệm thu.

Nhánh theo mẫu `<người-phụ-trách>/<mã-việc-hoặc-chức-năng>` từ `dev` trong `PHAN_CONG.md`, không dùng tiền tố `codex/`. Người thực hiện commit trên nhánh được giao, tạo PR vào `dev`, kiểm tra và review trước khi merge. Chỉ mở/merge PR `dev` → `main` và chạy production CI/deploy khi chủ dự án yêu cầu phát hành; quy trình hiện hành ở [CONTRIBUTING](../../CONTRIBUTING.md#tích-hợp-dev-và-phát-hành-main-từ-31102026). Không commit source trực tiếp lên hai nhánh chung. Dùng merge commit để giữ tác giả và ranh giới phần việc.

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

Tìm kiếm chứa từ khóa và tổng hợp báo cáo vẫn có thể đọc nhiều dòng bên trong một statement. Cần EXPLAIN/metrics trên phân bố dữ liệu đại diện trước khi thêm index hoặc đổi SQL; kiểm số query không chứng minh mọi query nhanh trên Neon. Không load test, seed hoặc chạy mutation production để đo. Lỗi tối ưu phải được kiểm rollback/CAS/lock order/guard/scope bằng PostgreSQL cô lập; giữ regression kích thước tập tăng mà số lượt execute không tăng. Kết quả và PR ở [tiến độ PERF02](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md#perf02--đã-có-trên-main-rà-query-trong-vòng-lặp-và-ghi-theo-tập).

## Index tìm kiếm và báo cáo — PERF03

V023 bổ sung năm index theo EXPLAIN ANALYZE/BUFFERS với SQL/bind lấy từ DAO thật; giữ V001–V022 nguyên vẹn. Hai B-tree `idx_trips_report_departure` (departure_at,carrier_id,id) và `idx_trips_report_carrier_date` (carrier_id,departure_at,id), INCLUDE scope_id/status, phục vụ báo cáo không lọc status. Không thay index lịch chồng hoặc guard scope. Ba GIN `idx_carriers_search_contact` (code/name/phone), `idx_carriers_search_name` (code/name cho picker báo cáo), `idx_catalog_search_text` (code/name_vi/name_en) dùng biểu thức lower/nối text trên trường NOT NULL, tương đương concat_ws cũ nhưng immutable.

Tìm kiếm dùng LIKE có escape để %, _, ! và backslash vẫn là ký tự nguyên văn; không chuyển thành tìm gần đúng. [pg_trgm PostgreSQL](https://www.postgresql.org/docs/17/pgtrgm.html) hỗ trợ LIKE chứa từ khóa; [Neon có pg_trgm](https://neon.com/docs/extensions/pg_trgm). Từ khóa rất ngắn hoặc khớp nhiều dòng có thể vẫn dùng scan; không ép planner/tắt index khác. Thống kê biểu thức đặt 1000 rồi ANALYZE vì mẫu mặc định trong fixture ước lượng từ khóa hiếm 202 thay vì 20 dòng, làm page chọn index ORDER BY và lọc gần hết bảng. Đây là thống kê từng index, không sửa cấu hình planner toàn DB.

Initializer vẫn một transaction/advisory lock, chỉ chạy khi bật rõ ràng. V023 cài extension mới vào schema ứng dụng, hoặc tìm namespace opclass của extension có sẵn và quote định danh, không di chuyển extension dùng chung/thêm public vào search_path. DB role cần quyền tạo index trên bảng và CREATE database nếu phải cài extension; lỗi dừng startup/rollback, không âm thầm bỏ index. CREATE INDEX thường trong transaction có thể chặn ghi: triển khai có cửa sổ dừng/drain node; dữ liệu lớn cần kế hoạch build index riêng trước khi triển khai, không tuyên bố zero downtime. GIN tăng dung lượng/chi phí ghi và cần autovacuum/ANALYZE; fixture bulk dọn pending list trước đo, không thêm lệnh dọn đó vào runtime. Test namespace ngoài app giữ mapping/search_path; plan và tải fixture không là số đo Neon. [Bằng chứng PERF03](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md#perf03--đã-có-trên-main-metadata-theo-nhu-cầu-index-tải-cô-lập-và-review-độc-lập).
