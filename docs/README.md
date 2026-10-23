# Bắt đầu tại đây — tài liệu VietRoute

Nếu mới vào nhóm, đọc theo **lộ trình 1 → 6** bên dưới; không cần đọc toàn bộ tài liệu vận hành trước khi chạy được app. Tài liệu được chia nhóm ở mục lục này; giữ đường dẫn file hiện có để link từ PR/Issue và hướng dẫn trong code tiếp tục dùng được.

## Lộ trình cho thành viên mới

| Thứ tự | Đọc gì | Sau bước này cần hiểu/làm được |
|---|---|---|
| 1. Hiểu dự án | [README dự án](../README.md), [Tổng quan hệ thống](TONG_QUAN_HE_THONG.md) | Stack MVC/JDBC/WAR, common/auth/business, phần đã có và chưa triển khai |
| 2. Chạy và format | [Chạy trong IntelliJ](CLOUDINARY_VA_INTELLIJ.md), [Format IntelliJ](FORMAT_INTELLIJ.md) | Bật profile local, cấp env riêng, Run/Debug app và Run Format; biết build không tự đọc `.env` |
| 3. Hiểu một request | [Luồng xử lý base](LUONG_XU_LY_BASE.md), [Hợp đồng API](API_BASE.md) | HTTP → security → controller → service/transaction → JDBC → envelope/locale/traceId |
| 4. Viết đúng quy ước | [Quy ước phát triển](QUY_UOC_PHAT_TRIEN.md), [Constants/package web](CONSTANTS_VA_CAU_TRUC_WEB.md), [Schema và phân quyền](SCHEMA_VA_PHAN_QUYEN.md) | DTO/Instant/audit/version, SQL không hardcode schema, quyền theo hành động và scope |
| 5. Nhận việc | [Tiến độ nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md), [Phân công](PHAN_CONG.md), [Đặc tả nghiệp vụ](DAC_TA_HE_THONG_XE_KHACH.md) | Việc mình nhận, phụ thuộc, tiêu chí nghiệm thu; kế hoạch không phải đóng góp đã hoàn thành |
| 6. Bàn giao | [Quy trình Git/PR](../CONTRIBUTING.md), [Nghiệm thu nền tảng](NGHIEM_THU_NEN_TANG.md) | Nhánh đúng người, commit/PR tiếng Việt, format/check và kiểm tra đúng phạm vi trước merge |

Lần đầu chạy local không bật migration/seed trên Neon để thử. Xin cấu hình môi trường riêng theo người phụ trách; không đưa secret vào Git/ảnh chụp/tài liệu. `/health` lên không chứng minh đăng nhập hoặc DB hoạt động. Build/test thường lệ dùng DB giả/tạm, không production.

## Nhóm A — Kiến trúc và nền tảng

| Tài liệu | Khi nào đọc |
|---|---|
| [Tổng quan hệ thống](TONG_QUAN_HE_THONG.md) | Cửa vào kiến trúc, package, luồng và giới hạn nhiều node |
| [Luồng xử lý base](LUONG_XU_LY_BASE.md) | Học một request từ filter đến lỗi/response; đọc trước khi thêm controller/service |
| [Nền tảng mở rộng](NEN_TANG_MO_RONG.md) | Trước khi thêm job/worker/mail, quota hoặc permission |
| [Schema và phân quyền](SCHEMA_VA_PHAN_QUYEN.md) | Trước khi viết SQL, migration, CRUD hoặc đổi schema môi trường |
| [Database và migration](../database/README.md) | Chạy migration lúc startup, history/checksum/lock và quy trình dữ liệu |

## Nhóm B — Viết code và giao diện

| Tài liệu | Khi nào đọc |
|---|---|
| [Quy ước phát triển](QUY_UOC_PHAT_TRIEN.md) | Trước mọi PR: package, base/audit, DTO/Instant, validation và comments |
| [Format IntelliJ](FORMAT_INTELLIJ.md) | Thiết lập máy mới, reformat khi sửa và chốt format trước commit |
| [Hợp đồng API/base/auth](API_BASE.md) | Request/response/error, locale, JWT/cookie/CSRF, Swagger |
| [Constants và cấu trúc web](CONSTANTS_VA_CAU_TRUC_WEB.md) | Khi thêm env/route/regex/header hoặc class common.web |
| [Tab và modal](TAB_VA_MODAL.md) | Khi thêm trang nhiều nhóm, form thêm/sửa/gán/upload, lỗi và focus |
| [Cloudinary và IntelliJ](CLOUDINARY_VA_INTELLIJ.md) | Chạy app từ IDE hoặc tích hợp ảnh công khai; phần provider chỉ đọc khi cần |

## Nhóm C — Dùng màn hệ thống và debug

| Tài liệu | Khi nào đọc |
|---|---|
| [Quản trị hệ thống](QUAN_TRI_HE_THONG.md) | Dùng UI job/retry, phân quyền/gán role và snapshot cấu hình chỉ đọc |
| [Nhật ký API/Postman](NHAT_KY_API_VA_DEBUG.md) | Có traceId/lỗi API: tra controller/status, request/response đã lọc, copy cURL/import collection |
| [Giám sát và vận hành](GIAM_SAT_VA_VAN_HANH.md) | Đọc heap/CPU/pool/HTTP, xử lý 503, metrics/cảnh báo; phân biệt cấu hình mẫu với dịch vụ thật |

Trang nhật ký cần ADMIN + SYSTEM_READ; lịch sử lưu PostgreSQL dùng chung, mặc định 7 ngày; không cache/queue lịch sử trong RAM hoặc thay thế gateway/audit bất biến. Nếu DB hỏng khiến JWT không xác thực được, dùng Logs đúng node/metrics vận hành, không bỏ qua kiểm quyền để mở nhật ký.

## Nhóm D — Triển khai và vận hành

| Tài liệu | Khi nào đọc |
|---|---|
| [Deploy Render](DEPLOY_RENDER.md) | Người phụ trách deploy: Docker, env, CI/build filters và cách bật dịch vụ |
| [Giám sát và vận hành](GIAM_SAT_VA_VAN_HANH.md) | Collector/receiver, nhiều node, sự cố, backup/restore staging |
| [Cloudinary và IntelliJ](CLOUDINARY_VA_INTELLIJ.md) | Thiết lập ảnh/provider và phân biệt môi trường local với Render |
| [Nền tảng mở rộng](NEN_TANG_MO_RONG.md) | Bật worker/Gmail có kiểm soát; queue/retry/idempotency |

Có adapter/config mẫu không đồng nghĩa Gmail/Cloudinary/cảnh báo/backup đã chạy thật. Không tăng pool/thread theo cảm tính; tổng connection tính mọi node và worker. Kafka/Redis chưa bật trong giai đoạn hiện tại.

## Nhóm E — Kế hoạch, nghiệp vụ và nghiệm thu

| Tài liệu | Nguồn sự thật cho nội dung nào |
|---|---|
| [Tiến độ và kế hoạch nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md) | Đã làm/chưa làm, phụ thuộc/điều kiện bàn giao và PR bằng chứng |
| [Phân công thành viên](PHAN_CONG.md) | Username/email đúng, người chủ trì và nhánh của từng phần |
| [Đặc tả hệ thống xe khách](DAC_TA_HE_THONG_XE_KHACH.md) | Luồng nghiệp vụ F01–F19 và ca nghiệm thu T01–T21 |
| [Nghiệm thu nền tảng](NGHIEM_THU_NEN_TANG.md) | Màn đã có, kiểm tra thực tế và phần phải chốt trước nghiệp vụ |
| [Quy trình đóng góp](../CONTRIBUTING.md) | Git identity, nhánh/PR/commit tiếng Việt và ghi nhận tác giả |

## Nhóm F — Bối cảnh và lịch sử quyết định

| Tài liệu | Cách đọc |
|---|---|
| [Rà soát base](RA_SOAT_BASE.md) | Danh sách rà soát tại mốc ghi trong file; đối chiếu tiến độ/PR mới trước khi coi còn thiếu |
| [Khởi tạo GitHub](KHOI_TAO_GITHUB.md) | Hướng dẫn bootstrap repo ban đầu; không chạy lại để ghi đè repo/tác giả hiện có |

Các file có ngày/commit đối chiếu giữ bối cảnh ở thời điểm đó. Muốn biết hiện đã merge hay đang chờ, xem tiến độ cùng PR, không suy ra trạng thái từ một đề xuất cũ.

## Đọc thêm theo người phụ trách

| Người | Sau lộ trình chung, ưu tiên |
|---|---|
| `hieuv526034` | Schema/transaction/scope, worker và tích hợp, Render/monitoring/debug; chốt hợp đồng dùng chung trước CRUD nghiệp vụ |
| `BangPhamIT` | Đặc tả trip/booking, API và quyền/scope, hợp đồng tồn chỗ/khóa do Hieu phụ trách; không tự tạo cơ chế tồn chỗ thứ hai |
| `anhhoang104` | Mẫu catalog, carrier/xe/tài xế theo scope, validation/audit/version và tab/modal |
| `hiencoi250404-design` | Địa điểm/tuyến/điểm dừng, UI search/chi tiết/lịch sử, locale/tab/modal và hợp đồng API từ Bang/Hieu |

Bảng này là hướng đọc theo phân công, không khẳng định nghiệp vụ đã làm xong. Khi thêm tài liệu, đưa link vào đúng nhóm ở đây và cập nhật nguồn phụ trách; không sao chép nhiều bản quy tắc dễ lệch nhau.
