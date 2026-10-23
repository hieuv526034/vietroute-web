# Giao diện quản trị hệ thống

Cập nhật 23/10/2026. Các màn này hoàn thiện nền tảng trước nghiệp vụ nhà xe/chuyến/booking. Dùng menu **Hệ thống** sau khi đăng nhập; link chỉ xuất hiện theo quyền hiện hành. Backend vẫn kiểm quyền kể cả khi nhập URL hoặc gọi API trực tiếp.

## Các màn đã triển khai trong source

| Trang | Quyền xem | Chức năng |
|---|---|---|
| `/operations/jobs` | JOB_READ | Lọc trạng thái, phân trang, xem metadata và mã lỗi, retry DEAD khi có JOB_RETRY |
| `/operations/access` | ACCESS_READ | Ma trận quyền của role; sửa role thường nếu có ACCESS_ASSIGN/ACCESS_WRITE trong phạm vi cho phép |
| `/operations/configuration` | SYSTEM_READ | Xem snapshot whitelist thông số không nhạy cảm của instance; chỉ đọc |
| `/monitoring` | SYSTEM_READ | JVM/heap/CPU/HTTP/pool/worker và mẫu probe DB opt-in; hướng dẫn tại [giám sát](GIAM_SAT_VA_VAN_HANH.md) |

Mỗi màn có vi/en, trạng thái tải/rỗng/lỗi, nút làm mới và menu theo quyền. JavaScript dùng Axios/CSRF/lỗi chung, không localStorage token, không đưa giá trị server vào innerHTML. Fieldset khóa trong lúc xử lý để hạn chế gửi lặp. Quyền hiển thị UI chỉ hỗ trợ thao tác; service là nơi quyết định quyền thực tế.

## Quản lý job

API giữ hợp đồng list/detail/retry hiện hành; list thêm query `status=QUEUED|RUNNING|SUCCEEDED|DEAD` tùy chọn. Không có status nghĩa xem tất cả. Page từ 0, size 1–100; UI dùng 20 dòng. Filter không hợp lệ trả 400, thiếu JOB_READ trả 403. DAO bind tham số và dùng search_path cấu hình, không nhận cột SQL từ client.

Chọn Chi tiết để đọc ID/type/status/attempts, availableAt/lease, mã lỗi, thời điểm/audit/version. Không trả payload, email người nhận, dedup key hoặc response provider. Chỉ DEAD và JOB_RETRY mới hiện thao tác retry khả dụng; UI yêu cầu xác nhận rồi gửi `POST /api/v1/admin/jobs/{id}/retry` với `{"version":<version đang xem>}`. Backend tiếp tục kiểm trạng thái/version trong SQL.

409 nghĩa dữ liệu/trạng thái đã đổi: UI hiển thị lỗi trong hộp chi tiết, không tự retry, người dùng làm mới rồi xem lại. Thành công giữ ID/dedup, queue nhận lại khi worker bật. At least once vẫn có thể lặp tác động provider nếu ACK trước đó mất; kiểm receipt/kết quả gửi trước khi retry. Không có nút retry hàng loạt hoặc xóa job/PII tùy ý.

## Ma trận quyền và role tài khoản

Ma trận lấy registry có tên vi/en và version role từ API hiện hành. ADMIN có quyền cứu hộ cố định: không cho sửa. Người chỉ ACCESS_READ xem được nhưng không lưu. Người được ủy quyền chỉ gán quyền thuộc tập họ đang có; nếu role hiện hành chứa quyền ngoài tập của mình, UI cho xem và yêu cầu ADMIN xử lý, backend vẫn chặn việc nâng quyền. Request PUT thay toàn bộ tập quyền, có version và CSRF; tập rỗng nghĩa thu hồi quyền chức năng của role thường.

Sau khi lưu mapping, UI lấy lại `/auth/me` vì thay quyền có hiệu lực ở request kế tiếp. Nếu tự mất ACCESS_READ qua role của mình, chuyển về trang tài khoản; không để UI tiếp tục coi quyền cũ còn hợp lệ. Làm mới lấy lại quyền trước khi dựng controls. Conflict không được gửi lại mutation tự động.

Phần tra/gán role tài khoản chỉ mở cho ADMIN có ACCESS_READ. API bổ sung:

```text
GET /api/v1/admin/access/users?username=Ad&page=0&size=20
```

`username` là prefix không phân biệt hoa/thường, chữ/số tối đa 32 ký tự; rỗng để xem tất cả. Chọn tường minh id/username/displayName/active/roles/version/createdAt/updatedAt; không đọc password hash vào DTO hoặc trả token/credential. Người có ACCESS_READ nhưng không ADMIN không được liệt kê tài khoản. Query dùng PreparedStatement, không wildcard do người dùng cung cấp và không N+1 theo từng user. Nếu dữ liệu lớn cần đo query plan và cân nhắc index prefix qua migration mới, không sửa migration đã áp dụng.

Chọn tài khoản để sửa các role; không tự sửa role bản thân. Ghi dùng API PUT đã có, cần thêm ACCESS_ASSIGN/ACCESS_WRITE và role ADMIN, version hiện hành; backend bảo vệ admin cuối và thu hồi token cũ của user đích. UI không cấp tài khoản mới, đổi mật khẩu người khác, gắn nhà xe hoặc thay scope; các phần đó theo AUTH02/AUTH03/SCOPE01 trong [kế hoạch nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md).

## Trang cấu hình an toàn

`GET /api/v1/admin/system/configuration` dùng SYSTEM_READ và envelope BaseController. DTO typed whitelist: môi trường/cookie/initializer/lifetime, pool/timeout, quota/concurrency/body, worker/lease/retention, cờ mail/media và cấu hình probe. Không serialize Settings, env map, JDBC URL, DB username/password/schema, mailbox, SMTP host/password, JWT key, Cloudinary secret, CIDR proxy hoặc token metrics.

Chỉ có GET; POST/PUT không phải API thay cấu hình. Sửa biến môi trường trong IDE/Render, restart/deploy và đọc lại snapshot trên đúng node. `enabled` nghĩa cờ bật tại startup, không khẳng định worker đang chạy hoặc provider đã kết nối thành công. Runtime xem màn Giám sát. Không có nút thử gửi mail/upload thật hay tự gọi provider khi mở trang. Không cần bật Kafka/Redis, worker, mail, media hoặc DB probe để render trang.

API cấu hình đọc Settings trong RAM; xác thực JWT/cookie vẫn tra DB như các API người dùng khác. Metrics token riêng chỉ đọc `/internal/metrics`, không mở các trang quản trị hoặc API cấu hình. Không coi UI cấu hình chỉ đọc là cơ chế lưu cấu hình runtime đa node.

## Kiểm tra và giới hạn

Kiểm local bằng MockMvc cho quyền xem từng trang vi/en, quyền service, filter/size sai bị chặn trước DB, config không lộ sentinel secret và OpenAPI có endpoint/DTO. Kiểm SQL và API qua PostgreSQL tạm trong Docker CI: tra prefix/page/no result, role/version, filter job/trạng thái lỗi, cấu hình whitelist và render trang bằng cookie đã đăng nhập. CI hiện hành kiểm role assignment, CSRF, admin bảo vệ và stale retry dùng chung.

Chrome headless local với HTML Thymeleaf và fixture API riêng đã kiểm click role/user assignment có CSRF/version, chặn ADMIN/bản thân, conflict retry hiện trong dialog không tự retry, config chỉ đọc và mobile không tràn ngang. Đây là bằng chứng UI trên fixture; SQL/auth thật vẫn cần Docker CI, không coi fixture là kết quả database production.

Các kiểm tra này không chứng minh provider production, cảnh báo thật, backup hoặc tải nhiều node. Tài liệu này mô tả chức năng source; kết quả CI và merge xem PR tương ứng. Không test trên Neon production. Khi bàn giao ghi đúng ca đã chạy và phân biệt HTML render, browser fixture với API/SQL thật.

## Hoàn thiện trước nghiệp vụ

Đợt tiếp theo bổ sung menu không gạch chân, active/focus/đóng Escape, lối tắt quản trị, UI danh mục sửa/xóa mềm/phân trang và trạng thái dịch vụ ảnh tắt. Đọc [nghiệm thu nền tảng](NGHIEM_THU_NEN_TANG.md) để xem phạm vi hoàn thành, ca kiểm giao diện và phần vận hành còn chờ cấu hình ngoài app. Không coi đã có đầy đủ tài khoản/scope nhà xe hoặc provider thật từ đợt UI này.

Đợt tab/modal: [Tab và modal dùng chung](TAB_VA_MODAL.md) ghi các màn đã áp dụng, luồng mở/lưu/hủy, lỗi trong dialog và hướng dẫn để business kế thừa.
