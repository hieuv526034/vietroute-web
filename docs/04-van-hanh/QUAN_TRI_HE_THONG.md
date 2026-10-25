# Giao diện quản trị hệ thống

Cập nhật 25/10/2026. Dùng menu **Hệ thống** và **Quản trị truy cập** sau khi đăng nhập; link chỉ xuất hiện theo quyền hiện hành. Nghiệp vụ nhà xe/chuyến/booking đã có màn riêng; trạng thái triển khai và bằng chứng nằm ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md). Backend vẫn kiểm quyền kể cả khi nhập URL hoặc gọi API trực tiếp.

## Các màn đã triển khai trong source

| Trang | Quyền xem | Chức năng |
|---|---|---|
| `/operations/jobs` | JOB_VIEW; dữ liệu cần JOB_READ | Lọc trạng thái, phân trang, xem metadata và mã lỗi, retry DEAD khi có JOB_RETRY |
| `/operations/access` | ACCESS_VIEW; dữ liệu cần ACCESS_READ | Ma trận quyền của role; sửa role thường nếu có ACCESS_ASSIGN/ACCESS_WRITE trong phạm vi cho phép |
| `/operations/configuration` | CONFIGURATION_VIEW; dữ liệu cần CONFIGURATION_READ | Xem snapshot whitelist thông số không nhạy cảm của instance; chỉ đọc |
| `/monitoring` | SYSTEM_VIEW; dữ liệu cần SYSTEM_READ | JVM/heap/CPU/HTTP/pool/worker và mẫu probe DB opt-in; hướng dẫn tại [giám sát](GIAM_SAT_VA_VAN_HANH.md) |

Mỗi màn có vi/en, trạng thái tải/rỗng/lỗi, nút làm mới và menu theo quyền. JavaScript dùng Axios/CSRF/lỗi chung, không localStorage token, không đưa giá trị server vào innerHTML. Fieldset khóa trong lúc xử lý để hạn chế gửi lặp. Quyền hiển thị UI chỉ hỗ trợ thao tác; service là nơi quyết định quyền thực tế.

## Quản lý job

API giữ hợp đồng list/detail/retry hiện hành; list thêm query `status=QUEUED|RUNNING|SUCCEEDED|DEAD` tùy chọn. Không có status nghĩa xem tất cả. Page từ 0, size 1–100; UI dùng 20 dòng. Filter không hợp lệ trả 400, thiếu JOB_READ trả 403. DAO bind tham số và dùng search_path cấu hình, không nhận cột SQL từ client.

Chọn Chi tiết để đọc ID/type/status/attempts, availableAt/lease, mã lỗi, thời điểm/audit/version. Không trả payload, email người nhận, dedup key hoặc response provider. Chỉ DEAD và JOB_RETRY mới hiện thao tác retry khả dụng; UI yêu cầu xác nhận rồi gửi `POST /api/v1/admin/jobs/{id}/retry` với `{"version":<version đang xem>}`. Backend tiếp tục kiểm trạng thái/version trong SQL.

Chi tiết job có cả createdBy/updatedBy; ID 0 hiển thị system. Retry ghi người bấm từ principal; worker nhận/chạy xong sẽ cập nhật người sửa thành system. Xem [quy tắc audit](../02-phat-trien/AUDIT_VA_SYSTEM.md); không suy diễn đây là lịch sử mọi lần thay đổi.

409 nghĩa dữ liệu/trạng thái đã đổi: UI hiển thị lỗi trong hộp chi tiết, không tự retry, người dùng làm mới rồi xem lại. Thành công giữ ID/dedup, queue nhận lại khi worker bật. At least once vẫn có thể lặp tác động provider nếu ACK trước đó mất; kiểm receipt/kết quả gửi trước khi retry. Không có nút retry hàng loạt hoặc xóa job/PII tùy ý.

## Ma trận quyền và role tài khoản

Ma trận lấy registry có tên vi/en và version role từ API hiện hành. ADMIN có quyền cứu hộ cố định: không cho sửa. Người chỉ ACCESS_READ xem được nhưng không lưu. Người được ủy quyền chỉ gán quyền thuộc tập họ đang có; nếu role hiện hành chứa quyền ngoài tập của mình, UI cho xem và yêu cầu ADMIN xử lý, backend vẫn chặn việc nâng quyền. Request PUT thay toàn bộ tập quyền, có version và CSRF; tập rỗng nghĩa thu hồi quyền chức năng của role thường.

Sau khi lưu mapping, UI lấy lại `/auth/me` vì thay quyền có hiệu lực ở request kế tiếp. Nếu tự mất ACCESS_READ qua role của mình, chuyển về trang tài khoản; không để UI tiếp tục coi quyền cũ còn hợp lệ. Làm mới lấy lại quyền trước khi dựng controls. Conflict không được gửi lại mutation tự động.

Phần tra tài khoản mở cho ACCESS_READ; gán cần ACCESS_ASSIGN trong phạm vi được ủy quyền. API bổ sung:

```text
GET /api/v1/admin/access/users?username=Ad&page=0&size=20
```

`username` là prefix không phân biệt hoa/thường, chữ/số tối đa 32 ký tự; rỗng để xem tất cả. Chọn tường minh id/username/displayName/active/roles/version/createdAt/updatedAt; không đọc password hash vào DTO hoặc trả token/credential. Role được ủy quyền có ACCESS_READ cũng liệt kê tài khoản, không trả credential. Query dùng PreparedStatement, không wildcard do người dùng cung cấp và không N+1 theo từng user. Nếu dữ liệu lớn cần đo query plan và cân nhắc index prefix qua migration mới, không sửa migration đã áp dụng.

Chọn tài khoản để sửa các role; không tự sửa role bản thân. Ghi dùng API PUT đã có, cần thêm ACCESS_ASSIGN/ACCESS_WRITE và version hiện hành; actor không ADMIN chỉ gán role có quyền nằm trong tập mình có, không đụng user ADMIN/system hoặc bản thân; backend bảo vệ admin cuối và thu hồi token cũ của user đích. ADMIN cấp tài khoản/mật khẩu tạm tại menu Tài khoản; người nhận tự đổi mật khẩu. Scope quản trị có tab riêng trong Phân quyền, khác hồ sơ nhà xe/tài xế; phạm vi booking đã được kiểm tại service/DAO nghiệp vụ; xem [kế hoạch nhóm](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

## Trang cấu hình an toàn

`GET /api/v1/admin/system/configuration` dùng CONFIGURATION_READ và envelope BaseController. DTO typed whitelist: môi trường/cookie/initializer/lifetime, pool/timeout, quota/concurrency/body, worker/lease/retention, cờ mail/media và cấu hình probe. Không serialize Settings, env map, JDBC URL, DB username/password/schema, mailbox, SMTP host/password, JWT key, Cloudinary secret, CIDR proxy hoặc token metrics.

Chỉ có GET; POST/PUT không phải API thay cấu hình. Sửa biến môi trường trong IDE/Render, restart/deploy và đọc lại snapshot trên đúng node. `enabled` nghĩa cờ bật tại startup, không khẳng định worker đang chạy hoặc provider đã kết nối thành công. Runtime xem màn Giám sát. Không có nút thử gửi mail/upload thật hay tự gọi provider khi mở trang. Không cần bật Kafka/Redis, worker, mail, media hoặc DB probe để render trang.

API cấu hình cần CONFIGURATION_READ, đọc Settings trong RAM; xác thực JWT/cookie vẫn tra DB như các API người dùng khác. Metrics token riêng chỉ đọc `/internal/metrics`, không mở các trang quản trị hoặc API cấu hình. Không coi UI cấu hình chỉ đọc là cơ chế lưu cấu hình runtime đa node.

## Kiểm tra và giới hạn

Kiểm local bằng MockMvc cho quyền xem từng trang vi/en, quyền service, filter/size sai bị chặn trước DB, config không lộ sentinel secret và OpenAPI có endpoint/DTO. Kiểm SQL và API qua PostgreSQL tạm trong Docker CI: tra prefix/page/no result, role/version, filter job/trạng thái lỗi, cấu hình whitelist và render trang bằng cookie đã đăng nhập. CI hiện hành kiểm role assignment, CSRF, admin bảo vệ và stale retry dùng chung.

Chrome headless local với HTML Thymeleaf và fixture API riêng đã kiểm click role/user assignment có CSRF/version, chặn ADMIN/bản thân, conflict retry hiện trong dialog không tự retry, config chỉ đọc và mobile không tràn ngang. Đây là bằng chứng UI trên fixture; SQL/auth thật vẫn cần Docker CI, không coi fixture là kết quả database production.

Các kiểm tra này không chứng minh provider production, cảnh báo thật, backup hoặc tải nhiều node. Tài liệu này mô tả chức năng source; kết quả CI và merge xem PR tương ứng. Không test trên Neon production. Khi bàn giao ghi đúng ca đã chạy và phân biệt HTML render, browser fixture với API/SQL thật.

## Hoàn thiện trước nghiệp vụ

Đợt tiếp theo bổ sung menu không gạch chân, active/focus/đóng Escape, lối tắt quản trị, UI danh mục sửa/xóa mềm/phân trang và trạng thái dịch vụ ảnh tắt. Đọc [nghiệm thu nền tảng](../05-ke-hoach/NGHIEM_THU_NEN_TANG.md) để xem phạm vi hoàn thành, ca kiểm giao diện và phần vận hành còn chờ cấu hình ngoài app. Không coi đã có đầy đủ tài khoản/scope nhà xe hoặc provider thật từ đợt UI này.

Đợt tab/modal: [Tab và modal dùng chung](../03-giao-dien/QUY_UOC_UI.md) ghi các màn đã áp dụng, luồng mở/lưu/hủy, lỗi trong dialog và hướng dẫn để business kế thừa.


## Quản trị role, quyền và menu/endpoint

Vào **Quản trị truy cập → Role và phân quyền** bằng tài khoản có ACCESS_VIEW và quyền hành động tương ứng. Tab **Role và quyền** chọn loại role/quyền, thêm bằng modal, sửa tên/trạng thái bằng icon ở cột thao tác. Mã giữ nguyên sau tạo, tên có cả vi/en; mã lõi được bảo vệ. Tab gán quyền role và role user vẫn tách riêng. Mỗi thao tác kiểm quyền hành động và version ở backend; 409 cần đọc lại rồi đối chiếu, không tự gửi lại bản nháp.

Tab **Menu và endpoint** chọn target từ inventory thật, chọn tất cả quyền bắt buộc và một trong các role cho phép. Để roles rỗng nếu không hạn chế theo role; permissions rỗng chỉ không thêm điều kiện quyền. Bỏ chọn cho phép sẽ chặn người thường. ADMIN có đường cứu hộ, còn guard nền trong code luôn giữ. Xóa policy trong modal chỉ bỏ điều kiện bổ sung. Nội dung và ca kiểm chi tiết ở [nền tảng mở rộng](../02-phat-trien/NEN_TANG_MO_RONG.md#registry-động-chính-sách-menuendpoint).

**Giám sát → Truy cập và online** hiển thị cửa sổ 3 phút, phân trang trình duyệt và tín hiệu bất thường. Không dùng số này làm báo cáo lượt người chính xác tuyệt đối; đọc [giới hạn lưu trữ/đa node](GIAM_SAT_VA_VAN_HANH.md#truy-cập-người-online-và-tín-hiệu-bất-thường). Thư viện ảnh có modal sửa tên/mô tả; thêm/sửa/xóa vẫn kiểm owner, quyền riêng và version, lỗi giữ bản nháp trong modal.

## Cây gán quyền — ACCESS03

Chọn role, mở **Chỉnh sửa quyền vai trò** để xem cây nhóm → chức năng → hành động. Có tìm kiếm theo tên/mã/nhóm, checkbox ba trạng thái, mở/thu gọn và chi tiết endpoint/ANY/ALL. ADMIN mở cây chỉ đọc. Chọn tất cả áp dụng trong kết quả đang hiển thị; preview luôn liệt kê mã thêm/bỏ và số tài khoản gán role ở snapshot. VIEW và READ độc lập; UI gợi VIEW cho nhánh đang chọn, đánh dấu quyền dùng chung. Sửa qua UI cần READ+UPDATE; API-only có thể chỉ dùng hành động.

Tab tài khoản có nút xem nguồn quyền, chỉ rõ role nào cấp từng permission. Bỏ quyền ở một role không làm mất quyền đến từ role khác. Registry role/permission ngừng hoạt động không được actor thiếu quyền kích hoạt lại để tự nâng quyền. Lưu có version/CSRF/loading, lỗi giữ draft, không tự gửi lại. Giới hạn và hợp đồng đầy đủ ở [schema và phân quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#cây-chức-năng-và-quyền-hành-động--access03); nâng V012 lên V013 cần dừng/drain các node cũ trước.

## Gán bộ hệ thống và tìm tài nguyên — ACCESS04

Ở Phân quyền, chọn role → Chỉnh sửa quyền vai trò → Chọn bộ quyền hệ thống → xem preview → Lưu. Đây là gán nhiều mapping quyền một lần, vẫn có thể bỏ riêng một quyền sau đó. Không gồm CRUD danh mục/ảnh/nghiệp vụ, không mở quyền chỉ ADMIN; actor được ủy quyền phải có đủ bộ quyền mới chọn được mẫu. Quyền chưa lưu chỉ ở draft; mở lại role đọc mapping từ DB. Module nghiệp vụ tiếp tục gán VIEW/LIST/DETAIL/CREATE/UPDATE/DELETE theo nhu cầu, READ cũ giữ hợp đồng.

Tab Tài nguyên UI/API chọn MENU/ENDPOINT hoặc tất cả, nhập route/controller/mô tả/mã quyền rồi Tra cứu. Lọc thực hiện trước paging; đổi trang giữ bộ lọc đã tìm, tìm mới về trang đầu. Từ khóa tối đa 160 ký tự, không phải biểu thức SQL/wildcard. Tài liệu Swagger ở menu API có đủ query kế thừa cùng body JSON/multipart để thử trực tiếp; dùng token/cookie của chính tài khoản đang kiểm.

## Cấp tài khoản và kiểm tra phân quyền

Vào **Quản trị truy cập → Tài khoản** bằng admin để tạo danh tính, gán role và bàn giao mật khẩu tạm qua kênh riêng. Người nhận phải đổi mật khẩu và xác minh email trước khi dùng quyền riêng tư. OPERATOR có DRIVER_ASSIGN chỉ cấp DRIVER tại hồ sơ chưa liên kết trong nhà xe của mình; không được vào quản trị danh tính toàn hệ thống. Account mới và hồ sơ vừa cấp vẫn ở trạng thái chờ thiết lập. Gmail hiện tạm hoãn nên account mới chưa hoàn tất xác minh email; không coi việc tạo danh tính thành công là dùng nghiệp vụ được ngay. Ngoại lệ ba account được chủ dự án xác nhận một lần được ghi ở tiến độ, không áp dụng cho account mới. [Modal cấp tài xế trên Render](../images/auth03-neon-driver-account.png) được kiểm bằng operator01 và đóng mà không tạo account.

Cấu hình role tại Phân quyền → Tạo vai trò → chọn menu/thao tác → Lưu; sau đó gán role tại Tài khoản. Dùng nút xem để kiểm quyền thực tế của tài khoản, gồm policy bổ sung. Lưu gán/khóa/mở làm token cũ hết hiệu lực; người nhận đăng nhập lại. Khóa không xóa lịch sử, không cho khóa ADMIN/system/bản thân hoặc mở bỏ qua email đang chờ. Version 409 cần đọc lại có chủ đích. Xem [hợp đồng chi tiết](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#quản-trị-tài-khoản-và-ma-trận-menu--access05).


## Tìm role và tra lịch sử đổi quyền

Ma trận/gán tài khoản tìm role theo tên vi/en hoặc mã, gõ không dấu vẫn tìm được. Lọc không bỏ các checkbox đã chọn; trước Lưu xem preview thêm/bỏ và số tài khoản bị ảnh hưởng. Tab Role và quyền có từ khóa/trạng thái/phân trang server, số mã đã dùng và quota; COM016 là đạt quota, khác 409 do trùng/version. Tombstone giữ mã role đã xóa nhưng không chiếm suất tạo mã khác.

Tab Lịch sử phân quyền chỉ ADMIN + ACCESS_READ: lọc loại, tiền tố mã/ID đối tượng và ID người thực hiện, mở icon để xem actor/time/traceId/version trước–sau. Mapping hiển thị mã bị bỏ/thêm, không phải toàn bộ quyền; thao tác thất bại không có sự kiện thành công giả. TraceId có thể tra thêm Nhật ký API bằng ADMIN + SYSTEM_READ, nhưng không phải mọi request đều có mutation hoặc mọi trace đều được lưu khi DB lỗi. Lịch sử quyền mặc định giữ 7 ngày/mục tiêu 1.000 sự kiện, không bất biến trước quản trị DB và không có dữ liệu quá khứ trước V014.

## Gán phạm vi cho nhân viên

Sau khi triển khai đồng nhất V015 trên mọi node: tạo role/quyền và tài khoản toàn cục trước, vào Phân quyền → Phạm vi dữ liệu → Tạo phạm vi → chọn icon thành viên → Gán tài khoản. Tìm prefix username, chọn đúng user, tick tập con role của chính user cho phạm vi này rồi Lưu. Không gán ADMIN/system/self. Một user có thể có quyền quản lý ở A và chỉ đọc ở B; backend không trộn hai tập quyền. Tắt membership/thu hồi global role làm mất quyền scoped ở lần DB check tiếp theo, không hủy transaction đã chạy.

Scope là metadata bảo mật; tạo scope ở màn phân quyền không tự tạo hồ sơ nhà xe/chuyến/booking. CARRIER01 tạo nhà xe cùng scope; DAO fleet/tuyến/chuyến/booking/report và AUTH03 đã gắn quan hệ và kiểm phạm vi. Khách xem nhà xe/chuyến công khai không cần membership nhân viên. Tắt scope bảo mật chặn mọi thao tác scoped, khác nhà xe ngừng nhận booking nhưng còn xử lý chuyến cũ. Xem [hợp đồng nâng phiên bản/giới hạn](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#nền-phạm-vi-dữ-liệu--scope01); trạng thái/bằng chứng thực tế nằm ở tiến độ.

## Khôi phục quản trị gốc

Đăng nhập bằng đúng username bootstrap cấu hình server (mặc định admin), đổi mật khẩu tạm nếu được yêu cầu, rồi vào avatar → Thông tin cá nhân → Khôi phục quản trị gốc. Nút này chỉ hiện cho tài khoản gốc, kể cả mất mọi role/quyền. Nhập mật khẩu hiện tại để xác nhận; chờ kết quả, không gửi lại khi chưa rõ trạng thái. Thành công tạo/sửa dữ liệu quyền thiếu và thu hồi mọi phiên của tài khoản; bấm Đăng nhập lại bằng mật khẩu hiện tại. ADMIN khác không có nút này; không dùng mật khẩu bootstrap cấu hình để thay mật khẩu account đã đổi. Policy từ chối /account cũ không khóa được đường cứu hộ.

Nếu không còn bản ghi account, không có phiên để bấm nút: cấu hình đúng username/password bootstrap qua môi trường riêng và khởi động với DB_INIT (schema mới) hoặc ACCESS_SYNC (schema đã đủ migration của bản source đang chạy). Startup giữ hash/history/lockout; AUTH17 chỉ cấp sẵn đúng bootstrap identity cấu hình, không phải mọi ADMIN. Nếu cả hai cờ tắt thì startup không tự sửa. Xem [chạy local](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md) và [hợp đồng cứu hộ](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#cứu-hộ-tài-khoản-bootstrap--access15). Hỏng bảng/migration history hoặc mất dữ liệu nghiệp vụ cần quy trình restore riêng; nút này không thay backup. Việc sửa thủ công ghi ADMIN_RECOVERED trong lịch sử cùng transaction; chỉ admin có quyền đọc lịch sử mới tra được sau khi đăng nhập lại.

## Dùng ba tài khoản đã cấp sẵn

Đăng nhập tại [VietRoute trên Render](https://vietroute-web.onrender.com/login) bằng mật khẩu đã bàn giao riêng. Ba tài khoản dưới đây đã được cấp sẵn theo xác nhận của chủ dự án; không cần thao tác email hoặc đổi mật khẩu tạm để xem nghiệp vụ hiện có.

| Tài khoản | Màn hình bắt đầu | Dữ liệu và thao tác được cấp |
|---|---|---|
| `custom01` — CUSTOMER | **Đơn của tôi** (`/my-bookings`), **Tìm chuyến**, **Nhà xe** | Ba đơn WEB của chính mình: hủy, từ chối và xác nhận. Chỉ một đơn WEB đang chờ/đã xác nhận tại một thời điểm; muốn đặt đơn khác phải xử lý đơn hiện hành theo điều kiện nghiệp vụ |
| `driver01` — DRIVER | **Chuyến được giao** (`/driver/trips`) | Bốn DEMOTRIP01–04 được giao; xem khách đã xác nhận, bắt đầu/đến/rời/hoàn tất đúng thứ tự và thời gian. Không sửa giá, thu tiền, quản trị nhà xe hoặc tài khoản |
| `operator01` — OPERATOR | **Nghiệp vụ** → Nhà xe, Xe, Tài xế, Tuyến, Chuyến, Đơn đặt chỗ, Báo cáo | Chỉ DEMOMIENNAM/scope 1; hai xe, hai hồ sơ tài xế, hai tuyến, bốn chuyến và bảy đơn WEB/PHONE. Tạo/sửa và xử lý theo hành động đã cấp; DRIVER_ASSIGN cấp danh tính tài xế trong phạm vi, không quản trị tài khoản toàn hệ thống |

Để đối chiếu dữ liệu cố định lúc bàn giao, lọc báo cáo từ **01/10/2026 đến 30/11/2026**: bốn chuyến, bảy đơn, bốn lượt chỗ xác nhận, giá trị đặt 2.400.000 đồng, thực thu/thực hoàn đều 0. Đây là DEMO có nhãn, không là giao dịch vận tải hoặc thu tiền thật. Sau thao tác nghiệp vụ, số liệu sẽ đổi theo DB; không nạp lại dữ liệu để ép về con số cũ. Các chuyến diễn ra từ 25/10 đến 03/11/2026, không tự dời ngày hoặc giả tiến độ khi đã quá giờ.

Tab **Vị trí thiết bị** hiển thị Maps chưa cấu hình. Chỉ tài xế được giao có quyền chủ động bật GPS, trong khung giờ được phép; trạng thái mặc định OFF. Khách chỉ xem khi đơn của chính mình CONFIRMED, đơn khác UNAVAILABLE. Không có GPS công khai, không tự phát vị trí khi mở trang. Khóa/ẩn tab hoặc mất mạng dừng watch; nếu request dừng không tới server, lease giới hạn dữ liệu còn hiển thị. Hợp đồng và giới hạn thiết bị ở [vận hành tracking](GIAM_SAT_VA_VAN_HANH.md#vị-trí-thiết-bị-và-google-maps--track01).

Danh mục có nhóm menu riêng và từng mục con độc lập cho admin. Khi cấp quyền mới: chọn **role → chức năng → hành động**; VIEW cho trang/menu, READ cho dữ liệu, CREATE/UPDATE/ASSIGN theo nhu cầu. Sau đó gán role cho account và membership/scoped role cho nhân sự nhà xe. Tạo permission trong DB không tự sinh chức năng mới; cấu trúc menu và guard bắt buộc vẫn khai báo trong source. Bản đồ 12 bảng và phần cấu hình cố định có chủ đích nằm ở [schema/phân quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#bản-đồ-bảng-và-ranh-giới-cấu-hình-phân-quyền).
