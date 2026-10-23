# Cấu hình schema và quyền theo hành động

Cập nhật 22/10/2026, phụ trách hieuv526034. Quy ước này thay phần schema cố định và quyền WRITE gộp trong tài liệu nền tảng trước đó.

## Đổi schema ở một nơi cấu hình

Biến `VIETROUTE_DB_SCHEMA` là nơi cấu hình schema của ứng dụng. Mặc định là `vietroute`, database Neon vẫn là `neondb`. Ví dụ khi tạo một môi trường/DB mới:

```properties
VIETROUTE_DB_SCHEMA=project_2026
```

WAR không tự đọc `.env`; launcher local chủ động đọc file riêng theo hướng dẫn chạy IntelliJ; đặt biến trong Environment Render hoặc cấu hình IDE/Tomcat. Giá trị môi trường ưu tiên hơn `db.schema` trong `src/main/resources/database.properties`. Render Blueprint có key này; service tạo thủ công phải đặt trong Dashboard. Không cần sửa/rebuild Java để chọn schema khác trên cùng image.

Tên schema chỉ nhận chữ thường ASCII/số/underscore, bắt đầu bằng chữ, dài tối đa 63, không dùng public, information_schema hoặc tiền tố pg_. Giá trị từ HTTP, role, tenant hoặc request không được dùng làm schema. `JdbcSchema` whitelist trước khi quote identifier; các giá trị SQL nghiệp vụ vẫn bind bằng PreparedStatement. Một schema triển khai không phải cơ chế tenant theo từng request.

Luồng cấu hình:

1. DatabaseSettings đọc key một lần khi tạo root context, kiểm tra tên; JdbcSchema là bean immutable, không có tên schema static dùng chung.
2. JdbcConnectionFactory mượn connection rồi gọi `pg_catalog.set_config('search_path', ?, false)` với path `"<schema>", pg_temp`. Mỗi lần mượn đều đặt lại, kể cả URL có currentSchema khác hoặc borrower trước đã đổi trạng thái session.
3. Repository/seed/job query dùng tên bảng không gắn schema. Không có public/$user fallback; pg_temp đứng cuối để bảng tạm không che bảng ứng dụng. PostgreSQL ưu tiên pg_catalog ngầm trước path cho hàm/kiểu hệ thống. Người không tin cậy không được có quyền CREATE trên schema ứng dụng; xem [PostgreSQL schema/search_path](https://www.postgresql.org/docs/17/ddl-schemas.html).
4. `@JdbcTable(name, primaryKey, indexes)` chỉ mô tả table/PK/index; JdbcSchemaValidator dùng schema thực tế trên connection sau bootstrap. Entity không phải sửa annotation khi đổi môi trường.
5. Bootstrap tạo schema/history trong transaction startup có advisory lock. DB mới tạo tất cả bảng/index/seed ở schema cấu hình. Legacy V001/history trong public được chuyển vào schema cấu hình rồi tiếp tục migration, giữ dữ liệu/hash/ID/FK/sequence.

## Migration đã áp dụng và dữ liệu đang có

V001/V002/V003 giữ **nguyên byte và checksum**. V001 vốn không gắn schema nên dùng search_path. Hai migration lịch sử V002/V003 từng ghi cố định tên cũ; LegacyMigrationSql chỉ ánh xạ identifier cũ sang tên đã validate/quote trong bản SQL thực thi ở RAM. Adapter whitelist đúng hai filename, không replace mọi migration hoặc query nghiệp vụ. History lưu SHA-256 của file nguồn gốc trước khi render. Không sửa file SQL đã áp dụng để đổi cấu hình, không tính checksum của SQL đã render.

Migration mới dùng tên bảng không gắn schema dưới path của initializer. Tên schema hiện tại chỉ nằm ở cấu hình; tên cũ còn trong migration lịch sử và adapter tương thích có chủ đích. Tên bảng `vietroute_schema_history` không phải tên schema và được giữ để theo dõi lịch sử cũ.

**Đổi cấu hình không tự di chuyển dữ liệu của schema đang chạy.** Startup kiểm history trong DB: nếu history ở schema khác hoặc có nhiều history, nó dừng trước CREATE/seed. Nhờ vậy gõ sai key không tạo admin/bảng rỗng song song. Một database triển khai có một history của ứng dụng; chưa hỗ trợ nhiều tenant/schema ứng dụng độc lập trong cùng DB.

Để đổi tên schema đang chứa dữ liệu:

1. Backup và kiểm khả năng phục hồi; dừng/drain tất cả web/worker dùng DB. Không để bản cũ và mới cùng chạy trong lúc đổi tên.
2. Người vận hành có quyền sở hữu schema và CREATE trên DB thực hiện `ALTER SCHEMA "old_name" RENAME TO "new_name";` trong cửa sổ bảo trì. Đây là thao tác lên DB, không phải sửa source. Quyền cần thiết xem [PostgreSQL ALTER SCHEMA](https://www.postgresql.org/docs/17/sql-alterschema.html).
3. Đặt `VIETROUTE_DB_SCHEMA=new_name` ở mọi web/worker, khởi động lại rồi kiểm login/dữ liệu/job/history. Không sửa checksum, không chạy lại seed để ghi đè mật khẩu.

Đợt này chỉ kiểm rename trên DB container CI tạm; không rename hay seed Neon production. Nếu không cần đổi tên dữ liệu hiện tại, giữ key `vietroute` và dùng cơ chế cấu hình cho các môi trường sau.

## Quyền cơ bản đã có

`PermissionAction` chuẩn hóa READ, CREATE, UPDATE, DELETE, ASSIGN, RETRY, SEND. Mỗi permission gắn **resource + action**, không có một quyền READ/WRITE/DELETE toàn hệ thống. Response registry `/api/v1/admin/access/permissions` có `code`, `resource`, `actions`, `messageKey`, `name` để UI dựng ma trận quyền. Key của permission/action có đủ vi/en.

| Resource | Permission | Hành động/endpoint được bảo vệ |
|---|---|---|
| CATALOG | CATALOG_READ | GET /api/v1/admin/catalogs/{id}, đọc quản trị cả bản đã xóa mềm |
| CATALOG | CATALOG_CREATE | POST /api/v1/admin/catalogs |
| CATALOG | CATALOG_UPDATE | PUT /api/v1/admin/catalogs/{id} |
| CATALOG | CATALOG_DELETE | DELETE /api/v1/admin/catalogs/{id}, xóa mềm |
| ACCESS | ACCESS_READ | Xem registry quyền và cấu hình role |
| ACCESS | ACCESS_ASSIGN | Gán tập quyền role; gán role user còn bắt buộc role ADMIN |
| JOB | JOB_READ / JOB_RETRY | Xem job / chạy lại job DEAD |
| MAIL | MAIL_SEND | Xếp email vào outbox, quota và idempotency vẫn áp dụng |
| SYSTEM | SYSTEM_READ | Xem readiness/pool/worker/queue |

API công khai `/api/v1/catalogs/{type}` vẫn chỉ trả mục active, không yêu cầu CATALOG_READ. Đây là hợp đồng tra cứu công khai; quyền đọc quản trị tách riêng để xem audit/bản xóa mềm. Chưa có API list quản trị bao gồm mọi trạng thái; GET theo ID là ví dụ read được bảo vệ.

Quyền không tự bao hàm nhau: CREATE không có READ/UPDATE/DELETE; UPDATE không có CREATE/DELETE; DELETE không có ASSIGN. Gán quyền không có nghĩa được phép bỏ qua phạm vi nhà xe/owner. Module mới khai báo permission cùng resource/hành động cần dùng, rồi kiểm authority tại service và scope đối tượng trong SQL/service.

Mã cũ vẫn giữ để role đang cấu hình không bị mất khả năng làm việc:

- CATALOG_WRITE tương thích CREATE + UPDATE, không thêm READ/DELETE/ASSIGN. API tạo/sửa chấp nhận mã cụ thể hoặc WRITE cũ; API xóa bắt buộc DELETE.
- ACCESS_WRITE tương thích ACCESS_ASSIGN, giữ các giới hạn gán mới. Code/hướng dẫn mới ưu tiên ACCESS_ASSIGN.

Seed thêm registry mới và cấp đủ quyền cho role ADMIN cứu hộ. Các role thường đã chỉnh không tự nhận quyền mới sau restart; quản trị phải gán tường minh. Không xóa mapping WRITE đang có hoặc tự mở rộng WRITE thành toàn quyền.

Gán mapping role dùng version/transaction và có hiệu lực từ request kế tiếp vì JWT filter đọc quyền từ DB. ADMIN cố định không sửa mapping qua API. Người được ủy quyền có ACCESS_ASSIGN nhưng không thuộc ADMIN chỉ được gán các permission chính họ đang có, không gán vượt tập quyền để nâng quyền. Gán role cho user bị giới hạn ADMIN và cần ACCESS_ASSIGN/ACCESS_WRITE; không tự đổi role, không bỏ admin hoạt động cuối, tăng tokenVersion thu hồi JWT cũ. Kiểm quyền service vẫn là bắt buộc khi gọi ngoài controller qua bean.

## Mẫu CRUD và audit/version

CatalogUpdateRequest chỉ có nameVi/nameEn/sortOrder/version; không nhận type/code/audit/active. Version bắt buộc, SQL UPDATE có `WHERE id=? AND version=? AND active=true`, tăng version và cập nhật audit trong một transaction. Version cũ hoặc bản đã xóa trả COM008/409; ID không tồn tại trả COM004/404.

DELETE nhận body `{ "version": 1 }`, đổi active=false và giữ record/FK/audit. Không xóa vật lý hoặc giải phóng mã UNIQUE. Bản xóa biến mất khỏi tra cứu công khai; CATALOG_READ vẫn xem được audit qua ID. UPDATE không tự phục hồi bản đã xóa; muốn restore phải thiết kế quyền/luồng riêng. Controller kế thừa BaseController, có @Operation; request/response có @Schema và validate trước service.

## Kiểm chứng và cách đọc code

Đọc theo thứ tự DatabaseSettings → JdbcSchema → JdbcConnectionFactory → SchemaBootstrap/LegacyMigrationSql → JdbcSchemaValidator → repository. Sau đó đọc PermissionAction/Permission → AccessService và CatalogService → CatalogRepository để hiểu authority/transaction/version.

Kiểm thử local không dùng DB production: kiểm tên schema lỗi/injection, path mỗi lần mượn, đóng connection khi đặt path lỗi, adapter migration, locale registry/action, quyền tạo không cho sửa/xóa và quyền gán không nâng quyền. CI PostgreSQL tạm kiểm DB mới schema tùy chọn (kể cả keyword), URL currentSchema sai, bảng public cùng tên, legacy public nâng cấp vào schema khác, giữ checksum/hash/dữ liệu sau rename offline, và chặn schema nhầm không tạo bảng mới. CI còn kiểm CRUD quyền cụ thể, WRITE tương thích, version cũ, xóa mềm, audit và JWT cũ được cập nhật quyền. Production Docker vẫn bỏ test compile/chạy/đóng gói test.

## Danh bạ UI/API tự đồng bộ — FOUNDATION03

Role chính là **nhóm quyền**: user nhận một hoặc nhiều role, role chứa các permission RESOURCE_ACTION. Chưa cần thêm bảng nhóm quyền trùng role. Nhóm phòng ban/đội nhà xe sẽ là khái niệm nghiệp vụ khác, thiết kế sau cùng scope/tenant; không đồng nhất với ADMIN/OPERATOR hay menu.

`AccessTargets.discover` thu thập method/template path/handler từ `RequestMappingHandlerMapping` thật của Spring. Controller MVC phân loại MENU (giao diện), RestController phân loại ENDPOINT (API/kỹ thuật); mô tả API lấy từ `@Operation`. Không cần nhập lại link khi thêm endpoint. Luôn khai báo method HTTP cụ thể bằng GetMapping/PostMapping/...; khai báo route constant không cấp quyền.

Luồng startup: root `DatabaseInitializer` áp dụng V010 → servlet context tạo xong mapping → `ContextRefreshedEvent` gọi `AccessResourceSynchronizer` → service transaction → advisory lock/upsert `auth_access_resources`. `VIETROUTE_ACCESS_SYNC_ENABLED` mặc định theo DB_INIT_ENABLED nếu không khai báo; có thể tắt riêng, hoặc bật sau khi DBA đã áp dụng V010. Render Blueprint bật true. Thiếu schema/DB khi bật sync sẽ làm startup báo lỗi, không báo đồng bộ thành công giả. Maven/unit test tắt cả init/sync và không đọc Neon.

Danh bạ có PK `(kind,target)`, audit system=0, TIMESTAMPTZ và index audit. Startup chỉ ghi metadata, không cấp quyền, không tạo policy mặc định, không sửa mapping role/user hoặc reset admin. Nhiều node dùng khóa và upsert idempotent; không xóa route vắng trên một node trong rolling deploy. `lastSeenAt` chỉ là lần startup quan sát, **không khẳng định route đang sống**. Metadata có thể phản ánh node khởi động sau cùng; quyết định truy cập luôn theo code/policy request hiện hành. Dọn route cũ cần kiểm mọi phiên bản đang triển khai, chưa có chức năng tự dọn.

Tab **Tài nguyên UI/API** ở trang Phân quyền đọc `GET /api/v1/admin/access/resources?page=0&size=20`, phân trang DB, yêu cầu ADMIN + ACCESS_READ tại service. Public được xác định từ `WebRoutePolicy` cùng cấu hình Security, không có API chỉnh public flag. Auth/metrics/quản trị quyền là tài nguyên chỉ đọc để giữ đường cứu hộ. Picker policy lấy mapping thật trên node hiện tại, không lấy route cũ trong DB để kích hoạt lại.

Hai loại policy kiểm độc lập: MENU kiểm URL trang và ẩn menu; ENDPOINT kiểm method + template của API. Có quyền menu không tự có quyền API và ẩn menu không thay kiểm backend. Điều kiện role là any-of, permission là all-of; policy chỉ siết thêm guard service/controller, ownership/version vẫn bắt buộc. Public không cần policy/quyền chức năng nhưng CSRF/quota/validation vẫn áp dụng. ADMIN bỏ điều kiện policy bổ sung để cứu hộ, không bỏ guard nền.

Khi thêm module: khai báo Routes → mapping + Operation → service guard resource/action + scope → menu theo quy ước UI → startup sync → quản trị tạo policy nếu cần siết thêm. Không dùng danh bạ như API gateway hoặc cơ chế tự cấp quyền cho code mới chưa có guard.

## Cách hiểu quan hệ user/role/menu/API

Một user nhận nhiều role qua auth_user_roles; mỗi role nhận nhiều permission RESOURCE_ACTION qua auth_role_permissions. JWT filter đọc hợp các quyền từ DB mỗi request, bỏ role/permission đã inactive. VIEW giao diện dùng READ; ghi tách CREATE/UPDATE/DELETE, gán dùng ASSIGN, không tạo WRITE toàn hệ thống. ADMIN/role không thay kiểm owner/tenant/version.

Danh bạ auth_access_resources ghi mapping thật để chọn tài nguyên, không tự cấp authority. auth_access_policies gắn MENU/ENDPOINT với allowed_roles (any-of) và required_permissions (all-of); UI quản trị có thể chọn các role và quyền cho từng tài nguyên. MENU quyết định ẩn/hiện và chặn URL trang; ENDPOINT kiểm method + route template API, GET áp dụng HEAD. Có quyền menu không tự có quyền API. Bộ kiểm HTTP → JWT/DB authorities → policy → controller/service @PreAuthorize → scope repository phải cùng đạt. Public không cần quyền chức năng nhưng giữ CSRF/quota/validation. Auth/quản trị quyền/metrics là đường cứu hộ chỉ đọc về chính sách, không tùy ý mở public từ DB.

Không cần bảng nhóm quyền nữa vì Role đã làm nhiệm vụ đó. Khi thêm API phải khai báo guard resource/action trong code; policy quản trị chỉ siết thêm guard nền, không thể biến người thiếu CATALOG_DELETE thành người được xóa dù được gán role ở policy API. Cơ chế này có chủ đích tránh cấu hình vô tình bỏ kiểm nghiệp vụ/tenant.


## Annotation và vòng đời role — FOUNDATION05

`@AccessFunction(group = "CATALOG", permissions = {"CATALOG_UPDATE", "CATALOG_WRITE"})` đặt trên class hoặc method controller; method ghi đè class. Startup vẫn lấy HTTP method/template thật từ Spring, mô tả từ Operation và thêm group/declaredPermissions từ annotation vào danh bạ (V012). Chưa annotation thì GENERAL. Mã metadata được validate khi discover; public chỉ từ WebRoutePolicy. Danh sách permission trên annotation dùng để tra cứu, không phải biểu thức all-of và không tự cấp quyền. Biểu thức any/all và scope bắt buộc thực thi tại service; policy DB chỉ siết thêm. Thêm module cần cả annotation mô tả lẫn guard nghiệp vụ, không được coi metadata là authorization.

UserRepository đọc role active/chưa deleted và permission active từ DB mỗi request; giá trị Role/Permission là mã chuỗi, values() chỉ phục vụ bootstrap. Role tùy chỉnh có CATALOG_READ có thể vào `/admin`; trang danh mục không còn bắt ROLE_ADMIN. Các luồng quản trị quyền, journal và cứu hộ vẫn cần ADMIN cùng quyền hành động tương ứng. CUSTOMER là role mặc định của hợp đồng đăng ký public, không phải quyền do client chọn.

ADMIN không được sửa tên/trạng thái/mã, xóa hay thay mapping qua API. Role khác, kể cả OPERATOR/DRIVER/CUSTOMER, được sửa tên/tắt qua registry version. DELETE `/api/v1/admin/access/registry/roles/{code}?version=...` cần ADMIN + ACCESS_DELETE: giữ advisory lock cùng gán role/policy, chặn COM015/409 nếu còn bất kỳ user hoặc policy tham chiếu (kể cả policy tắt). Khi hợp lệ, xóa mapping quyền và tombstone role (`deleted=true`, active=false), audit principal; mã không được tái sử dụng và startup không tạo lại. Đây là xóa mềm để giữ lịch sử, không tự rút role khỏi user hay làm policy mở quyền. Role CUSTOMER bị tắt/xóa thì đăng ký mới không tạo được CUSTOMER; cần cân nhắc hợp đồng đăng ký trước khi thao tác. Quyền lõi vẫn không được tắt; custom permission hiện vô hiệu hóa, chưa có API xóa quyền.

Đăng ký public cũng lấy cùng advisory lock gán/xóa role trước registration/user; BCrypt ở ngoài transaction. CI dùng session giữ khóa riêng, xác nhận request đăng ký phải chờ trước INSERT user. Nhờ đó xóa CUSTOMER và tạo mapping mới không chạy lọt nhau giữa node.
