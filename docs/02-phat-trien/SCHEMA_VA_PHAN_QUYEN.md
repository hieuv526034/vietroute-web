# Cấu hình schema và quyền theo hành động

Cập nhật 22/10/2026, phụ trách hieuv526034. Quy ước này thay phần schema cố định và quyền WRITE gộp trong tài liệu nền tảng trước đó.

## Bản đồ bảng và ranh giới cấu hình phân quyền

Hệ thống có **11 bảng chuyên trách phân quyền và bảng tài khoản `auth_users`**, tổng cộng 12 bảng liên quan trực tiếp. Đây là cấu trúc từ migration/source; không phải kết quả đếm dữ liệu hoặc kiểm schema trực tiếp trên Neon. Bảng nằm trong schema do `VIETROUTE_DB_SCHEMA` chọn, không viết prefix schema cố định vào DAO.

| Bảng | Khóa chính | Trách nhiệm và nguồn ghi |
|---|---|---|
| `auth_users` | `id` | Danh tính, trạng thái active/pending, xác minh email, mật khẩu tạm, token_version. Auth quản lý; không có danh sách quyền riêng trong cột user |
| `auth_roles` | `code` | Registry vai trò/nhóm quyền, tên vi/en, active/deleted, version registry và version mapping. Quản trị sửa role thường; ADMIN được bảo vệ |
| `auth_permissions` | `code` | Registry mã RESOURCE_ACTION, tên vi/en, active/version. Startup đăng ký mã source; API quản trị tạo mã tùy chỉnh |
| `auth_user_roles` | `(user_id, role_code)` | Gán một hoặc nhiều role toàn cục cho tài khoản; mutation giữ version/thu hồi phiên/lịch sử |
| `auth_role_permissions` | `(role_code, permission_code)` | Gán quyền cho role; nguồn authority chức năng hiện hành sau khi lọc trạng thái DB |
| `auth_access_functions` | `code` | Danh bạ cây chức năng: parent, nhãn, đường menu, VIEW, thứ tự. Đồng bộ từ AccessFeatures khi bật sync; không phải menu tùy ý được tạo qua DB |
| `auth_access_resources` | `(kind, target)` | Danh bạ MENU/ENDPOINT, handler, function, quyền khai báo, ANY/ALL, public/configurable/admin_only và thời điểm startup thấy route. Đồng bộ từ mapping/annotation thật |
| `auth_access_policies` | `id`, unique `(kind,target)` | Điều kiện bổ sung cho menu/endpoint: enabled, required_permissions và allowed_roles. Quản trị cấu hình; chỉ siết guard nền, không biến route private thành public |
| `auth_access_changes` | `id` | Lịch sử thao tác quản trị với actor/trace/before/after tường minh; ghi cùng transaction mutation, có retention/capacity |
| `auth_data_scopes` | `id`, unique `code` | Phạm vi dữ liệu bảo mật và trạng thái/version; chưa phải hồ sơ nhà xe |
| `auth_scope_memberships` | `(scope_id,user_id)` | Thành viên của scope, active/version/audit; không dùng cho khách public tra cứu chuyến |
| `auth_scope_roles` | `(scope_id,user_id,role_code)` | Role áp dụng trong scope. FK đến membership và mapping role toàn cục của đúng user; scoped role phải là tập con global roles |

Nguồn tạo bảng: V001 (user/role/mapping user), V003 (permission/mapping role), V007 (policy), V010 (resource), V013 (function và metadata binding), V014 (history), V015 (scope/membership/scoped role). V006/V007/V011/V012/V013 còn bổ sung audit/trạng thái/index cho các bảng cũ; luôn đọc schema cuối sau toàn bộ migration, không chỉ CREATE TABLE đầu tiên.

Luồng cấp chức năng là `auth_users → auth_user_roles → auth_roles → auth_role_permissions → auth_permissions`. Với dữ liệu nhà xe, `auth_scope_memberships → auth_scope_roles` lọc đúng role của user trong scope trước khi lấy permission. Policy là điều kiện bổ sung ở HTTP; service vẫn kiểm chức năng và DAO kiểm bản ghi/phạm vi. Không có bảng `auth_user_permissions`, tầng nhóm quyền thứ hai hoặc bảng action/endpoint-permission riêng: action là enum, quyền endpoint đang lưu trong `declared_permissions` của resource.

Hai bảng bảo mật liên quan là `auth_password_history` và `auth_account_challenges` (V011); chúng phục vụ mật khẩu/mã xác minh, không cấp permission. `api_request_journal`, bảng activity và `vietroute_schema_history` phục vụ vận hành/migration, không phải nguồn cấp quyền.

| Phần cấu hình | Có thay đổi qua DB/UI không? | Ranh giới hiện tại |
|---|---|---|
| Role thường, tên/trạng thái và mapping user/role/permission | Có, qua API/UI có quyền/version | Role/Permission là value object chuỗi; `values()` chỉ liệt kê mã bootstrap, không giới hạn registry hoặc suy authority của user |
| Mã permission mới | Có | Phải đúng RESOURCE_ACTION; chỉ tạo bản ghi không tự tạo nút/API hoặc thêm guard vào hàm chưa được lập trình |
| Policy bổ sung | Có | Ngữ nghĩa hiện cố định: đủ mọi required permission và ít nhất một allowed role nếu danh sách role không rỗng. enabled=false chặn người thường tại target, không phải bỏ áp dụng policy. ADMIN được miễn policy bổ sung, vẫn qua guard bắt buộc |
| Scope/membership/scoped role | Có | Đã tích hợp nhà xe/fleet/tuyến/chuyến/đơn/báo cáo và cấp tài xế qua DAO. Mỗi module vẫn tự bind ID + scope/owner/assigned; không có cơ chế tự chèn WHERE |
| Cây chức năng, menu path/parent/nhãn/thứ tự, bộ chọn quyền hệ thống | Trong source AccessFeatures | Đổi cần sửa/triển khai code; bảng function/resource là danh bạ đồng bộ, không phải công cụ tự xây chức năng từ DB |
| Action, guard service, binding HTTP ANY/ALL/adminOnly, public/self-service | Trong enum/annotation/WebRoutePolicy | Phải giữ ở server; không nhận biểu thức bảo mật, route public hay tên method từ DB/client |
| Bảo vệ ADMIN/system/bootstrap, không tự đổi role, giới hạn ủy quyền | Trong code và một số CHECK/FK DB | Là rào bảo mật có chủ đích. Bootstrap username/email lấy từ cấu hình môi trường, không cố định một username trong Java |
| Quy tắc tài khoản và điều hướng ban đầu | Một phần trong code | Đăng ký public chỉ cấp CUSTOMER; ADMIN vào workspace quản trị, tài khoản khác vào account. DRIVER không tự có membership/hồ sơ/chuyến được giao |
| Quota/retention | Env được validate trong AccessSettings | Registry mặc định 512, policy 256, scope 512; có trần code. Hợp đồng payload còn cố định 32 role/user, 512 permission/role, 32 membership/user và 32 role/membership |

Không có cấp trực tiếp permission cho từng user, kế thừa role, explicit DENY hoặc ngày hết hạn cho từng mapping. User nhận hợp quyền từ các role active; bỏ một role chưa chắc mất permission nếu role khác vẫn cấp. Nếu nghiệp vụ yêu cầu các cơ chế này phải thiết kế/test riêng, không dùng policy hoặc tên role để giả lập rồi kỳ vọng mọi guard hiểu cùng nghĩa.

Tập quyền tự đồng bộ và bảo vệ ADMIN khác với quyền role thường: startup sync/init có thể đăng ký/bật lại permission khai báo trong source và sửa mapping ADMIN; không tự ghi mapping mới cho role thường. `CATALOG_WRITE`/`ACCESS_WRITE` còn là alias tương thích phạm vi hẹp. Thêm module dùng resource/action chuẩn, không thêm alias WRITE mới. Phát hiện/việc còn dở của từng đợt rà soát luôn ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), không coi tài liệu này là chứng minh mọi ca đã kiểm.

ACCESS23 thống nhất tập mã này bằng `AccessPermissionDefinitions`: gồm bootstrap/tương thích, VIEW của cây chức năng, toàn bộ quyền HTTP (kể cả extra/alias) và service-only. Collector servlet công bố metadata cả khi tắt ghi sync. API/UI đánh dấu `builtIn` từ cùng tập source; không cho tạo trùng hoặc tắt mã mà startup sẽ bật lại, vẫn cho sửa tên và gán/thu hồi mapping role thường. Quyền tự tạo ngoài source vẫn đổi trạng thái được. Snapshot này chỉ chứa định nghĩa của node; guard tiếp tục đọc trạng thái account/tokenVersion/mapping DB hiện hành.

Module mới khai báo resource/action và bổ sung `permission.<CODE>` trong cả hai bundle vi/en; không phải thêm từng mã vào `Permission.values()`. Sync dùng đúng key của mã mới, thiếu bản dịch thì hiện chính mã thay nhãn chung `permission.custom`; khi bản ghi đã có, sync giữ tên quản trị đã sửa. API/modal nguồn quyền trả đủ hợp quyền nhiều role tới trần an toàn `Limits.REGISTRY_MAX` (2048), không phụ thuộc quota tạo thủ công thấp hơn; đọc thêm một dòng để báo COM016 nếu dữ liệu ngoài API vượt trần, không trả danh sách thiếu. Giới hạn 512 permission/role và 32 role/user vẫn áp dụng.

## Danh mục địa lý dùng chung — LOCATION01

V016 thêm `location_provinces` và `location_places` trong schema cấu hình, ngoài nhóm bảng cấp quyền ở trên. Hai bảng giữ code nội bộ unique/bất biến, tên vi/en, active, sort_order, version và audit TIMESTAMPTZ/actor NOT NULL. location_places có FK province_id (không đổi qua API), địa chỉ và NUMERIC tọa độ có CHECK giới hạn. Không seed địa danh hoặc có luồng xóa vật lý. Ngừng sử dụng giữ ID/tham chiếu; public chỉ lấy địa điểm và tỉnh cùng active. Thứ tự khóa khi tạo/bật địa điểm là tỉnh FOR SHARE trước INSERT/UPDATE địa điểm, cùng transaction; cập nhật version bằng compare-and-set SQL. Các bảng tuyến/chuyến sau phải tham chiếu ID và snapshot dữ liệu lịch sử theo hợp đồng API.

Danh mục chung không cần scope nhà xe; quản trị ADMIN + LOCATION_* ở cả annotation HTTP và service. LOOKUP công khai có projection riêng, không mở danh sách quản trị hoặc coi mọi hành động READ là public. Startup sync đăng ký VIEW/READ/CREATE/UPDATE từ source và chỉ cấp ADMIN; role thường không được tự cấp. Nâng app cần áp dụng V016 qua init rõ ràng và sync registry khi bật, không sửa V001–V015/checksum hoặc chạy migration để thử build trên Neon. Hợp đồng và giới hạn ở [API base](API_BASE.md#danh-mục-địa-lý--location01).

## Đổi schema ở một nơi cấu hình

Biến `VIETROUTE_DB_SCHEMA` là nơi cấu hình schema của ứng dụng. Mặc định là `vietroute`, database Neon vẫn là `neondb`. Ví dụ khi tạo một môi trường/DB mới:

```properties
VIETROUTE_DB_SCHEMA=project_2026
```

WAR không tự đọc `.env`; launcher local chủ động đọc file riêng theo hướng dẫn chạy IntelliJ; đặt biến trong Environment Render hoặc cấu hình IDE/Tomcat. Giá trị môi trường ưu tiên hơn `db.schema` trong `src/main/resources/database.properties`. Render Blueprint có key này; service tạo thủ công phải đặt trong Dashboard. Không cần sửa/rebuild Java để chọn schema khác trên cùng image.

Tên schema chỉ nhận chữ thường ASCII/số/underscore, bắt đầu bằng chữ, dài tối đa 63, không dùng public, information_schema hoặc tiền tố pg_. Giá trị từ HTTP, role, tenant hoặc request không được dùng làm schema. `JdbcSchema` whitelist trước khi quote identifier; các giá trị SQL nghiệp vụ vẫn bind bằng PreparedStatement. Một schema triển khai không phải cơ chế tenant theo từng request.

Luồng cấu hình:

1. DatabaseSettings đọc key một lần khi tạo root context, kiểm tra tên; JdbcSchema là bean immutable, không có tên schema static dùng chung.
2. JdbcConnectionFactory mượn connection, tắt autocommit rồi gọi `pg_catalog.set_config('search_path', ?, true)` trong transaction (AUTH19) với path `"<schema>", pg_temp`. Mỗi lần mượn đều đặt lại, kể cả URL có currentSchema khác hoặc borrower trước đã đổi trạng thái session.
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

`PermissionAction` chuẩn hóa VIEW, READ, CREATE, UPDATE, DELETE, ASSIGN, RETRY, SEND. Mỗi permission gắn **resource + action**, không có một quyền READ/WRITE/DELETE toàn hệ thống. Response registry `/api/v1/admin/access/permissions` có `code`, `resource`, `actions`, `messageKey`, `name` để UI dựng ma trận quyền. Key của permission/action có đủ vi/en.

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

Luồng startup: root `DatabaseInitializer` áp dụng V013 → servlet context tạo xong mapping → `ContextRefreshedEvent` gọi `AccessResourceSynchronizer` → service transaction → advisory lock/upsert `auth_access_resources`. `VIETROUTE_ACCESS_SYNC_ENABLED` mặc định theo DB_INIT_ENABLED nếu không khai báo; có thể tắt riêng, hoặc bật sau khi DBA đã áp dụng V013. Render Blueprint bật true. Thiếu schema/DB khi bật sync sẽ làm startup báo lỗi, không báo đồng bộ thành công giả. Maven/unit test tắt ghi init/sync và không đọc Neon. Metadata vẫn validate khi servlet context khởi động dù tắt sync.

Danh bạ có PK `(kind,target)`, audit system=0, TIMESTAMPTZ và index audit. Startup validate metadata, đăng ký permission khai báo trong code và bổ sung đúng các quyền đó cho ADMIN. Không cấp quyền mới cho role thường, không tạo policy mặc định hoặc reset mật khẩu. Metadata/public flag trong DB không thể mở route. Nhiều node dùng khóa và upsert idempotent; không xóa route vắng trên một node trong rolling deploy. `lastSeenAt` chỉ là lần startup quan sát, **không khẳng định route đang sống**. Metadata có thể phản ánh node khởi động sau cùng; quyết định truy cập luôn theo code/policy request hiện hành. Dọn route cũ cần kiểm mọi phiên bản đang triển khai, chưa có chức năng tự dọn.

Tab **Tài nguyên UI/API** ở trang Phân quyền đọc `GET /api/v1/admin/access/resources?page=0&size=20`, phân trang DB, yêu cầu ACCESS_READ tại service (có thể ủy quyền). Public được xác định từ `WebRoutePolicy` cùng cấu hình Security, không có API chỉnh public flag. Auth/metrics/quản trị quyền là tài nguyên chỉ đọc để giữ đường cứu hộ. Picker policy lấy mapping thật trên node hiện tại, không lấy route cũ trong DB để kích hoạt lại.

Hai loại policy kiểm độc lập: MENU kiểm URL trang và ẩn menu; ENDPOINT kiểm method + template của API. Có quyền menu không tự có quyền API và ẩn menu không thay kiểm backend. Điều kiện role là any-of, permission là all-of; policy chỉ siết thêm guard service/controller, ownership/version vẫn bắt buộc. Public không cần policy/quyền chức năng nhưng CSRF/quota/validation vẫn áp dụng. ADMIN bỏ điều kiện policy bổ sung để cứu hộ, không bỏ guard nền.

Khi thêm module: khai báo function/menu trong AccessFeatures → mapping + Operation + AccessFunction(actions) → guard service AccessResource/AccessOperation và scope → startup validate/sync → gán role trong cây. Không tạo AccessRules cho từng quyền; xem ACCESS08 bên dưới. Header tự lọc theo VIEW; quản trị tạo policy nếu cần siết riêng endpoint. Không dùng danh bạ như API gateway hoặc cơ chế tự cấp quyền cho code mới chưa có guard.

## Cây chức năng và quyền hành động — ACCESS03

Triển khai theo [Issue #49](https://github.com/hieuv526034/vietroute-web/issues/49), thay thiết kế ACCESS02. Trạng thái/CI/merge xem [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), không suy ra đã nghiệm thu chỉ từ tài liệu này.

```text
User → nhiều Role → hợp Permission hiệu lực (DB mỗi request)
Hệ thống
└── Danh mục
    ├── CATALOG_VIEW   → GET /admin
    ├── CATALOG_READ   → GET /api/v1/admin/catalogs/{id}
    ├── CATALOG_CREATE → POST /api/v1/admin/catalogs
    ├── CATALOG_UPDATE → PUT /api/v1/admin/catalogs/{id}
    └── CATALOG_DELETE → DELETE /api/v1/admin/catalogs/{id}
```

VIEW mở trang/hiện menu, READ đọc API; không tự bao hàm CREATE/UPDATE/DELETE. Role có VIEW+CREATE chỉ thấy menu/nút Thêm; gọi API sửa/xóa trực tiếp trả 403 với envelope/mã lỗi/traceId chung. Vai trò chỉ dùng API có thể chỉ nhận CREATE mà không VIEW; đó là hợp đồng tách UI/API, không phải đường bỏ kiểm quyền. Form UI gợi ý VIEW khi chọn hành động, preview nêu rõ quyền thêm. Sửa trên UI cần cả READ để nạp dữ liệu và UPDATE; RETRY/gán cũng cần READ nếu muốn thao tác từ danh sách. Lookup công khai giữ projection và CSRF/quota/validation hiện hành.

Role là nhóm quyền duy nhất. User nhiều role nhận hợp quyền; thu hồi ở một role chưa chắc mất quyền nếu role khác vẫn cấp. Quyền hiệu lực bỏ role inactive/deleted và permission inactive, không tin role trong JWT. `/users/{id}/effective` chỉ rõ permission đến từ role nào; user tắt trả tập hiệu lực rỗng. Owner/tenant/version vẫn kiểm ở service/repository, không coi full quyền chức năng là bỏ scope dữ liệu.

### Một định nghĩa cho endpoint, menu và nút

`AccessConstants.Functions/Groups/Permissions` chứa mã compile-time; không chứa danh sách người được cấp. `AccessFeatures` khai báo code, parent, nameKey, menu path, VIEW permission và thứ tự cây. Header được dựng từ cây sau khi lọc capabilities, bỏ nhóm rỗng; API docs và tài khoản cá nhân giữ luồng public/self-service riêng.

```java
@AccessFunction(
    group = Groups.CATALOG,
    function = Functions.CATALOG,
    permissions = { Permissions.CATALOG_CREATE, Permissions.CATALOG_WRITE },
    match = AccessFunction.Match.ANY
)
```

Method ghi đè tập quyền class; function bỏ trống kế thừa class. ANY là một quyền đủ (phục vụ WRITE tương thích), ALL đòi mọi quyền; không đoán điều kiện từ HTTP method. `adminOnly` của class luôn giữ khi method bổ sung annotation. Self-service khai báo `authenticated=true`, owner kiểm riêng; public chỉ từ WebRoutePolicy, không đọc public flag DB để bỏ guard.

`AccessPolicyInterceptor` kiểm annotation trên HandlerMethod thật trước controller, rồi áp policy siết thêm. `AccessOperation` dùng meta-PreAuthorize chung và suy ra resource/action cho service/page; giữ method security ở cả root/servlet context, bảo vệ cả lời gọi bean không qua HTTP. Không parse chuỗi SpEL để sinh quyền. Chuỗi đầy đủ: JWT/trạng thái/tokenVersion → authorities DB → annotation/policy HTTP → guard service → scope/version. Chưa đăng nhập trả 401 hoặc redirect trang login; thiếu quyền trả 403.

`GET /api/v1/auth/capabilities` chỉ trả quyền của chính principal: function, view, actions, targets. Tính từ cùng mapping/guard/policy, không nhận user ID/client roles. Frontend dùng target cụ thể khi một action có nhiều endpoint, tránh gọi API đọc bị policy chặn. Không lưu quyền/token vào localStorage; API vẫn kiểm lại mỗi request. Một số hành động SYSTEM_READ dùng chung giữa giám sát/journal, cấu hình dùng CONFIGURATION_READ riêng; UI đánh dấu quyền dùng chung, chỉ gợi VIEW của nhánh vừa chọn. Journal/online vẫn cần ADMIN + SYSTEM_READ theo hợp đồng dữ liệu nhạy cảm.

### Startup và lưu PostgreSQL

Servlet context validate group/permission/function, cây trùng mã/cha sai/chu trình, trang thiếu binding VIEW và endpoint private thiếu quyền/self-service. Sau khi mapping sẵn sàng, bật ACCESS_SYNC sẽ transaction sync `auth_access_functions` cùng `auth_access_resources`, registry permission và quyền ADMIN bằng advisory lock. Chỉ quyền khai báo chủ động trong source mới được đăng ký; không cấp mọi URL được tìm thấy. INSERT registry giữ tên/trạng thái đã có. Audit system=0, TIMESTAMPTZ, FK/index; không reset password, không cấp mới cho role thường hoặc phục hồi quyền đã thu hồi sau restart.

Danh bạ DB giữ parent/path/VIEW/order cùng binding function/match/adminOnly; runtime luôn dùng mapping của node đang xử lý. Route cũ trong DB không kích hoạt lại. Nhiều node dùng lock/upsert; không xóa metadata vắng trên một node khi các phiên bản tương thích cùng chạy. `lastSeenAt` chỉ là startup quan sát. Cờ sync tắt chỉ ngừng ghi DB, không tắt kiểm metadata/guard. Mapping đã validate được giữ bất biến theo servlet context, không chứa quyền user/lịch sử và không quét lại mỗi API. Policy dùng một snapshot trên request; JSON/health bỏ dựng model menu. Role/quyền hiệu lực vẫn đọc DB mỗi request.

V013 mới bổ sung VIEW, CONFIGURATION_READ và bảng/binding cây, giữ nguyên V001–V012. Trên dữ liệu V012, backfill VIEW một lần từ quyền cùng resource đã có, chuyển tiếp SYSTEM_READ cũ sang CONFIGURATION_READ cho role cũ, giữ mã/password/hash/mapping cũ, tăng permission_version để form cũ không ghi đè. Sau khi quản trị bỏ VIEW, restart không cấp lại cho role thường. **Nâng từ V012 phải drain/dừng các node cũ trước khi áp V013**, rồi khởi động toàn bộ bằng source mới: code cũ chưa hiểu PermissionAction.VIEW. Chưa hỗ trợ rolling không gián đoạn qua mốc này; nhiều node cùng bản mới vẫn kiểm bằng PostgreSQL CI. Không chạy upgrade/test ghi trên Neon để xác nhận build.

### Quản trị và giới hạn ủy quyền

ACCESS_READ đọc cây/registry/policy/user/nguồn quyền. ACCESS_CREATE/UPDATE/DELETE quản lý registry/policy; ACCESS_ASSIGN (hoặc WRITE tương thích) gán mapping. Role tùy chỉnh được dùng các chức năng này nếu có quyền, không bắt buộc tên role ADMIN.

ADMIN vẫn bất biến tên/trạng thái/mã/mapping và không xóa. Người được ủy quyền không tự gán lại role cho tài khoản mình, không sửa user ADMIN/system, không gán ADMIN, không thay mapping có quyền ngoài tập đang có (kiểm cả tập cũ/mới). Gán role kiểm toàn bộ quyền của role đích trong transaction có khóa. AccessActors buộc actor truyền vào service trùng principal hiện tại, chặn caller nội bộ giả ADMIN để bỏ giới hạn. Bật lại role hoặc permission cũng là cấp quyền gián tiếp: actor không ADMIN chỉ sửa registry khi tập quyền bị ảnh hưởng nằm trong tập mình có, chặn tự kích hoạt role/permission mạnh đã tắt. Giữ bảo vệ admin cuối và tăng tokenVersion khi gán role cho user.

Role khác có thể sửa/tắt/xóa mềm: DELETE `/registry/roles/{code}?version=...` cần ACCESS_DELETE, chặn 409 nếu còn user/policy tham chiếu kể cả policy tắt; xóa mapping trong cùng transaction, giữ tombstone và không tái dùng mã. CUSTOMER tắt/xóa ảnh hưởng đăng ký public, cần cân nhắc trước thao tác. Permission lõi không được tắt; custom permission có thể vô hiệu hóa trong phạm vi ủy quyền, chưa xóa vật lý.

### Màn gán và hợp đồng API

Trang `/operations/access` cần ACCESS_VIEW. Chọn role → xem/gán cây (kể cả role tắt để chuẩn bị mapping trước khi bật); ADMIN mở được cây chỉ đọc. Nhóm có checkbox ba trạng thái, tìm theo tên/mã/nhóm cha, chọn tất cả chỉ trong kết quả hiển thị, mở/thu gọn, và mở endpoint để xem method/template/match/ADMIN. Quyền dùng chung đồng bộ cùng một mã; quyền cũ/ngừng hoạt động vẫn hiện để ADMIN bỏ, không âm thầm làm mất mapping khi lọc. Preview quyền thêm/bỏ và số tài khoản đang gán role là snapshot, gồm cả user tắt, không hứa số liệu bất biến đến lúc lưu.

Giữ modal/ARIA/focus/loading/chặn bấm lặp và lỗi 403/409 không đóng hoặc tự gửi lại. Footer lưu bên phải, vùng cây cuộn riêng. Tab tài khoản xem nguồn role cấp quyền; tra cứu/paging tại DB, không tải mọi user vào RAM. READ-only chỉ xem; VIEW-only không tự tải các API READ. Policy endpoint riêng nằm ở tab Menu và endpoint, chỉ siết thêm quyền đã gán, không sửa guard bắt buộc.

| API | Điều kiện / dữ liệu |
|---|---|
| GET `/api/v1/admin/access/tree` | ACCESS_READ; cây + action + binding của node hiện tại, permission active từ DB |
| GET `/api/v1/auth/capabilities` | Đã đăng nhập; chỉ capability của chính principal |
| GET `/api/v1/admin/access/users/{id}/effective` | ACCESS_READ; username/active và map permission → role |
| PUT `/api/v1/admin/access/roles/{role}/permissions` | ACCESS_ASSIGN/WRITE; permissions tối đa 512, version bắt buộc; thay toàn bộ mapping |
| PUT `/api/v1/admin/access/users/{id}/roles` | ACCESS_ASSIGN/WRITE + giới hạn ủy quyền; roles tối đa 32, version bắt buộc |
| POST/PUT registry/policy | Quyền CREATE/UPDATE; response chỉ tài nguyên vừa ghi, không trả toàn bộ registry cho người chưa có READ |
| DELETE registry/policy | Quyền DELETE + version; response danh sách rỗng sau khi thành công |

Danh sách role dùng một statement tổng hợp mapping/số user, tránh query N+1. Role tắt không được gán mới cho user; khi thay toàn bộ role của user cần chọn các role đang hoạt động.

Các API dùng BaseController/ApiResult, Swagger @Operation/@Schema, vi/en và traceId. Không tự replay mutation, không xuất token debug thật hoặc credentials.

## Bộ quyền hệ thống và quyền DB — ACCESS04

Theo yêu cầu và lựa chọn chủ dự án ngày 24/10/2026: **gán cả bộ quyền hệ thống, giữ bảo vệ ADMIN**. Màn cây có nút Chọn bộ quyền hệ thống; `AccessFeatures.inSystemBundle` đánh dấu JOBS/MONITORING/CONFIGURATION/JOURNAL/MAIL/ACCESS, response cây có `systemBundle`. Bộ chọn lấy các permission active từ cây/endpoint hiện có, bỏ WRITE tương thích và endpoint chỉ ADMIN; không gom CRUD danh mục/ảnh hoặc nghiệp vụ tương lai. Chọn chỉ sửa draft, preview ghi đủ mã; Lưu dùng API thay mapping có version/CSRF và giới hạn ủy quyền đang có. Mẫu bị khóa nếu actor thiếu một quyền trong cả bộ, không lặng lẽ gán một phần. Quyền ngoài bộ đang có được giữ nguyên.

Không tạo một authority toàn cục để bỏ mọi guard. DB vẫn lưu permission riêng trong auth_role_permissions, nên thu hồi từng quyền và xem nguồn role vẫn hoạt động. Đây là snapshot được quản trị chủ động gán: thêm endpoint/quyền mới không tự tăng quyền cho role thường. ADMIN vẫn được bootstrap/sync bổ sung quyền source khi bật, không tự đổi mật khẩu. Nhật ký/online và tài khoản ADMIN/system/cứu hộ giữ giới hạn đã có; gán mẫu không cho role thường vượt ADMIN.

| Thành phần | Trách nhiệm |
|---|---|
| AccessConstants/Permission constants | Mã ổn định để lập trình viên khai báo chức năng endpoint/service; không chứa user được cấp |
| Registry + user-role + role-permission DB | Admin tạo role/mã tùy chỉnh, gán/tắt/thu hồi; là dữ liệu quyết định quyền hiệu lực |
| JWT filter → AccessFunction → PreAuthorize | Đọc lại user/quyền active từ DB mỗi request, kiểm HTTP rồi service; lời gọi nội bộ cũng bị kiểm |

`@PreAuthorize(AccessRules.CATALOG_UPDATE)` khai báo điều kiện của thao tác, không cấp quyền cho user. ACCESS06 thay biểu thức `hasAuthority` trước đây bằng lời gọi hàm `accessGuard` đọc DB ngay khi method security kiểm; chi tiết ở mục dưới. Giữ kiểm service vì worker/bean nội bộ có thể gọi mà không qua interceptor HTTP. Xóa annotation service sẽ làm mất lớp bảo vệ đó. `Permission.values()` chỉ phục vụ bootstrap/tương thích; valueOf/JSON nhận mã registry hợp lệ, không giới hạn những hằng đã biết. Không dùng values() để trả quyền của user. Mã endpoint mới khai báo qua constants/annotation sẽ được collector đăng ký khi sync bật, không cần thêm vào Permission.values() để JWT chấp nhận; mã tùy chỉnh có tên mặc định chung, quản trị đặt tên vi/en rõ nghĩa trong registry DB.

Nghiệp vụ mới có thể dùng RESOURCE_LIST (danh sách), RESOURCE_DETAIL (chi tiết), CREATE/UPDATE/DELETE/ASSIGN; VIEW vẫn riêng cho menu. LIST và DETAIL độc lập, không bao hàm READ hoặc lẫn nhau. Triển khai đồng nhất bản hiểu LIST/DETAIL trên mọi node trước khi tạo/gán các mã này, vì node cũ chưa parse được action mới. READ giữ hợp đồng cũ cho các API hiện tại, lookup công khai không bị đổi thành API kín trong đợt này. Khi làm module mới phải khai báo guard/actions và kiểm scope tại service, startup thu thập endpoint, rồi quản trị gán vào role. Mã permission tùy chỉnh chỉ có tác dụng với endpoint đã khai báo hoặc policy siết thêm; DB không biến route public thành private hoặc thay code nghiệp vụ bằng SpEL người dùng nhập.

## Phân tích cây chức năng và quyền hành động — ACCESS02

Thiết kế ban đầu ở [Issue #46](https://github.com/hieuv526034/vietroute-web/issues/46)/PR #47 đã được triển khai theo hợp đồng ACCESS03 phía trên. Trạng thái và bằng chứng nghiệm thu luôn xem tiến độ/PR, không coi bản phân tích cũ là kết quả chạy thật.

## Quản trị tài khoản và ma trận menu — ACCESS05

Màn `/operations/accounts` là khu danh tính toàn hệ thống dành riêng ADMIN, có ACCOUNT_VIEW cho trang, ACCOUNT_READ cho danh sách/preview, ACCOUNT_CREATE + ACCESS_ASSIGN để cấp tài khoản, ACCOUNT_UPDATE để khóa/mở. Các guard nằm ở annotation HTTP và service trong hai context WAR. Role DRIVER/CUSTOMER là lựa chọn nhanh cho tài khoản; một user có nhiều role active. Không tạo tầng nhóm quyền thứ hai, không tạo membership nhà xe giả và không mở quyền quản trị toàn hệ thống cho OPERATOR. Hồ sơ tài xế/liên kết nhà xe tiếp tục thuộc SCOPE01/AUTH03.

Quy trình thao tác:

1. Vào Quản trị truy cập → Phân quyền; dùng **Tạo vai trò** nếu cần role riêng. Role ADMIN bất biến. Role mới chưa được cấp quyền tự động.
2. Chọn role → Chỉnh quyền: ma trận cho thấy tất cả menu/chức năng hiện có, mỗi cột là một hành động. Tick từng ô hoặc cả nhánh, xem mã thêm/bỏ trước Lưu. Mở menu VIEW độc lập với quyền dữ liệu; dấu gạch ngang nghĩa là chưa có endpoint cho hành động đó. Chi tiết API vẫn hiển thị guard ANY/ALL và ADMIN-only. Tìm theo tên/mã/nhóm giữ nguyên lựa chọn ngoài kết quả.
3. Vào Tài khoản → Tạo tài khoản: username, tên, mật khẩu tạm và ít nhất một role active. Service BCrypt trước khi lấy connection, rồi khóa mapping, kiểm role và INSERT user/mapping/audit trong một transaction. Trùng username trả 409, không tạo user dở dang. Không trả password/hash/token/email trong DTO quản trị.
4. Bàn giao mật khẩu tạm qua kênh riêng. Sau login, JWT hợp lệ nhưng không có authority; chỉ dùng hồ sơ đọc, đổi mật khẩu, logout và API công khai. Trang chức năng chuyển về `/account`; API riêng trả AUTH011. Đổi mật khẩu thành công tăng tokenVersion và phát phiên mới; authorities lại đọc DB. AUTH17 cấp sẵn trạng thái sẵn sàng riêng cho bootstrap; tài khoản khác sau đổi mật khẩu còn phải xác minh email (AUTH012) trước khi dùng quyền.
5. Danh sách lọc prefix username, role và active, phân trang PostgreSQL. Gán role dùng API/version hiện có và thu hồi mọi JWT cũ. Nút xem cho biết menu/thao tác hiệu lực của user, đã xét trạng thái, mật khẩu tạm, role/quyền active và policy của **user đích**, không dùng snapshot bỏ qua policy của admin đang xem.
6. Khóa/mở dùng version; cùng khóa gán role rồi khóa dòng user, đổi active + tokenVersion + audit, xóa challenge cũ nguyên tử. Không khóa system/bản thân/ADMIN; không mở/khóa tài khoản đang chờ kích hoạt để tránh bỏ xác minh email. Không xóa user hoặc lịch sử. Mở lại không khôi phục JWT/challenge đã thu hồi.

Không cần migration mới: tái sử dụng auth_users/auth_user_roles; startup sync đăng ký ACCOUNT_* và function ACCOUNTS khi bật, chỉ tự cấp ADMIN. Giữ V001–V013 nguyên vẹn. ADMIN-only của khu danh tính không thể bị nới bằng policy hay cấp ACCOUNT_* cho role khác. Không gộp khu danh tính vào mẫu quyền hệ thống cho ordinary roles. Preview là ảnh chụp trạng thái đọc tại thời điểm yêu cầu, không giữ quyền hoặc lịch sử trong RAM, không phát token impersonation; guard backend luôn kiểm lại lúc thao tác. Email mời tự động và quản lý tài xế theo phạm vi nhà xe chưa nằm trong đợt này.

Triển khai ACCESS05: nâng toàn bộ node hoặc drain node cũ trước khi cấp tài khoản mật khẩu tạm. Code cũ chưa cưỡng chế flag này nên không được duy trì mixed-version rồi tuyên bố bắt đổi mật khẩu đồng nhất. Không đổi khóa JWT hoặc reset mật khẩu admin đang dùng.

## Hàm kiểm DB trong PreAuthorize — ACCESS06

Mốc ACCESS06 giữ `@PreAuthorize`, nhưng chuyển mọi biểu thức trong `AccessRules` sang bean `DatabaseAccessGuard` tên `accessGuard`. Cách khai báo hằng theo từng quyền dưới đây là lịch sử, đã được thay bằng metadata chung ở ACCESS08; cơ chế đọc DB và giới hạn thu hồi vẫn giữ:

```java
@PreAuthorize(AccessRules.CATALOG_UPDATE)
```

Hằng này triển khai `@accessGuard.any(authentication, 'CATALOG_UPDATE', 'CATALOG_WRITE')`. WRITE là mã tương thích có phạm vi giới hạn đã chốt, không phải quyền ghi toàn hệ thống. Module mới dùng mã từ `AccessConstants.Permissions`, annotation `AccessFunction` và rule dùng chung, không viết mã user/role được cấp vào controller. `has` kiểm một quyền, `any` kiểm ít nhất một, `all` kiểm đủ các quyền; `adminAll` kiểm ADMIN kèm đủ quyền hành động. Các điều kiện gộp dùng cùng một statement DB.

`AuthorizationRepository.find` đọc `auth_users` → `auth_user_roles` → `auth_roles` → `auth_role_permissions` → `auth_permissions` bằng LEFT JOIN trong schema cấu hình. Chỉ role active/chưa xóa và permission active tham gia quyền hiệu lực. Hàm kiểm tài khoản tồn tại/active/đã kích hoạt, không phải system, tokenVersion khớp principal đã xác minh; không tin authorities hoặc role giữ trong phiên cũ. Projection chỉ có ID/trạng thái/version/role/mã quyền, không tải password hash, email hoặc token. Không cache kết quả trong RAM hay dùng `Permission.values()` làm quyền người dùng.

`authenticated` cho phép tài khoản cần đổi mật khẩu dùng luồng tự phục vụ; các hàm kiểm quyền/ADMIN từ chối must_change_password. Bộ lọc AUTH011 tiếp tục giới hạn HTTP cho những phiên này. Interceptor làm mới Authentication từ cùng projection trước khi kiểm `AccessFunction`, policy, capabilities và giới hạn ủy quyền; method security đọc DB lại tại controller/service, kể cả lời gọi bean nội bộ không đi qua HTTP. Bảo vệ ADMIN, phạm vi dữ liệu, audit/version/transaction và registry sync vẫn giữ nguyên. UI ẩn nút không thay kiểm backend.

DB mất kết nối trả COM007/503 qua mapper chung, không fallback về quyền cũ và không thực hiện method bị chặn. Thiếu quyền trả 403. Mỗi lần gọi hàm thực hiện một SELECT và trả connection trước nghiệp vụ; một request có thể có nhiều lần kiểm, bên cạnh truy vấn xác thực/policy hiện có. Đây là chi phí chủ động để kiểm quyền hiện hành, chưa có bằng chứng tải production. Thu hồi có hiệu lực ở lần kiểm DB tiếp theo; không hủy transaction đang chạy hoặc hứa serialization giữa việc thu hồi và thao tác đã qua kiểm.

Không cần migration/khóa JWT mới. Cần nâng các node trước khi tuyên bố toàn hệ thống dùng guard DB này. Regression `DatabaseAccessGuardTest` dùng state độc lập authorities, kiểm ANY/ALL/mã tùy chỉnh, thu hồi, trạng thái, tokenVersion, hai context WAR, URL trực tiếp và lỗi DB ngay trong PreAuthorize. CI `verify-database-guard.sh` gọi class production với PostgreSQL Docker tạm, cố tình giữ Authentication cũ khi đổi mapping/trạng thái. Bằng chứng chạy/merge nằm ở tiến độ/PR; không thử ghi trên Neon.

## Guard chung theo metadata hàm — ACCESS08

Theo yêu cầu 24/10/2026, bỏ lớp AccessRules và các biểu thức riêng theo CATALOG_UPDATE, MEDIA_DELETE... Mỗi service có thể khai báo tài nguyên một lần, mỗi hàm chỉ đánh dấu hành động. Ví dụ từ CatalogService:

```java
@Service
@AccessResource(Groups.CATALOG)
public class CatalogService {
    @AccessOperation(action = PermissionAction.DELETE)
    public CatalogResponse delete(...) { ... }
}
```

`AccessOperation` là meta-annotation của một `@PreAuthorize` dùng chung. AccessMethodExpressionHandler ở cả root/servlet context đưa MethodInvocation thật vào `accessGuard.operation`. Resolver lấy tài nguyên/action từ method/class server, sinh `CATALOG_DELETE`, rồi guard kiểm mapping active và account/tokenVersion trong DB. Module mới không cần thêm hằng AccessRules hoặc Permission.values(). Tài nguyên/hành động mô tả hợp đồng của hàm; không thể suy ra chính xác nghiệp vụ từ tên Java hay HTTP verb và không nhận chúng từ payload/header người dùng.

HTTP dùng `AccessFunction(actions = { PermissionAction.DELETE })`, resource mặc định là group hoặc chỉ định riêng khi tên nhóm khác tài nguyên. Guard HTTP, menu/nút, danh bạ và service dùng cùng quy tắc RESOURCE_ACTION. Thu thập startup kiểm cả method guard trên service, không gọi business để đăng ký quyền; ACCESS_SYNC bật mới ghi registry + mapping ADMIN trong cùng transaction/lock hiện có. Không cấp role thường hoặc reset mật khẩu. Metadata function/menu mới vẫn cần đăng ký đường dẫn/nhãn trong AccessFeatures và vi/en; không phải khai báo lại một biểu thức bảo mật cho từng quyền.

`aliases` chỉ giữ mã tương thích cũ như CATALOG_WRITE; `required` là điều kiện bổ sung ALL, ví dụ ACCOUNT_CREATE còn cần ACCESS_ASSIGN. `adminOnly` giữ đường cứu hộ/dữ liệu nhạy cảm; `authenticated` dành cho tự phục vụ và không thay owner check. Hàm upsert dùng tên tham số `createArgument` đã khai báo trong source: boolean true hoặc ID 0 là CREATE, false/ID dương là UPDATE, ID âm hoặc kiểu sai bị từ chối. Không lưu/thực thi SpEL do DB/client cung cấp.

UI chỉ gợi VIEW nếu người gán có quyền gán VIEW đó; trường hợp thiếu VIEW giữ nguyên hành động API và hiện giải thích vi/en. Chọn cả nhánh/bộ hệ thống cũng qua cùng kiểm tra, không thêm quyền ngoài actor và không nới service. Đây là sửa lỗi được tái hiện ở ACCESS07. Lịch sử trước–sau, picker/quota registry, scope và benchmark là các mục triển khai tiếp, không được coi hoàn thành bởi thay annotation.

## Lịch sử quản trị quyền — ACCESS09

`auth_access_changes` (V014) lưu các thay đổi qua API quản trị của app: tạo/sửa/xóa role, tạo/sửa permission, tạo/sửa/xóa policy, thay quyền của role, thay role của user, cấp và khóa/mở tài khoản. Khóa ngoại actor/audit trỏ auth_users; không xóa user để dọn lịch sử. Mỗi sự kiện có loại/đối tượng, actor đã xác minh, traceId từ filter, Instant, version và trạng thái whitelist. Username được join theo actor_id khi đọc; không tin user ID từ header/body. Lịch sử chỉ ADMIN + ACCESS_READ ở cả HTTP và service/hai context.

Mutation giữ khóa/version hiện có, đọc trước, ghi thay đổi và gọi AccessChangeService bằng **cùng connection/transaction**. Append/cleanup lỗi làm rollback cả mutation; stale version hoặc từ chối trước mutation không sinh sự kiện thành công giả. Không tạo transaction audit độc lập hoặc queue RAM. Mapping chỉ lưu chính xác mã bị bỏ trong before, mã thêm trong after, tổng mapping và version trước/sau; quyền không đổi không bị nhân bản. Xóa role giữ cả mapping sắp dọn. Policy giữ target template và cấu hình/mapping trước/sau; không lưu raw HTTP hay URL người dùng. Cấp tài khoản chỉ ghi role, trạng thái và ID, không password/hash/email/token/challenge.

Cấu hình `VIETROUTE_ACCESS_AUDIT_RETENTION_DAYS` mặc định 7 (1–90), `VIETROUTE_ACCESS_AUDIT_CAPACITY` mặc định 1000 (100–100000). Query luôn loại dòng quá hạn; append và SYSTEM_CLEANUP dọn tối đa 100 dòng/lượt với advisory transaction lock riêng. Capacity là mục tiêu dọn dần, không ràng buộc cứng đúng 1000 trong burst. Không có hoạt động/job thì dữ liệu quá hạn có thể còn vật lý nhưng không trả ở API. Tăng capacity cần cân đối Neon Free và index/storage.

Đây là lịch sử ứng dụng có retention: không bất biến trước quản trị DB, không ghi đầy đủ SQL trực tiếp/bootstrap/node cũ hoặc dựng lại lịch sử trước V014. Nhật ký request thất bại vẫn tra bằng traceId ở journal; không suy rằng mọi request đều có sự kiện đổi quyền. Khi nâng, đồng nhất các node V014 trước khi xác nhận mọi mutation đều có lịch sử. Không đổi/migrate Neon để kiểm build; bằng chứng SQL cách ly ở CI/tiến độ.

## Tìm role và quota registry — ACCESS10

Màn gán dùng tên vi/en kèm mã bất biến, tìm theo tên/mã và hỗ trợ gõ tiếng Việt không dấu. Lọc chỉ ẩn label/option, không xóa checkbox đang chọn hoặc tự thêm quyền; role đang chọn vẫn được giữ trong select để người dùng không vô tình đổi đối tượng. Bản nháp được backend kiểm lại khi lưu. Khu registry dùng API phân trang PostgreSQL với từ khóa/trạng thái; allocated là tổng mã chưa xóa, độc lập kết quả lọc.

`VIETROUTE_ACCESS_REGISTRY_CAPACITY` mặc định 512, khoảng 64–2048 cho mỗi loại role/permission; `VIETROUTE_ACCESS_POLICY_CAPACITY` mặc định 256, khoảng 16–1024. Ngưỡng được kiểm trong transaction dưới cùng advisory lock gán/cập nhật, không đọc quota ở UI rồi tin kết quả. Role tắt vẫn chiếm suất; tombstone role không chiếm suất nhưng mã cũ vẫn không tái sử dụng/bootstrap hồi sinh. Hạ quota dưới dữ liệu có sẵn không tự xóa/tắt mã; chỉ chặn tạo mới. Đạt quota trả COM016/409 kèm giới hạn đã dịch, không báo trùng mã chung chung.

API danh sách tương thích không âm thầm cắt ở 512: đọc đủ trong trần an toàn 2048/1024 và báo lỗi nếu DB bị ghi trực tiếp vượt trần, thay vì bỏ điều kiện bảo vệ hoặc để quyền vô hình. API registry mới phân trang tối đa 100 dòng; role picker hiện vẫn tải metadata/mapping hữu hạn, không tuyên bố đã virtualize cho hàng trăm nghìn role. Giới hạn 512 permission/role và 32 role/user giữ nguyên hợp đồng. Startup sync metadata/ADMIN có thể làm tổng mã vượt quota tạo thủ công; cần theo dõi và tăng ngưỡng sau kiểm dung lượng, không tự cấp quyền cho role thường.

## Chi phí kiểm quyền — ACCESS11

`UserRepository.findById(Long)` phục vụ JWT filter/me/preview dùng projection tường minh, không password_hash/bộ đếm đăng nhập và gộp user + tập role/quyền active trong **một statement/snapshot**. Nhánh nhận connection để login/đổi mật khẩu/transaction vẫn đọc credential có chủ đích và mapping riêng; không dùng projection thiếu hash để xác minh mật khẩu. AccountAccessSql dùng chung subquery mảng, loại role tắt/xóa và permission tắt, giải phóng SQL array tại biên JDBC. AuthorizationRepository trả một hàng/user thay lặp cột trạng thái theo từng permission; quyền và tokenVersion vẫn lấy DB hiện hành.

Mỗi lần mượn connection vẫn có câu set_config search_path bắt buộc. Vì vậy một lần đọc projection gồm 2 prepared statements (search_path + SELECT) so với nhánh credential 3 (search_path + user + mapping). Một request còn có interceptor refresh, service guards, policy và nghiệp vụ; không được quảng cáo mọi request chỉ có một query. Không thêm cache authority hoặc bỏ guard để giảm số đo. Thu hồi/mất DB/hai context tiếp tục dùng regression có sẵn.

Bộ CI AccessCostInspector đo production DAO với 4 worker × 25 lượt, mỗi lượt đọc user rồi DB guard, pool riêng 2; kiểm số statement, quyền giống nhánh credential và hash không được tải. access-load.py chỉ gọi hai cổng loopback CI, 4 worker × 20 GET luân phiên resource page/registry search trên hai node; báo p95/p99/rps/lỗi. Ngưỡng CI rộng để phát hiện hồi quy (JDBC p95 <= 2000ms, HTTP p95 <= 2500ms, 0 lỗi), không phải SLA hoặc mức tải tối đa. Metrics borrow/acquire sau workload gồm cả hoạt động khác của app; số đo query helper tách biệt workload HTTP. Không có benchmark Neon Free/Render, provider hoặc Internet từ bài kiểm này. Kết quả thực nằm tiến độ/PR, không ghi số dự đoán vào đây.

## Nền phạm vi dữ liệu — SCOPE01

Ba bảng V015 thuộc auth, không tạo bảng nhà xe/chuyến/đơn hoặc tự gán membership cho OPERATOR/DRIVER cũ:

| Bảng | Quan hệ và trách nhiệm |
|---|---|
| auth_data_scopes | ID server cấp, code duy nhất bất biến, tên vi/en, active bảo mật, audit/version. CARRIER01 sẽ tham chiếu scope_id duy nhất; đây chưa phải hồ sơ nhà xe |
| auth_scope_memberships | PK(scope_id,user_id), trạng thái/audit/version, FK scope/user; không system. Một user tối đa 32 membership kể cả tắt, không lấy scope hiện hành từ cookie/JWT client |
| auth_scope_roles | PK(scope_id,user_id,role_code), FK membership và (user_id,role_code) của auth_user_roles; không ADMIN. Role scoped phải thuộc chính user toàn cục, thu hồi global role dọn mapping bằng FK |

Role vẫn là nhóm quyền, không thêm tầng group trùng lặp. ADMIN quản lý metadata/membership bằng ACCESS_READ/CREATE/UPDATE/ASSIGN cùng ràng buộc ADMIN-only; không ủy quyền quản trị scope cho ordinary role chỉ vì có ACCESS_ASSIGN. User phải được cấp role toàn cục trước; từng membership chỉ chọn tập con role của user đó. Cùng user có role quản lý tại A và role đọc tại B thì quyền quản lý A không tham gia guard/capabilities B. Không gán system/ADMIN/self thành membership. Tài khoản mật khẩu tạm có thể được chuẩn bị membership nhưng chưa thao tác cho tới đổi mật khẩu; account đang tắt/chờ kích hoạt chỉ có thể thu hồi/tắt membership, không kích hoạt quyền mới.

DataScopeGuard.require dùng connection của transaction/DAO, principal server đã xác minh và **một snapshot DB** kiểm user/tokenVersion/active/activation/must_change, scope active, membership active, scoped role thuộc user toàn cục và role/permission active đúng RESOURCE_ACTION. Không tin ROLE_ADMIN trong Authentication cũ; ADMIN chỉ cứu hộ khi DB hiện hành xác nhận ADMIN active và scope vẫn bật. AccessOperation chức năng/policy vẫn bắt buộc. DAO business phải bind cả ID lẫn scope_id, kiểm quan hệ cha/con đã đọc DB và owner/tài xế được phân công; guard không tự viết lại SQL hoặc bảo vệ các DAO tương lai chưa tích hợp. Catalog/media nền hiện hữu giữ phạm vi global/owner đã chốt, không tự chuyển sang tenant.

GET auth/scopes chỉ trả membership/scope đang bật có role active của principal; GET auth/scopes/{id}/permissions trả tập permission theo đúng scoped roles, 403 nếu không thuộc scope. UI business phải giao tập này với capabilities/policy chung trước khi hiện nút; guard backend luôn kiểm lại. Không ghi scope claim tự tin vào JWT hoặc cache authority. Khách tra cứu nhà xe/chuyến công khai không cần membership nhân viên và vẫn nhìn đủ lựa chọn; quy tắc một booking/chuyến đang hoạt động ở BOOKING01 là ràng buộc business riêng.

Mutation giữ advisory lock gán chung, optimistic version, audit actor và lịch sử cùng transaction. Membership tạo bằng version 0 và nhận version 1; cập nhật cần version hiện hành. Khi thay global roles, chỉ xóa role thực sự bị thu hồi, giữ timestamp/mapping không đổi. Membership ảnh hưởng được tăng version trước FK cascade và ghi trước–sau riêng trong cùng transaction; JWT user cũng bị thu hồi như hợp đồng cũ. Lỗi lịch sử rollback cả global/scoped mapping/version, không có queue RAM hoặc sự kiện thành công giả.

Scope bảo mật tắt nghĩa là chặn tất cả scoped access. Khác trạng thái nhà xe ngừng nhận booking trong đặc tả (vẫn phải xử lý chuyến/đơn tồn): CARRIER01 không được tự đồng nhất hai cờ này. Không API xóa vật lý scope/membership/user. Quota scope `VIETROUTE_ACCESS_SCOPE_CAPACITY` mặc định 512, khoảng 1–4096; scope tắt vẫn chiếm suất, đạt ngưỡng COM016. Giới hạn membership 32 gồm cả tắt, role/membership tối đa 32; không tuyên bố quy mô vô hạn.

Triển khai V015: drain/nâng toàn bộ node trước khi gán membership hoặc sửa global role. Node cũ xóa/chèn mọi auth_user_roles có thể làm mất scoped mappings qua FK và không ghi version/history scoped; không hỗ trợ mixed-version không gián đoạn. Giữ V001–V014/checksum nguyên vẹn, không chạy migration/test ghi trên Neon để kiểm build. SQL CI dùng ba scope/role chéo, Authentication cố tình cũ, bảng TEMP minh họa predicate ID+scope, thu hồi/stale/rollback. Đây không phải bằng chứng đã có scope trong CRUD nhà xe/chuyến/booking chưa triển khai; CARRIER01/AUTH03/DRIVER01/TRIP01 phải tích hợp trước nghiệm thu business.

## Snapshot trong thân service — ACCESS13

Guard generic không chỉ đọc DB để trả allow/deny: khi cho phép, nó thay Authentication trong SecurityContext bằng chính snapshot vừa kiểm trước khi chạy thân hàm. Điều này bảo vệ lời gọi bean trực tiếp không qua interceptor HTTP, khi code gán quyền/kiểm ủy quyền bên trong đọc principal/authorities từ context. Actor truyền vào còn tập role cũ bị AccessActors từ chối; quyền đã thu hồi không được dùng để gán tiếp. Không đọc DB thêm, không cache authority giữa lần gọi/request/node; guard bị từ chối hoặc lỗi DB vẫn không chạy thân hàm.

Regression dùng AccessService thật trong hai context, DB fixture độc lập chỉ ACCESS_ASSIGN nhưng Authentication cũ chứa ADMIN/CATALOG_DELETE. Transaction fixture ném ngay nếu lọt kiểm, nên tái hiện trước sửa không mở JDBC; sau sửa cả hai trường hợp bị AUTH003 trước transaction. Helper CI gọi proxy bằng PostgreSQL thật còn kiểm ngay trong thân bean không giữ ADMIN/BOOKING_DETAIL giả. HTTP đã refresh ở interceptor; thay đổi này khép cùng hợp đồng cho lời gọi service nội bộ, không thay quyền DB hoặc JWT.

## Cứu hộ tài khoản bootstrap — ACCESS15

Username quản trị gốc lấy duy nhất từ VIETROUTE_BOOTSTRAP_ADMIN_USERNAME (mặc định admin). Đây là danh tính cấu hình server, không phải mọi user đang giữ role ADMIN. Đăng ký public không được chiếm tên này (giữ phản hồi chung); quản trị không tạo thay hoặc gỡ ADMIN khỏi account gốc, kể cả còn admin khác. Đổi username cấu hình là thay danh tính cứu hộ và phải là quyết định vận hành có kiểm soát.

Khi DB_INIT bật, BootstrapAdminInitializer chạy trong transaction migration/seed. Khi ACCESS_SYNC bật, collector servlet đã validate gọi cùng initializer và sync toàn bộ định nghĩa HTTP/service/function. Tạo account nếu thiếu bằng mật khẩu bootstrap hợp lệ; nếu đã tồn tại thì giữ hash/lịch sử mật khẩu, avatar và khóa chống dò. AUTH17 chuẩn hóa riêng bootstrap thành active, không pending, không bắt đổi mật khẩu tạm, email cấu hình được cấp sẵn trạng thái đã xác minh. Sửa/tạo role ADMIN, đăng ký/bật lại permission khai báo trong source, gán ADMIN cho account bootstrap và đủ mọi permission active cho ADMIN. Quyền source mới không cần thêm hằng AccessRules. Không phục hồi quyền đã gỡ khỏi role thường, không hồi sinh tombstone role thường, không tự bật custom permission đã tắt. Bật lại permission lõi có hiệu lực với mọi role đang giữ mapping đó; đây là sửa registry chung, không cấp mapping mới cho role thường. Cùng advisory lock gán quyền, idempotent mapping và audit system=0; không chạy trong Maven/Docker packaging hoặc migration/check ghi thử trên Neon.

Trang /account hiển thị nút Khôi phục quản trị gốc chỉ cho username bootstrap đã đăng nhập, độc lập ACCESS_VIEW để dùng khi mất mọi quyền. POST auth/admin-recovery không nhận username/role/permission, dùng JWT/principal và đọc lại ID/username/account/tokenVersion từ DB dưới khóa. Chỉ account active, không pending, đã đổi mật khẩu tạm và hết khóa đăng nhập mới được dùng. Yêu cầu mật khẩu hiện tại, CSRF cookie, quota/semaphore credential. Sai mật khẩu commit bộ đếm, khóa 15 phút sau 5 lần; không dùng mật khẩu bootstrap cấu hình như mật khẩu thay thế cho account đã có.

Khôi phục thủ công dùng metadata server của servlet hiện hành, sửa registry/mapping và ghi ADMIN_RECOVERED cùng transaction; lỗi history rollback cả quyền/role/tokenVersion. Snapshot chỉ mã role/permission và trạng thái role ADMIN, không hash/token/password/email. Sau thành công thu hồi mọi JWT và đăng nhập lại. Không bypass guard DB thông thường hoặc mở endpoint qua cờ public. ADMIN khác không được thấy nút/gọi API; họ vẫn giữ đầy đủ quyền quản trị thông thường theo DB.

Giới hạn: đây là phục hồi bản ghi trong schema hợp lệ, không tạo lại bảng/migration history bị phá, không khôi phục dữ liệu nghiệp vụ hay account bị xóa còn FK. Account bị khóa chống dò vẫn phải chờ/quên mật khẩu. Riêng trạng thái active/pending/mật khẩu tạm của bootstrap được AUTH17 sửa về sẵn sàng khi init/sync bật. Nếu account gốc không tồn tại thì không có phiên để bấm nút: cấu hình bootstrap password hợp lệ và khởi động với DB_INIT hoặc ACCESS_SYNC sau khi schema sẵn sàng để tạo lại. Nếu hai cờ đều tắt thì không ghi tự sửa lúc startup. Tài khoản thường vẫn đổi mật khẩu tạm và xác minh email; bootstrap được cấp sẵn theo AUTH17, không có bypass quyền runtime. Startup repair là lifecycle, không ghi giả thành sự kiện người dùng trong auth_access_changes. Bằng chứng thực ở tiến độ/PR.

Đường /account cũng nằm ngoài target policy quản trị như auth/access: policy MENU cũ từ chối GET /account không được áp dụng, tránh khóa UI khôi phục sau khi account gốc mất ADMIN. Không cho tạo/sửa policy mới nhắm vào trang này; policy cũ có thể xóa qua quản trị. Trang vẫn yêu cầu xác thực, API hồ sơ và recovery vẫn giữ guard/owner/CSRF riêng. Regression hai context tái hiện trang bị chặn trước sửa rồi đạt sau sửa; CI PostgreSQL kiểm policy cũ cùng tình huống mất role. Không mở các trang/API quản trị khác theo ngoại lệ này.

## Tài khoản gốc sẵn sàng và xác minh email — AUTH17

Theo yêu cầu chủ dự án ngày 24/10/2026, chỉ danh tính cấu hình VIETROUTE_BOOTSTRAP_ADMIN_USERNAME (hiện admin) được cấp sẵn email từ VIETROUTE_BOOTSTRAP_ADMIN_EMAIL; fallback bootstrap.admin.email trong src/main/resources/bootstrap.properties là hieuv526034@gmail.com. Đây là xác nhận vận hành của chủ hệ thống, không phải bằng chứng đã gửi/nhận Gmail. Không lấy role ADMIN làm điều kiện miễn xác thực. Email này không phải cấu hình SMTP/Gmail OAuth. Đổi email gốc qua cấu hình rồi restart với init/sync, không qua form gắn email cá nhân; trùng email của user khác thì transaction thất bại, không chiếm email của họ.

Initializer dùng khóa gán quyền rồi khóa dòng account, tạo lại nếu thiếu, chuẩn hóa email/verified/active/pending/must-change chỉ cho bootstrap, sửa ADMIN/registry/mapping như ACCESS15. Giữ nguyên hash, lịch sử mật khẩu và khóa đăng nhập sai; thay đổi trạng thái/email làm tăng tokenVersion, xóa challenge cũ và buộc đăng nhập lại một lần. Lặp init không thay version/thời điểm xác minh khi dữ liệu đã đúng. Startup không ghi sự kiện người dùng giả; nút recovery vẫn xác nhận mật khẩu/CSRF và ghi history nguyên tử. Không tự sửa khi cả DB_INIT và ACCESS_SYNC tắt, không phục hồi schema bị hỏng hoặc backup nghiệp vụ.

Mọi tài khoản còn lại, kể cả hieuboy được gán ADMIN, phải đổi mật khẩu tạm nếu có và xác minh email. Filter HTTP bỏ authority/redirect account hoặc trả AUTH012; service guard và scope SQL cũng kiểm email_verified_at trực tiếp trong DB. Không chỉ ẩn menu. Phiên chưa hoàn tất vẫn đọc hồ sơ, đổi mật khẩu, gắn email, kích hoạt và đăng xuất được; không có quyền chức năng riêng tư. Preview user đích cũng áp dụng điều kiện này. Việc gắn email mới lập tức làm mất quyền cho đến xác minh; email/challenge/outbox giữ cơ chế một lần và mã hóa cũ.

Triển khai: drain/nâng tất cả node cùng phiên bản và cùng cấu hình bootstrap. Account cũ chưa xác minh (ngoài bootstrap) cũng bị chặn quyền riêng tư sau nâng; cần bật AUTH_SELF_SERVICE cùng mail/worker/provider hợp lệ để họ hoàn tất. Không được coi SMTP loopback CI hoặc email cấp sẵn là mail đã hoạt động ngoài app. Tái sử dụng V011, không đổi V001–V015, không cần migration mới.

## Rà soát user và quyền hiệu lực — AUTH18

Nguồn quyền GET access/users/{id}/effective xét cùng điều kiện sẵn sàng như preview quản trị: active, không pending, không mật khẩu tạm và email đã xác minh. Mapping chưa bị xóa nhưng nguồn hiệu lực rỗng khi tài khoản bị hạn chế; active vẫn phản ánh cờ DB, không đổi nghĩa thành ready. ManagedAccountResponse chỉ thêm boolean emailVerificationRequired, không công bố email; danh sách và chi tiết UI nêu lý do thiếu chức năng.

Các service tự phục vụ me, đổi mật khẩu, đăng xuất có principal, hồ sơ và gắn email đối chiếu actor với SecurityContext vừa được guard refresh trước khi đọc/ghi. HTTP vẫn lấy principal server; kiểm này bảo vệ cả caller nội bộ truyền nhầm principal người khác. Token/role thu hồi ở DB không fallback authority cũ; recovery/ADMIN/system và scope vẫn giữ giới hạn trước đó.

Rà soát chưa phát hiện đường bỏ kiểm quyền DB trong các ca hiện có: nhiều role, hành động riêng theo tài nguyên, VIEW độc lập, policy chỉ siết, không tự cấp quyền cho role thường, thu hồi liên node ở lần kiểm tiếp theo, bảo vệ ADMIN/system/bootstrap và rollback lịch sử. Không coi đây là kiểm thử xâm nhập toàn diện hoặc chứng minh phạm vi business đã hoàn thành. Nguồn quyền là mapping quyền chung; menu/thao tác cuối cùng vẫn dùng capabilities kết hợp policy/guard, scope còn phải gắn vào DAO nghiệp vụ.

## Schema cùng transaction trên Neon pooler — AUTH19

JdbcConnectionFactory.open tắt autocommit trước khi JdbcSchema.select gọi set_config(search_path, ..., true). Schema được chọn LOCAL, ghép cấu hình đã whitelist và pg_temp ở cuối, không public/$user. Toàn bộ truy vấn/callback dùng cùng transaction và backend; commit/rollback không để path rò sang caller khác. Không đổi schema cứng trong DAO hoặc migration V001–V015. Không được setAutoCommit(true) rồi tiếp tục dùng connection đã mượn.

BaseRepository dùng chung JdbcTransactions cho callback độc lập: thành công commit, SQLException/RuntimeException/Error rollback, close trả connection về pool. Service vẫn truyền connection hiện hành cho DAO trong transaction để không tạo transaction con. Callback DAO độc lập có ghi dữ liệu (như claim worker) cũng được commit đúng. Journal append và hàm ghi activity commit tường minh; các thao tác đọc trực tiếp chỉ đóng pool để rollback transaction còn mở, không chờ HTTP hoặc gọi provider trong lúc giữ connection. Factory chọn schema lỗi rollback/close, không mất nguyên nhân SQL gốc.

Lý do: [Neon transaction pooling](https://neon.com/docs/connect/connection-pooling) không bảo đảm session state giữa transaction; đặt search_path ở autocommit rồi mới truy vấn có thể dùng backend khác. Đây là lỗi tương thích thiết kế xác định từ code và hợp đồng provider, chưa phải kết luận nguyên nhân trace 503 cũ đã mất.

CI bổ sung PgBouncer bên trong container PostgreSQL tạm, transaction mode và DISCARD ALL sau mỗi transaction. Helper chứng minh cách đặt path cũ bị reset, rồi kiểm schema LOCAL, commit callback, rollback lỗi, pg_temp không che bảng và node ứng dụng login/guard/journal qua pooler. Chỉ fixture CI, không thêm PgBouncer/Docker service vào app hoặc Render, không thử sửa schema/mutation trên Neon.


## Áp dụng scope và quyền danh mục vào nghiệp vụ — BUSINESS02

V017 bổ sung `carriers` (scope duy nhất), `carrier_vehicles`, `carrier_drivers`. Hai bảng con giữ FK `(carrier_id,scope_id)` và định danh `(id,carrier_id,scope_id)` để bước tuyến/chuyến sau này ràng buộc cùng nhà xe; vehicle type dùng FK `(vehicle_type_id,vehicle_type)` vào catalog. Không thêm tầng nhóm quyền mới. `auth_data_scopes/auth_scope_memberships/auth_scope_roles` tiếp tục là nguồn scope, global roles vẫn là trần. Registry/function ADMIN sync khi enabled; không tự cấp quyền CARRIER/VEHICLE/DRIVER cho role thường.

Các mã `VEHICLE_TYPE_*`, `BOOKING_SOURCE_*`, `PROVINCE_*` cho từng danh mục. Alias CATALOG/LOCATION cũ chỉ giữ tác vụ trước đây, không mở sang nhà xe hoặc nhóm mới. CATALOG là nhóm menu có các menu con, `/admin` giữ VIEW cũ để tương thích trang tổng quan. Danh mục tỉnh/địa điểm vẫn ADMIN-only; quyền riêng cho phép tách cấu hình trong tập quản trị. VIEW không thay READ/API, policy bổ sung không thể bỏ service guard.

`DataScopeGuard.visibleSql/bindVisible` lọc SQL cả count/trang/ID từ alias owner do DAO lấy trong DB; đọc lại account/tokenVersion và membership/role/perms, không trust carrierId filter hoặc authority client. CREATE/UPDATE dùng cùng connection và parent lock; ADMIN recovery vẫn cần DB ADMIN + scope active. Trạng thái kinh doanh carrier inactive chặn gán mới/bật xe/tài xế nhưng giữ scope active để có thể ngừng sử dụng/xử lý dữ liệu cũ. Account DRIVER phải ready và có membership DRIVER đúng scope khi liên kết; hồ sơ chưa liên kết không có quyền đăng nhập. Thu hồi account/membership vẫn kiểm lại ở mỗi thao tác tiếp theo.

Hợp đồng route/payload/DEMO và kiểm cô lập ở [API base](API_BASE.md#danh-mục-riêng-và-nhà-xephương-tiệntài-xế--business02). Phần scope này chưa thay các row guard/lock/snapshot/tồn chỗ bắt buộc của tuyến/chuyến/booking tương lai. Chưa có quyền xóa cứng các hồ sơ kinh doanh.

## Tuyến và chuyến trong phạm vi nhà xe — ROUTE01/TRIP01

V018 thêm `carrier_routes`/`carrier_route_stops`; V019 thêm `carrier_trips`/`trip_stops`/`trip_segments`/`trip_events`. FK composite giữ đúng parent/carrier/scope; schema lấy từ connection cấu hình, migration mới không ghi prefix. Tuyến là mẫu một chiều; điểm chuyến là snapshot độc lập và sự kiện ghi cùng transaction. Không thêm tầng role/group hay quyền toàn cục bỏ qua scope.

TRIP_VIEW/READ/CREATE/UPDATE là tài nguyên riêng, không dùng ROUTE/VEHICLE/DRIVER_READ làm quyền phụ thuộc ẩn. Picker tạo/phân công sử dụng chính action TRIP trong scope; ID chuyến dương được kiểm trước khi ép carrier cho picker. VIEW/API tách, quyền scoped và global cùng bắt buộc. Registry/ADMIN sync khi enabled, mapping role thường cần quản trị lưu qua API/UI; thêm source không tự cấp cho OPERATOR/DRIVER/CUSTOMER.

Hợp đồng payload, khóa lịch và snapshot ở [API base](API_BASE.md#điều-hành-chuyến--trip01-trong-business04). BOOKING01 tích hợp xác nhận/hủy trong cùng transaction theo mục dưới.

## Booking theo owner/scope và projection công khai — V020

`bookings` lưu đơn WEB/PHONE, owner nullable chỉ cho PHONE, trip/carrier/scope composite FK, hai stop snapshot, số chỗ, giá đã chốt, contact, trạng thái, UUID, tiền đã thu/hoàn và audit/version. `booking_events` giữ sự kiện chuyển trạng thái/thu/hoàn và số tiền, FK composite về booking. Partial unique WEB PENDING/CONFIRMED theo customer; UUID tạo và UUID sự kiện tiền có unique riêng. Không thêm group hoặc bảng quyền trùng; MY_BOOKING_VIEW/READ/CREATE/UPDATE và BOOKING_VIEW/READ/CREATE/UPDATE là mapping role-permission thông thường, không tự cấp ordinary roles.

MY_BOOKING yêu cầu CUSTOMER ready hiện hành và owner từ principal, kể cả ADMIN không có CUSTOMER không được mượn owner người khác. BOOKING dùng DataScopeGuard trên connection hiện hành, đồng thời SQL count/trang/ID gắn đúng scope; passenger API giữ assigned driver và chỉ projection cần đón/trả. Lookup chuyến công khai tách DTO khỏi booking/driver/user entity và không yêu cầu staff membership. V020 giữ migrations trước nguyên vẹn; bootstrap chỉ tạo schema khi enabled, không seed đơn/tiền hay cấp quyền thường trong build. Hợp đồng khóa, giá, chống lặp và cascade tại [API base](API_BASE.md#đặt-chỗ-theo-chặng-và-tìm-công-khai--booking01search01).

REPORT01 thêm chức năng Báo cáo vận hành trong nhóm Nghiệp vụ, hai quyền REPORT_VIEW/READ suy từ annotation và registry/ADMIN sync khi bật. Không migration hoặc auto-grant role thường. REPORT_READ scoped được áp dụng vào SQL tổng hợp và picker riêng, không yêu cầu CARRIER/TRIP/BOOKING_READ; VIEW chỉ mở trang. Cùng guard current account/tokenVersion/membership/role/permission; ID carrier ngoài scope trả 404. Dữ liệu response tổng hợp không chứa PII; hợp đồng khoảng ngày/tiền ở API_BASE.

AUTH03 bổ sung DRIVER_ASSIGN cho thao tác cấp một account DRIVER mới và membership cùng hồ sơ đúng nhà xe. Không cấp ACCOUNT_CREATE/ACCESS_ASSIGN hoặc quyền gán role tùy ý cho OPERATOR. Role DRIVER phải nằm trong trần nghiệp vụ DRIVER_TRIP_VIEW/READ/UPDATE (gồm cả mapping tắt), nếu mạnh hơn chặn DRIVER001; ADMIN có thể dùng quy trình quản trị account riêng khi cần. Cả account/link/membership/lịch sử cùng transaction, version và khóa cha; không đổi ngoại lệ AUTH011/AUTH17. Chi tiết API/thiết lập account ở API_BASE.

TRACK01 giữ quyền chức năng hiện có DRIVER_TRIP_READ/UPDATE, TRIP_READ, MY_BOOKING_READ và thêm kiểm consent/assigned/owner CONFIRMED ở backend, không tự grant role thường. GPS không là lookup công khai. V021 thêm trip_tracking/trip_locations riêng dữ liệu nghiệp vụ, không là bảng role/permission. Source/API chưa là bằng chứng đã lấy GPS vật lý; Maps chưa cấu hình, không mở provider hoặc nạp tọa độ thiết bị giả trên Neon.
