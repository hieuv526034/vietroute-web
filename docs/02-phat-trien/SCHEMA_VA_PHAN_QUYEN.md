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

Khi thêm module: khai báo constants/function trong AccessFeatures → mapping + Operation + AccessFunction → guard service AccessRules và scope → startup validate/sync → gán role trong cây. Header tự lọc theo VIEW; quản trị tạo policy nếu cần siết riêng endpoint. Không dùng danh bạ như API gateway hoặc cơ chế tự cấp quyền cho code mới chưa có guard.

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

`AccessPolicyInterceptor` kiểm annotation trên HandlerMethod thật trước controller, rồi áp policy siết thêm. `AccessRules` gom biểu thức PreAuthorize dùng cùng constants cho service/page; giữ method security ở cả root/servlet context, bảo vệ cả lời gọi bean không qua HTTP. Không parse chuỗi SpEL để sinh quyền. Chuỗi đầy đủ: JWT/trạng thái/tokenVersion → authorities DB → annotation/policy HTTP → guard service → scope/version. Chưa đăng nhập trả 401 hoặc redirect trang login; thiếu quyền trả 403.

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

## Phân tích cây chức năng và quyền hành động — ACCESS02

Thiết kế ban đầu ở [Issue #46](https://github.com/hieuv526034/vietroute-web/issues/46)/PR #47 đã được triển khai theo hợp đồng ACCESS03 phía trên. Trạng thái và bằng chứng nghiệm thu luôn xem tiến độ/PR, không coi bản phân tích cũ là kết quả chạy thật.
