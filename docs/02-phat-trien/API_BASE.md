# Hợp đồng API nền tảng

## API auth hiện hành

Đối chiếu source ngày 02/11/2026. Đối chiếu ba controller [UserAPI](../../src/main/java/vn/vietroute/auth/controller/api/UserAPI.java), [RoleAPI](../../src/main/java/vn/vietroute/auth/controller/api/RoleAPI.java), [FunctionAPI](../../src/main/java/vn/vietroute/auth/controller/api/FunctionAPI.java). Source có **36 mapping REST auth**, không tính page controller. Swagger /docs và JSON /v3/api-docs tạo từ MVC/@Operation/@Schema là hợp đồng để thử trên đúng phiên bản app đang chạy. [Schema/phân quyền](SCHEMA_VA_PHAN_QUYEN.md) giải thích cơ chế; [quy ước](QUY_UOC_PHAT_TRIEN.md#auth-gọn-40-class-tám-bảng) liệt kê 40 class.

Tất cả URL dưới đây là khi WAR ở ROOT; thêm context khi deploy /vietroute. API private kiểm đúng method + template từ DB, không suy quyền từ tên ADMIN/OPERATOR/DRIVER/CUSTOMER. Public/self-service là phân loại ứng dụng; self-service vẫn phải có tài khoản/token hợp lệ. Actor, ID chủ sở hữu, audit và quyền hiệu lực lấy từ server.

### Phiên đăng nhập và hồ sơ — UserAPI

| Method | URL | Input chính | Data khi thành công |
|---|---|---|---|
| GET | /api/v1/auth/csrf | Public | headerName, token; cookie CSRF tương ứng |
| POST | /api/v1/auth/login | username, password; không dùng email | UserDTO wrapper có accessToken/tokenType/expiresAt/user; cookie HttpOnly |
| GET | /api/v1/auth/me | Self-service | UserDTO tài khoản hiện tại |
| POST | /api/v1/auth/change-password | currentPassword, newPassword; self-service | null; thu hồi mọi token và xóa cookie, đăng nhập lại |
| POST | /api/v1/auth/logout | Public, có principal thì thu hồi token | null; xóa cookie |
| PUT | /api/v1/auth/profile | displayName, version; self-service | UserDTO với tên hiển thị đã cập nhật |
| GET | /api/v1/auth/capabilities | Self-service | List FunctionDTO của chính user, targets/menu đã lọc trạng thái |

Login sai 5 lần khóa 15 phút; khóa tạm chặn xác minh password, không tự đổi password hoặc thay cho active=false. Password mới ít nhất 8 ký tự, có hoa/thường/số/ký tự khác, tối đa 72 byte UTF-8; không trùng hash hiện tại và hai hash trước. Thời hạn JWT 30 phút, HS256 đúng issuer/audience/exp/iat/jti, chưa refresh token. TokenVersion thay đổi thu hồi token trên mọi thiết bị.

### Quản trị tài khoản — UserAPI

| Method | URL | Input chính | Data |
|---|---|---|---|
| GET | /api/v1/admin/accounts | UserSearch | PageResponse UserDTO |
| POST | /api/v1/admin/accounts | username/displayName/password/roles; email tùy chọn | UserDTO mới, HTTP 201 |
| GET | /api/v1/admin/accounts/{id} | id>0 | UserDTO |
| GET | /api/v1/admin/accounts/{id}/access | id tài khoản đích | UserDTO wrapper user + capabilities của đích |
| PUT | /api/v1/admin/accounts/{id} | displayName, version | UserDTO; không đổi username/email/password |
| PUT | /api/v1/admin/accounts/{id}/state | active, version | UserDTO; thu hồi token/challenge nguyên tử |
| DELETE | /api/v1/admin/accounts/{id} | query version | null; soft-delete, thu hồi mappings/token/challenge |

Account quản trị tạo active/verified nhưng bắt đổi password khởi tạo. Default accounts theo JSON/env là trường hợp cấp sẵn. Endpoint permission chưa đủ để sửa system/self/bootstrap/account nhận protected role: service còn bảo vệ trạng thái và trần ủy quyền. Preview dùng ID đích, không dùng quyền của admin để giả kết quả đích.

### Vai trò và gán chức năng — RoleAPI

| Method | URL | Input chính | Data |
|---|---|---|---|
| GET | /api/v1/admin/access/registry/roles | Không query bắt buộc | List RoleDTO |
| GET | /api/v1/admin/access/registry/roles/page | RoleSearch | PageResponse RoleDTO |
| POST | /api/v1/admin/access/registry/roles/{code} | RoleRequest nameVi/nameEn, active/allScopes tùy chọn | RoleDTO, HTTP 201 |
| PUT | /api/v1/admin/access/registry/roles/{code} | nameVi/nameEn/version; active/allScopes tùy chọn | RoleDTO |
| DELETE | /api/v1/admin/access/registry/roles/{code} | query version | null; chưa được user tham chiếu và không protected |
| GET | /api/v1/admin/access/roles/{role}/functions | role code | RoleDTO có functionCodes/version |
| PUT | /api/v1/admin/access/roles/{role}/functions | functionCodes, version | RoleDTO; tập rỗng thu hồi |
| GET | /api/v1/admin/access/users | UserSearch | PageResponse UserDTO, dùng tra người để gán |
| PUT | /api/v1/admin/access/users/{id}/roles | UserRequest roles/version | UserDTO; scoped roles bị thu hồi nếu không còn thuộc global |

Role code: chữ hoa, số/gạch dưới, bắt đầu chữ, dài 2–24; source Regex.ROLE_CODE là nguồn chính. Sửa role bỏ active/allScopes sẽ giữ cờ hiện có. Gán function xét trần target hiện hành và all_scopes; không chỉ so mã function. Version của role có thể tăng khi function mà role tham chiếu thay đổi, nên đọc lại trước lưu.

### Phạm vi và membership — RoleAPI

| Method | URL | Input chính | Data |
|---|---|---|---|
| GET | /api/v1/admin/access/scopes | RoleSearch | PageResponse RoleDTO projection scope |
| PUT | /api/v1/admin/access/scopes/{id} | nameVi/nameEn/active/version | RoleDTO projection scope |
| GET | /api/v1/admin/access/scopes/{id}/members | UserSearch.userId/page/size | PageResponse RoleDTO projection membership |
| PUT | /api/v1/admin/access/scopes/{id}/members/{userId} | UserRequest roles/active/version | RoleDTO membership |
| GET | /api/v1/auth/scopes | Self-service | List RoleDTO scope của chính user |
| GET | /api/v1/auth/scopes/{id}/permissions | Self-service; id scope | RoleDTO scopeId/targets, tên URL giữ tương thích, không permission registry |

Không có POST scope độc lập: tạo nhà xe ở CarrierAPI mới tạo phạm vi. Member roles phải thuộc global roles của cùng user. Version của membership rỗng vẫn được giữ; không tự gửi version 0 khi gán lại sau thu hồi. Cờ scope active và trạng thái kinh doanh của nhà xe độc lập.

### Chức năng và danh bạ endpoint — FunctionAPI

| Method | URL | Input chính | Data |
|---|---|---|---|
| GET | /api/v1/admin/access/functions | FunctionSearch | PageResponse FunctionDTO, targets toàn trang tải theo tập |
| GET | /api/v1/admin/access/functions/{code} | code | FunctionDTO đầy đủ targets/version |
| POST | /api/v1/admin/access/functions | FunctionRequest code/nameVi/nameEn/targets | FunctionDTO, HTTP 201 |
| PUT | /api/v1/admin/access/functions/{code} | code body khớp URL, nameVi/nameEn/targets/version | FunctionDTO |
| DELETE | /api/v1/admin/access/functions/{code} | query version | null; function chưa được role tham chiếu |
| GET | /api/v1/admin/access/targets | Không query bắt buộc | List FunctionDTO inventory MVC, gồm cả public/self-service để xem |
| GET | /api/v1/admin/access/resources | FunctionSearch.kind/query/page/size | PageResponse FunctionDTO inventory |

Modal Function gọi **/targets**, không /resource-catalog. Chỉ target có trong inventory, private và thuộc trần actor được gán. FunctionRequest.description/icon/sortOrder/active tùy chọn; sửa bỏ field giữ metadata hiện có. Bộ lọc User/Role/Function dữ liệu dùng DB; inventory chỉ là danh sách MVC đã thu thập trong node, lọc/phân trang trên danh sách đó, không bảng DB mới. Query active không dùng để lọc inventory; kind là MENU hoặc ENDPOINT.

### DTO, query và ví dụ gán quyền

UserDTO/RoleDTO/FunctionDTO nằm trực tiếp trong vn.vietroute.auth.dto. Request ở dto.request, Search ở dto.search; không record hoặc dto.response trong auth. Các DTO dùng theo tài nguyên để giữ 40 class: không phải mọi field dùng ở mọi operation. Bean Validation kiểm hình dạng/độ dài; service kiểm field bắt buộc và nghiệp vụ theo bảng trên. Credential WRITE_ONLY, không trả hash/tokenVersion; client không gửi actor/audit. Unknown JSON field bị từ chối, field đã khai báo chỉ có tác dụng ở operation sử dụng nó.

| Search | Bộ lọc áp dụng | Phân trang |
|---|---|---|
| UserSearch | username prefix literal không wildcard, role, active cho account list; userId cho scope member list | page 0–1000000; size 1–100, mặc định 20 |
| RoleSearch | query mã/tên, active; dùng cả scope list | Như trên |
| FunctionSearch | query/active cho function list; query/kind cho inventory | Như trên |

Ví dụ cấp một nhân viên chỉ xem danh sách xe: tạo Function, tạo Role rồi gán Function cho Role và Role cho User. Target GET /operations/vehicles cho phép mở menu; GET /api/v1/admin/vehicles cho phép tải bảng. Không tự có GET /api/v1/admin/vehicles/{id} hoặc PUT /api/v1/admin/vehicles/{id}. Nhân viên nhà xe cần membership scoped tương ứng để xem đúng dữ liệu.

```json
{
  "code": "VEHICLE_LOOKUP",
  "nameVi": "Tra cứu phương tiện",
  "nameEn": "Vehicle lookup",
  "targets": ["GET /operations/vehicles", "GET /api/v1/admin/vehicles"]
}
```

Body POST Function ở trên. Sau POST Role code VEHICLE_READER với nameVi/nameEn, PUT role/functions nhận `{"functionCodes":["VEHICLE_LOOKUP"],"version":0}`; PUT user/roles nhận `{"roles":["VEHICLE_READER"],"version":<version vừa đọc>}`. Version 0 chỉ minh họa role vừa tạo, không mặc định cho tài nguyên đã đổi. Mọi lệnh trên cần target grant thật của actor và CSRF/cookie hoặc Bearer phù hợp, không chạy ví dụ ghi trên Neon để kiểm build.

### Các luồng đã bỏ và tương thích

Không có controller đăng ký/activate/resend/forgot/reset/email/options, admin-recovery, registry permission/policy/tree cũ, gán role resources/permissions hoặc API history. Một số URL hiện hành như scopes/{id}/permissions giữ tên để tương thích contract; tên URL không tạo thêm bảng permission hoặc cơ chế action. Không gửi password reset qua mail hoặc hứa người dùng cũ tự xác minh email; xử lý chuyển đổi theo [bootstrap](SCHEMA_VA_PHAN_QUYEN.md#khởi-tạo-và-bảo-vệ-bootstrap).

Các page auth trong UserPageController: /login, /account, /operations/accounts, /operations/access, /operations/access/resources, /admin và /docs. /dashboard thuộc business/report. Page private kiểm riêng target GET; query lang không thay grant. /login với principal redirect /dashboard và giữ locale, filter có thể đưa phiên chưa hoàn tất về /account.

## Locale và envelope

Ưu tiên ?lang=vi|en → X-Locale → Accept-Language → vi. Locale tường minh không hỗ trợ trả400. API auth/business kế thừa BaseController, execute trả ApiResult<T>, HTTP body là ApiResponse<T>: success/code/messageKey/message/data/errors/timestamp/path/traceId/locale. PageResponse có content/page/size/totalElements/totalPages/hasNext/hasPrevious. Time là ISO-8601 Instant UTC; actor là server.

Lỗi binding/JSON trước controller qua GlobalExceptionHandler; lỗi trong execute qua ApiExceptionMapper; filter qua ApiErrorWriter. Không bọc lỗi thành HTTP200, không trả SQL/stacktrace/password. Unknown JSON field, key trùng và token dư trả400. Header X-Request-ID dùng tra traceId; không lưu credential vào log.

## Nhóm mã lỗi

| Mã | HTTP | Ý nghĩa chính |
|---|---|---|
| COM001 / COM002 / COM003 | 400 | Validation / JSON-binding / locale |
| COM004 | 404 | Không tìm thấy hoặc ngoài phạm vi |
| COM005 / COM008 | 409 | Trùng/conflict / stale version |
| COM006 / COM007 | 500 / 503 | Nội bộ / DB chưa sẵn sàng |
| COM009 / COM010 / COM011 | 405 / 415 / 406 | Method / media type / Accept |
| AUTH001 / AUTH002 / AUTH004 | 401 | Credentials sai / chưa đăng nhập / token sai hoặc bị thu hồi |
| AUTH003 / AUTH006 | 403 | Không có quyền / CSRF |
| AUTH005 | 429 | Khóa password5 lần,15 phút |
| AUTH007 / AUTH008 | 400 | Password hiện tại sai / mới trùng lịch sử |
| AUTH011 / AUTH012 | 403 | Phải đổi password / chưa xác minh |

[ErrorCode](../../src/main/java/vn/vietroute/common/constant/ErrorCode.java), ValidationCode và hai bundle vi/en là nguồn chính; enum có thể còn mã của flow lịch sử, không suy endpoint còn hoạt động từ mã lỗi.

## Dùng cookie hoặc Bearer

Browser lấy GET csrf, giữ cookie XSRF-TOKEN và gửi header data.headerName/token trước mutation. Axios client chung đã làm; JWT ở cookie HttpOnly/SameSite=Lax/Secure theo môi trường, không localStorage/sessionStorage. API client cũng cần CSRF/cookie khi login ban đầu, sau đó dùng Authorization: Bearer <token>. Logout/password thu hồi tất cả token, không phát token thay thế sau đổi password. Cookie path theo context ứng dụng.

Không có quyền offline: private request đọc account/grants DB hiện hành, DB lỗi từ chối. Public lookup được khai báo trong WebRoutePolicy, không lấy role/quyền từ header.

## Transaction, Swagger và kiểm thử

Đọc [luồng base](LUONG_XU_LY_BASE.md) trước khi thêm API. JPA CRUD auth và JDBC batch/history cùng JpaTransactionManager; không commit riêng trong repo, không query/save theo từng phần tử. Auth DTO là whitelist riêng; không yêu cầu kế thừa BaseDTO khi model hiện hành dùng class trực tiếp. Các module business JDBC đang có giữ base/repository của chúng.

Swagger mô tả path/query/header/body, inherited pagination và generic envelope. @Operation có trên API, @Schema có trên field; principal/servlet là server-injected. HTTP 201 do execute chọn; lỗi vẫn status thực. Auth source inventory dùng /targets hoặc /resources, không lấy từ OpenAPI như nguồn cấp quyền.

Maven/test không tự đọc `.env`, không migration Neon. Init/sync theo cờ chỉ ở runtime; [chạy local](../01-bat-dau/CHAY_LOCAL.md) mô tả OS/IDE → file launcher → default properties. `.env` gốc được theo dõi Git theo yêu cầu, không đưa giá trị vào docs/log/WAR/image. H2 kiểm phần portable, PostgreSQL riêng kiểm dialect/migration/FK/batch và hai context. Bằng chứng kết quả/giới hạn nằm tại tiến độ; code local không đồng nghĩa đã lên dev/main/Render.

## Hợp đồng business và ranh giới JPA/JDBC

Quy hoạch source business ngày 01/11/2026 giữ URL/method, payload, status, locale/envelope, Bean Validation, Swagger và kiểm quyền endpoint hiện hành. Các response/request record đã chuyển thành class trong dto/request và dto/response; query/lookup/picker chuyển thành *Search ở dto/search. Java caller dùng getter chuẩn. BUSINESSDB02 gộp request theo tài nguyên; payload JSON hiện có vẫn dùng được, tên schema request chuyển về *Request. [Cấu trúc và số file](QUY_UOC_PHAT_TRIEN.md#stack-và-cấu-trúc) là nguồn quy ước.

JPA CRUD: danh mục, tỉnh/địa điểm, nhà xe/xe/tài xế. JDBC trong repo: join/count/page/scope/owner, khóa cha và kiểm quan hệ, snapshot tuyến/chuyến, batch stops/segments, confirm/cancel/thu-hoàn tiền/history, public lookup và báo cáo. Không chuyển cơ chế tồn chỗ hoặc cascade sang save từng đơn; không đổi lock order hoặc ngữ nghĩa tiền thật chỉ vì đổi ORM. JPA/JDBC cùng transaction và connection; lỗi constraint/version vẫn trả 409, lỗi history rollback mutation.

CatalogItemAPI gộp public/sẵn có và hai nhóm typed; type cố định ở handler giữ chống sửa chéo ID. BookingAPI gộp staff và owner, gọi phương thức BookingService tương ứng. Service không suy quyền từ tên role, không có API chọn tùy ý nhánh staff hoặc actor. Public projection vẫn không có PII của booking/driver/staff. Class/package đổi không tự cấp quyền mới; target method+template được giữ.

Initializer chỉ chạy khi bật cờ runtime, không trong Maven. DDL hiện hành nằm trong baseline theo cụm; chuyển package không thay contract HTTP. Hibernate không tự DDL; restart/clean WAR cần loại class cũ đã gộp. Kiểm thử không đọc .env/ghi Neon; kết quả và giới hạn triển khai xem tiến độ.


### Một request cho mỗi tài nguyên

Tất cả request business ở [quy ước BUSINESSDB02](QUY_UOC_PHAT_TRIEN.md#request-dùng-chung-và-constant). Không còn class CreateRequest/UpdateRequest/WriteRequest/ManageUpdateRequest/DeleteRequest theo cùng tài nguyên. Trường cần gửi được Swagger required theo operation, không dùng schema union bắt nhập cả field POST lẫn PUT. Các enum nghiệp vụ chuyển sang constant, giữ nguyên giá trị JSON và trạng thái DB.

Ví dụ cùng VehicleRequest:

```json
{"code":"XE002","carrierId":1,"plateNumber":"51A-123.45","vehicleTypeId":5,"capacity":16}
```

POST /api/v1/admin/vehicles không cần version. PUT /api/v1/admin/vehicles/{id} dùng cùng class:

```json
{"plateNumber":"51A-123.45","vehicleTypeId":5,"capacity":16,"active":true,"version":0}
```

Code/carrierId không bắt buộc ở PUT; nếu gửi lại phải trùng giá trị đã lưu. Không đổi quan hệ bằng cách thêm field create vào payload sửa. ID/version ở trên chỉ minh họa hợp đồng, phải lấy giá trị thật từ môi trường thử. Province/Location/Driver/Carrier/Catalog kiểm định danh tương tự; Route giữ trường code/carrierId của hợp đồng trước. Booking payment không bắt gửi contact/trip của lúc tạo. Guard/scope/owner/version/history và CSRF vẫn độc lập validation.


## Catalog: danh mục và địa lý

GET /api/v1/provinces và /api/v1/locations là lookup public theo active/locale. CRUD quản trị /api/v1/admin/provinces và /api/v1/admin/locations dùng target cụ thể từ MVC; menu /catalogs/provinces, /catalogs/locations và /operations/locations kiểm GET riêng. Cập nhật kiểm version, quan hệ tỉnh-địa điểm và tọa độ hợp lệ; owner/scope không được client tự gán. Chi tiết field/coordinate validation đọc các DTO trong business/catalog và Swagger của phiên bản đang chạy.

## Common: nhật ký và hợp đồng HTTP

Chỉ giữ nhật ký request có grant page/API chính xác: [hợp đồng debug/cURL/Postman](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md). Bỏ API/mail/job/media/status/runtime/configuration/activity/metrics và các màn tương ứng. User profile bỏ avatarMediaId; carrier/vehicle bỏ logoMediaId/photoMediaId, public carrier bỏ logoUrl. JSON gửi trường đã bỏ bị từ chối, không bỏ qua âm thầm. Không cần multipart/credential provider. Envelope/page/field-error chuyển từ record sang class, tên JSON giữ nguyên.

## Carrier: nhà xe, phương tiện, tài xế

Mỗi danh mục có trang riêng dưới menu Danh mục: `/catalogs/vehicle-types`, `/catalogs/booking-sources`, `/catalogs/provinces`, `/catalogs/locations`. `/admin` là tổng quan. URL cũ `/operations/locations` và API `/api/v1/admin/catalogs` giữ hợp đồng tương thích; không còn là nơi chọn mọi loại mới. Grant GET menu độc lập từng API GET/POST/PUT/DELETE. Không có bộ 20 function cố định trong Java.

| API dưới `/api/v1/admin` | Method/target cần được gán | Hợp đồng |
|---|---|---|
| `/vehicle-types`, `/booking-sources` | Từng GET list/id, POST, PUT id, DELETE id theo đúng path | Lọc query/active, count/page PostgreSQL; cập nhật active + version; DELETE nhận JSON version và ngừng sử dụng. ID/type khác endpoint trả 404/400. Các API catalog tương thích vẫn kiểm target riêng. |
| `/provinces`, `/locations` | Từng target page/API địa lý trong DB | Các route địa lý tương thích vẫn kiểm target riêng; public lookup vẫn chỉ active. |
| `/carriers` | Từng target page/API carriers | Actor có endpoint + globalTargets tạo scope+nhà xe trong một giao dịch qua auth, với kiểm global target và lịch sử SCOPE_CREATED; đổi active cần global grant của chính target đó. Thành viên có scope sửa thông tin; scope security không bị tắt theo business active. |
| `/vehicles` | Từng target page/API vehicles | Tạo cần carrierId được kiểm scope; code/carrier/scope bất biến khi sửa. Biển số chuẩn hóa chữ hoa, DB unique theo ký tự chữ/số, capacity 1–200, loại VEHICLE_TYPE active. |
| `/drivers` | Từng target page/API drivers | Hồ sơ có thể chưa có account. Khi gắn account: account ready, có global DRIVER và scoped DRIVER đúng nhà xe. Không suy membership từ tên role. |
| `/vehicles/carriers`, `/drivers/carriers` | Grant GET picker tương ứng và scope | Picker tìm/phân trang tại DB bằng đúng quyền scoped của tài nguyên con, chỉ trả id/code/name; không đòi GET danh sách carrier riêng hoặc lộ hồ sơ đầy đủ. |
| `/demo` POST | POST /api/v1/admin/demo + global scope cho endpoint đó | Thao tác chủ động từ `/operations/demo`; chỉ startup khi bật riêng BUSINESS_INIT và DB_INIT. Một transaction/advisory lock, mã từ JSON trong source, chỉ INSERT phần thiếu, giữ nguyên bản đã có, collision đơn vị cùng code khác tên rollback toàn bộ. |

Danh sách carrier/vehicle/driver nhận query/active/carrierId/page/size; cả count lẫn trang dữ liệu kiểm account/tokenVersion/scope/membership/role/function/target hiện hành. Truy cập ID ngoài scope trả 404; filter carrierId không cấp quyền. CREATE/UPDATE suy scope từ cha trong DB, khóa cha trước con, audit lấy principal; stale version 409 và không tự gửi lại. Mỗi dòng trả canUpdate từ guard SQL cùng scope; UI giao cờ này với capabilities target chung. Ngừng sử dụng thay xóa vật lý. COMMON01 bỏ trường ảnh và luồng upload; CRUD/version/scope/lock giữ nguyên.

Bộ dữ liệu `src/main/resources/bootstrap/foundation.json` và catalog seed gồm danh mục, tỉnh/địa điểm, hai nhà xe, bốn xe/bốn hồ sơ tài xế. Scope là cột carriers, không thêm hai bảng/dòng scope rời. Không tạo account, membership, grant hoặc email; mã/biển số/điện thoại/tọa độ minh họa. Chạy lại trả created=0/reused=21 nếu bộ đã đủ theo dữ liệu source hiện hành; không đặt lại trạng thái/nội dung người dùng đã sửa. Không xóa hoặc ghi đè để ép seed thành công. BusinessDataInitializer có bộ vận hành mở rộng trong bootstrap/operations.json: khi BUSINESS_INIT và DEFAULT_ACCOUNTS bật sẽ tạo tuyến/chuyến/đơn giả lập theo cấu hình, tách khỏi API nền. [Danh sách tài khoản và luồng dùng thử](../01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo) mô tả đầy đủ. Mặc định role thường không được tự cấp quyền source mới; quản trị gán global role/quyền rồi membership đúng carrier scope qua màn Phạm vi dữ liệu.

Nhà xe/phương tiện/tài xế nằm trong carrier/001_schema.sql với FK composite sang auth/catalog. Tài khoản quản trị cấp đã xác minh và bắt đổi password; không có email self-service.

Regression `BusinessIntegrationTest` chỉ chạy với `-Dbusiness.test.database=jdbc:postgresql://127.0.0.1:<port>/vietroute_business_test`, PostgreSQL test user ciadmin, database dành riêng không có app schema khác; tạo/drop schema random `business02_it_*` sau mỗi ca. Không đọc .env; CI dựng PostgreSQL 17 cô lập và chạy Maven verify trước image build. Kiểm hai context WAR, menu/API/type độc lập, scope count/ID/picker/revoke, stale/concurrent version, unique plate, parent inactive, account DRIVER, rollback/idempotence seed. Preview phải dùng DB khác (ví dụ vietroute_business_preview) vì startup chủ động chặn nhiều app history trong cùng DB.

## Trip: tuyến và điểm dừng

Tuyến/điểm thuộc trip/001_schema.sql, có FK composite route/carrier/scope và địa điểm. GET `/api/v1/admin/routes` nhận query/active/carrierId/page/size, GET `/{id}` trả toàn bộ điểm; grant GET route tương ứng và predicate scope trong SQL count/trang/ID. GET `/carriers` chỉ trả picker nhà xe active có grant POST tuyến tương ứng, không đòi GET danh sách carrier riêng.

POST tuyến cần grant POST tuyến tương ứng, PUT `/{id}` cần grant PUT tuyến tương ứng. JSON gồm carrierId/code/nameVi/nameEn/description/active/stops; PUT thêm version và giữ carrierId/code cũ. Mỗi điểm gồm locationId, arrivalOffsetMinutes, departureOffsetMinutes, cumulativeFare, pickupAllowed, dropoffAllowed. 2–32 địa điểm khác nhau, thứ tự theo mảng; phút đến tăng qua mốc rời trước, 0–43200 phút, giá cộng dồn không giảm và tối đa 2 chữ số thập phân. Điểm đầu arrival=0/fare=0/pickup=true; điểm cuối dropoff=true. Hai chiều là hai tuyến riêng; không tự đảo điểm/giá.

Transaction khóa nhà xe → tuyến → danh mục, thay điểm nguyên tử và CAS version. Lỗi ROUTE001/400 giữ draft, version cũ 409, parent ngừng dùng 409; ngoài scope 404. Chuyến dùng snapshot độc lập, không tham chiếu ID mẫu điểm để sửa lịch sử. `/operations/routes` đòi grant GET menu tuyến độc lập API GET. Không auto cấp các quyền này cho role thường; mapping vận hành được gán qua quản trị theo yêu cầu chủ dự án.

## Trip: điều hành chuyến

Chuyến thuộc trip/001_schema.sql. Chuyến lưu snapshot tên tuyến, loại xe/biển số, tài xế và điểm/địa chỉ/tọa độ/giờ/giá. Sửa mẫu/danh mục không sửa lịch sử đã chụp. Tồn chỗ có một dòng cho mỗi đoạn liên tiếp, capacity 1–200 và reserved_seats 0..capacity; module booking dùng các dòng này trong transaction riêng theo thứ tự khóa chung.

| API | Quyền và hợp đồng |
|---|---|
| GET `/api/v1/admin/trips` và `/{id}` | grant GET trip tương ứng + scope; query/carrierId/status/date/page/size lọc/count tại DB. date là ngày xuất phát ở Asia/Ho_Chi_Minh. Chi tiết có stops và tối đa 100 sự kiện mới nhất. ID ngoài quyền 404. |
| GET `/api/v1/admin/trips/choices` | kind CARRIER/ROUTE/VEHICLE/DRIVER, carrierId (cần cho con), query/page/size; tripId=0 dùng grant POST chuyến tương ứng, tripId dương dùng grant PUT trip tương ứng và buộc nhà xe của trip. Projection ID/mã/tên/capacity; không cần quyền CRUD danh mục phụ thuộc. |
| POST `/api/v1/admin/trips` | grant POST chuyến tương ứng; code/routeId/vehicleId/driverId/departureAt/capacity. Tạo DRAFT, giờ UTC ISO-8601 trong tương lai tối đa 730 ngày, chuẩn hóa giây. Source active, cùng carrier/scope, DRIVER ready có global/scoped DRIVER. |
| PUT `/{id}/assignment` | grant PUT trip tương ứng; vehicleId/driverId/capacity/departureAt nullable/version/note. DRAFT/OPEN; chỉ DRAFT dịch lịch. Không giảm dưới mức reserved cao nhất hoặc vượt xe. Không sửa snapshot điểm/giá. |
| PUT `/{id}/state` | grant PUT trip tương ứng; status OPEN/CANCELLED, version và note. DRAFT→OPEN; DRAFT/OPEN→CANCELLED, hủy cần lý do. Không cho staff tự gửi IN_PROGRESS/COMPLETED hoặc đi ngược trạng thái. |

Mở bán khóa trip → nhà xe → source → xe → tài xế/account; khóa xe/tài xế tuần tự hóa cạnh tranh lịch. Đổi phân công dùng trip → nhà xe → xe → tài xế/account → segments tăng dần. Lịch chồng dùng `start < other.end AND end > other.start` cho OPEN/IN_PROGRESS, gồm qua đêm/nhiều ngày; hai chuyến tiếp giáp được phép. Không khóa trip khác khi kiểm lịch để tránh vòng chờ. Mutation tăng version/CAS và ghi trip_events cùng transaction; lỗi lịch sử rollback cả thay đổi. Snapshot địa điểm cũ cũng phải active trước mở bán, kể cả mẫu đã thay điểm.

`/operations/trips` có grant GET menu chuyến riêng, modal thông tin/điểm/lịch sử, picker tìm và phân trang server, lỗi giữ draft. TRIP001 là dữ liệu không hợp lệ (400), TRIP002 lịch trùng và TRIP003 trạng thái không cho phép (409); version cũ COM008/409. Không auto grant role thường. Luồng điều hành được nối với tiến độ tài xế, hủy/giải phóng chỗ, tìm công khai và báo cáo theo các contract dưới đây; không có theo dõi GPS.

## Trip: chuyến được giao và tiến độ

GET `/api/v1/driver/trips` và `/{id}` dùng grant GET API tài xế tương ứng; PUT `/{id}/progress` dùng grant PUT /api/v1/driver/trips/{id}/progress. Trang `/driver/trips` dùng grant GET menu tài xế riêng. SQL count/page/ID đồng thời yêu cầu user là driver_user_id, hồ sơ driver vẫn active/liên kết user và global + scoped DRIVER còn hiệu lực trong đúng scope. Không trả DRAFT hoặc chuyến người khác; ADMIN không tự đóng vai tài xế. Thu hồi liên kết/membership/role có hiệu lực tại lần kiểm tiếp theo, không hứa hủy request đã chạy.

Progress JSON gồm action START/ARRIVE/DEPART/COMPLETE, stopOrder, version, note. START chỉ OPEN tại điểm 1 và từ 30 phút trước departure; ghi actual đến/rời điểm đầu cùng thời gian server, kiểm lại assets/driver ready/lịch xe đang chạy. ARRIVE cần điểm trước đã rời; DEPART cần đã đến điểm giữa; COMPLETE cần đã đến điểm cuối. Không bỏ điểm/đi lùi/tự gửi actual timestamp. Khóa trip trước mọi thay đổi, CAS version, actual/state/events cùng connection; lỗi event rollback toàn bộ và hai START cùng version chỉ một thành công. Đây là tiến độ do tài xế xác nhận, không phải GPS.

PUT `/api/v1/admin/trips/{id}/notice` dùng grant PUT trip tương ứng trong scope, JSON note không rỗng + version. Chỉ OPEN/IN_PROGRESS; tăng version, giữ trạng thái và lưu thông báo + NOTICE nguyên tử. Không cho hủy IN_PROGRESS hoặc tự hoàn tiền/chuyển khách. UI staff cho ghi chú/thông báo trong modal ngay cả khi chuyến đang chạy; không mở lại form phân công.

BookingRepo phối hợp trên cùng connection: đóng PENDING tại điểm rời, hoàn thành CONFIRMED tại điểm trả và cascade hủy chuyến. Tiến độ xác nhận vẫn tách khỏi GPS.

## Booking và tìm chuyến công khai

Đơn gắn FK composite trip/carrier/scope và hai điểm snapshot. WEB phải có customer; PHONE không cần tài khoản khách. Giá chốt bằng giá cộng dồn của snapshot tại lúc gửi, không nhận owner/source/giá/audit từ client. Partial unique chỉ một WEB PENDING/CONFIRMED mỗi customer; khi tạo WEB khóa user sau trip để tuần tự hóa cả hai chuyến khác nhau. PENDING không giữ chỗ.

| API | Quyền và dữ liệu |
|---|---|
| GET `/api/v1/trips` | Công khai; fromLocationId hoặc fromProvinceId, toLocationId hoặc toProvinceId, date ngày pickup Việt Nam, seats 1–200, carrierId/vehicleTypeId tùy chọn, page/size. SQL lọc hướng/điểm chưa rời/nhà xe và scope active/MIN chỗ trên từng range; count/trang cùng predicate. Chỉ chuyến từng OPEN, đang IN_PROGRESS hoặc OPEN chưa quá giờ đầu. |
| GET `/api/v1/trips/{id}` và `/{id}/quote` | Công khai, projection tường minh không customer/account/driver PII; chi tiết snapshot/tiến độ/thông báo vẫn đọc được sau hủy/hoàn thành, DRAFT 404. Quote nhận pickupStopId/dropoffStopId/seats và tính tại server; chỉ báo khả năng nhận hiện tại, không giữ chỗ. |
| GET `/api/v1/my-bookings` và `/{id}` | grant GET API owner tương ứng + CUSTOMER ready hiện hành, chỉ owner; query/tripId/status/page/size lọc tại DB. ID người khác 404. |
| POST `/api/v1/my-bookings` | grant POST đặt chỗ owner + CUSTOMER ready; tripId/pickupStopId/dropoffStopId/seats/contactName/contactPhone/requestKey UUID. Actor/source/giá lấy tại server. |
| PUT `/api/v1/my-bookings/{id}/state` | grant PUT owner tương ứng, owner; chỉ CANCELLED trước actual departure pickup, version/note bắt buộc. |
| GET `/api/v1/admin/bookings` và `/{id}` | grant GET booking tương ứng + scope trong SQL count/page/ID; không dùng CUSTOMER owner để mở dữ liệu nhà xe khác. |
| GET `/api/v1/admin/bookings/trips` | grant POST booking tương ứng trong scope, picker PHONE query/page/size độc lập grant GET trip tương ứng. |
| POST `/api/v1/admin/bookings` | grant POST booking tương ứng + scope; cùng payload đặt nhưng server cố định PHONE và customer NULL. Dùng chung thuật toán chỗ/giá. |
| PUT `/api/v1/admin/bookings/{id}/state` | grant PUT booking tương ứng + scope; PENDING→CONFIRMED/REJECTED/CANCELLED, CONFIRMED→CANCELLED trước pickup đã rời. Version, lý do từ chối/hủy. |
| PUT `/api/v1/admin/bookings/{id}/payment` | grant PUT booking tương ứng + scope; kind PAYMENT/REFUND, amount, note, version, requestKey. Ghi nhận thủ công đã diễn ra ngoài hệ thống: chỉ thu CONFIRMED/COMPLETED, hoàn CANCELLED; tổng thu ≤ giá đơn, tổng hoàn ≤ đã thu. Không gọi provider. |
| GET `/api/v1/driver/trips/{id}/passengers` | grant GET API tài xế tương ứng, guard đúng assigned/global+scoped DRIVER/link hiện hành trong cùng connection; phân trang CONFIRMED/COMPLETED theo đón/trả. Chỉ mã/chặng/số chỗ/tên/điện thoại cần phục vụ; không email/user ID/giá/audit. |

Mutation khóa trip → user (chỉ tạo WEB) → booking → segments tăng dần. `TripSegmentRepo` là một thuật toán dùng chung; confirm tăng đúng [pickup,dropoff), hủy CONFIRMED giảm đúng một lần, kiểm liên tục và đủ mọi chặng. CAS và booking_events cùng transaction; history lỗi rollback. UUID tạo/tiền replay cùng actor và nội dung trả kết quả cũ, khác nội dung bị chặn; không tự retry mutation. Hủy trip cascade PENDING/CONFIRMED và giải phóng chỗ trong transaction trip. START/DEPART đóng PENDING tại điểm đã rời; ARRIVE hoàn thành CONFIRMED ở điểm trả, giữ reserved lịch sử. Hoàn thành/hủy kết thúc ràng buộc một WEB active.

BOOKING001 dữ liệu sai/400, BOOKING002 thiếu chỗ/409, BOOKING003 trạng thái/409, BOOKING004 đã có WEB active/409, BOOKING005 UUID khác nội dung/409, BOOKING006 thu/hoàn vượt giới hạn/400; version cũ COM008/409. Tiền hủy chưa hoàn có refundDue, không tự ghi đã hoàn. Trang `/journeys` công khai, `/my-bookings` grant GET menu đơn của tôi và `/operations/bookings` grant GET menu booking độc lập API GET. Startup sync bindings/bootstrap khi bật; role thường vẫn cần mapping DB tường minh. Báo cáo nằm trong business/report; tiến độ là mốc tài xế xác nhận, không có phát vị trí thiết bị.

## Trip: tra cứu công khai và bộ lọc

GET `/api/v1/carriers` công khai nhận query/page/size; count/trang trong SQL, từ khóa nguyên văn không phân biệt hoa/thường, thứ tự tên/id ổn định. GET `/{id}` chỉ nhà xe và scope còn active, còn lại 404. DTO chỉ id/code/name/phone/description, không scope/audit/version/nhân sự/customer. Đây là thông tin và liên hệ nhà xe đã công bố; không dùng staff membership để lọc lựa chọn của khách. Các API quản trị `/api/v1/admin/carriers` vẫn có guard riêng, không mở theo matcher public. COMMON01 đã bỏ logoUrl và API media.

Logo được lấy từ FK server sau kiểm công bố, dùng MediaService đọc URL READY theo lô tối đa 100 ID trên cùng connection, không gọi CDN/N+1 hoặc trả provider ID/metadata thư viện. Ảnh không READY không công bố URL. Trang `/carriers` có tìm/phân trang, modal liên hệ và link `/journeys?carrierId=...`; URL chỉ là bộ lọc, không là quyền/scope.

GET `/api/v1/trips` thêm minPrice/maxPrice nullable: giá một chỗ VND trên đúng cặp điểm, gồm hai cận, không âm, tối đa 10 chữ số phần nguyên/2 thập phân và min≤max. Khoảng đảo trả 400/COM001 với field priceRangeValid, VAL015/validation.priceRange; OpenAPI gồm cả from/to, date/seats/carrierId/vehicleTypeId/minPrice/maxPrice/page/size kế thừa. Picker nhà xe dùng public projection trên; loại xe dùng catalog lookup active, đều có tìm/trang và giữ lựa chọn ngoài trang. Giá và count vẫn tính tại DB, không lọc sau phân trang ở JavaScript. Không migration hoặc ordinary role grant mới.

## Report: báo cáo vận hành

`GET /api/v1/admin/reports` dùng grant GET báo cáo tương ứng scoped; `GET /api/v1/admin/reports/carriers` là picker query/page/size cùng quyền, không đòi GET danh sách carrier riêng. GET menu riêng bảo vệ `/operations/reports`. Role có all_scopes và chính grant endpoint đó tổng hợp mọi nhà xe trong scope active; các trường hợp khác chỉ các membership/role hiện hành có grant GET báo cáo tương ứng, không union quyền từ scope khác. Carrier ID ngoài phạm vi trả 404. Picker chỉ có id/code/name/active, không xuất scope/audit/nhân sự. Nhà xe ngừng kinh doanh vẫn có lịch sử khi scope bảo mật còn active.

`fromDate`/`toDate` là ngày khởi hành tại Việt Nam, bao gồm hai cận, tối đa 366 ngày (VAL016). Mặc định 30 ngày đến hôm nay; carrierId tùy chọn. PostgreSQL đổi cận thành [đầu ngày, đầu ngày kế tiếp) và tổng hợp bằng một statement snapshot, tách aggregate chuyến/đơn để không nhân trùng số chuyến. DTO xuất thời điểm asOf, số chuyến/đơn cùng 5 trạng thái hữu hạn mỗi loại, lượt chỗ và giá chốt của CONFIRMED/COMPLETED, tổng đã thu/đã hoàn, thu trừ hoàn và tiền của đơn CANCELLED còn phải hoàn. Không xuất PII hoặc chi tiết đơn qua báo cáo.

Tiền là số hiện tại của đơn thuộc nhóm chuyến khởi hành trong khoảng, kể cả đơn hủy; không phải dòng tiền theo ngày ghi nhận payment. PENDING không là chỗ đã bán hoặc doanh thu; confirmedValue không là paidAmount. Mỗi chỗ của một đơn là một lượt, không phải ghế vật lý duy nhất. Không migration, không tự cấp endpoint báo cáo cho role thường; vận hành gán mapping bằng API quản trị sau triển khai. Không phát sinh thanh toán hoặc hoàn tiền khi đọc báo cáo.

## Carrier: cấp tài khoản tài xế

POST /api/v1/admin/drivers/{id}/account nhận UserRequest với username/email/password/version. Actor cần endpoint này và phạm vi nhà xe của hồ sơ; UserService còn kiểm toàn bộ endpoint của role tài xế định cấp không vượt quyền actor. Nhà xe/hồ sơ bị khóa cùng transaction, hồ sơ chưa liên kết account và chưa có chuyến được phân công. Không cấp tùy ý role từ payload.

Account mới active/verified, must_change_password=true, email chuẩn hóa; global/scoped role và history ghi nguyên tử. Hồ sơ tài xế được liên kết nhưng inactive; sau đổi password, người có quyền nghiệp vụ bật hồ sơ mới có thể phân chuyến. Không có bước email tự phục vụ. Response dùng UserDTO với id/username/mustChangePassword/version cần thiết, không hash/password, không hợp đồng DriverProvisionResponse cũ. UI đọc canAssign theo endpoint mutation riêng. Snapshot chuyến cũ không tự chuyển sang account mới.

## Report: dashboard và điều hướng

`GET /dashboard` là trang HTML cần xác thực, không có endpoint aggregate hoặc bảng mới. Home/login đưa phiên sẵn sàng tới dashboard; cờ mustChangePassword/emailVerificationRequired vẫn đưa tới account. Dashboard chỉ GET các API hiện hành theo capabilities: my-bookings theo owner, driver/trips theo phân công hiện hành, reports và admin/bookings theo grant endpoint. Guard/scope/owner/version vẫn do các service cũ kiểm, không lấy role/tenant từ query. Mỗi danh sách lấy tối đa 5 bản ghi và tổng PostgreSQL; báo cáo dùng nhóm ngày khởi hành từ đầu tháng tới hết tháng kế tiếp tại Việt Nam, tiền thực thu không phải giá chốt. Menu/breadcrumb giữ locale/context và lọc target GET menu; role owner/phân công là điều kiện nghiệp vụ bổ sung, không thay grant endpoint GET. Chi tiết trình bày ở [quy ước UI](../03-giao-dien/QUY_UOC_UI.md).
