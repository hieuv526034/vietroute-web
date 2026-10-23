# Tiến độ và kế hoạch triển khai của nhóm VietRoute

Cập nhật 23/10/2026. Nền tảng đối chiếu tại `b1f3cde` sau [PR #13](https://github.com/hieuv526034/vietroute-web/pull/13), kế hoạch nhóm ở [PR #21](https://github.com/hieuv526034/vietroute-web/pull/21), UI quản trị đã merge tại [PR #22](https://github.com/hieuv526034/vietroute-web/pull/22). Đợt hoàn thiện menu/danh mục và cửa nghiệm thu trước nghiệp vụ tại [NGHIEM_THU_NEN_TANG.md](NGHIEM_THU_NEN_TANG.md). Chức năng mới chỉ trở thành Đã có trên main khi PR tương ứng merge. Tài liệu này theo dõi trạng thái; [PHAN_CONG.md](PHAN_CONG.md) là nguồn username/email và phạm vi phụ trách, [đặc tả](DAC_TA_HE_THONG_XE_KHACH.md) là nguồn luồng F01–F19 và ca nghiệm thu T01–T21.

## 1. Cách đọc trạng thái

| Trạng thái | Ý nghĩa |
|---|---|
| Đã có trên main | Có code đã merge và bằng chứng PR/kiểm tra; không tự có nghĩa đã deploy hoặc thử provider production |
| Một phần | Có API/base hoặc UI giới hạn; cột còn thiếu ghi rõ phần chưa triển khai |
| Chưa triển khai | Chưa có chức năng tương ứng trong source main đã đối chiếu; được giao trong kế hoạch |
| Chờ cấu hình ngoài app | Source/adapter/mẫu đã có; credentials, collector/receiver hoặc dịch vụ vận hành thật chưa được xác nhận |

Không tính phần trăm tiến độ bằng số dòng code, số nhánh hoặc số task. Có nhánh chuẩn bị, đặc tả, enum/role hoặc adapter không đồng nghĩa đã có nghiệp vụ hoàn chỉnh. Tài liệu chỉ xác nhận những gì có trên main tại mốc nêu trên, không kết luận về công việc chưa push hoặc nhánh riêng của thành viên.

APIJOURNAL01 đã có trên main tại `eeecd3c` qua [PR #25](https://github.com/hieuv526034/vietroute-web/pull/25), do `hieuv526034` thực hiện trên `hieuv526034/api-request-journal`: UI lịch sử/filter/modal, metadata controller/trace/lỗi, projection request/response và xuất cURL/Postman. Có kiểm quyền ADMIN + SYSTEM_READ, TTL/capacity từng node; không lưu bền vững/toàn cụm, không gateway hay body nguyên trạng. 85 test Java, 5 test Node, production WAR, Docker/PostgreSQL tạm và kiểm liên kết tài liệu đạt trước merge. Xem [hợp đồng và cách dùng](NHAT_KY_API_VA_DEBUG.md). Không chuyển việc nền tảng này thành đóng góp đã hoàn thành của thành viên khác.

Onboarding và rule format IDE tập trung tại [mục lục đọc theo nhóm/thứ tự](README.md) và [Format IntelliJ](FORMAT_INTELLIJ.md). Phần này giữ nguyên người phụ trách/tác giả của các module; không coi bổ sung tài liệu là đã triển khai nghiệp vụ.

## 2. Nền tảng đã thực hiện và phần còn thiếu

Phần code nền tảng bên dưới hiện được commit dưới định danh `hieuv526034 <hieuv526034@gmail.com>`. Không phân bổ lại các commit này cho người khác. Cột chủ trì tiếp theo ở mục 4 là kế hoạch, không phải tác giả đã hoàn thành.

| Mã | Phần việc | Trạng thái thực tế | Còn thiếu/giới hạn | Bằng chứng |
|---|---|---|---|---|
| BASE01 | Base entity/DTO/request/response/page, audit Instant, locale vi/en, validation, lỗi/API envelope và Swagger | Đã có trên main | Module mới phải dùng lại và bổ sung key/permission riêng | [PR #3](https://github.com/hieuv526034/vietroute-web/pull/3), [#4](https://github.com/hieuv526034/vietroute-web/pull/4), [#5](https://github.com/hieuv526034/vietroute-web/pull/5) |
| DB01 | JDBC/HikariCP, transaction, schema cấu hình một nơi, migration V001–V004/checksum/lock, seed admin/catalog | Đã có trên main | Chưa có bảng carrier/route/trip/booking/tracking; không tự chạy test trên Neon | [PR #7](https://github.com/hieuv526034/vietroute-web/pull/7), [#9](https://github.com/hieuv526034/vietroute-web/pull/9), [#11](https://github.com/hieuv526034/vietroute-web/pull/11) |
| AUTH01 | Đăng nhập/me/đổi mật khẩu/logout, JWT/BCrypt, CSRF, khóa sai và thu hồi token | Đã có trên main | Chưa có đăng ký CUSTOMER, cấp tài khoản theo nhà xe, quên mật khẩu/OTP | [PR #3](https://github.com/hieuv526034/vietroute-web/pull/3), [#8](https://github.com/hieuv526034/vietroute-web/pull/8) |
| ACCESS01 | API xem/gán quyền role và role user; hành động theo resource | Một phần | Đã bổ sung UI ma trận/gán role và tra tài khoản cho ADMIN; chưa cấp tài khoản mới/membership/scope nhà xe | [PR #8](https://github.com/hieuv526034/vietroute-web/pull/8), [#9](https://github.com/hieuv526034/vietroute-web/pull/9), [UI quản trị](QUAN_TRI_HE_THONG.md) |
| CAT01 | Catalog VEHICLE_TYPE/BOOKING_SOURCE: API đọc/tạo/sửa/xóa mềm, audit/version và seed | Một phần | UI `/admin` đã bổ sung tạo/sửa/xóa mềm/version, lọc nhóm và phân trang; source đợt hoàn thiện được bàn giao cùng PR này. Chưa có khôi phục UI hoặc danh mục địa điểm | [PR #3](https://github.com/hieuv526034/vietroute-web/pull/3), [#9](https://github.com/hieuv526034/vietroute-web/pull/9) |
| TRAFFIC01 | Quota IP, semaphore request/login/upload, giới hạn byte/pixel, timeout và trusted proxy | Đã có trên main | Quota memory theo từng node; chưa nghiệm thu tải production. Không bật Kafka/Redis | [PR #8](https://github.com/hieuv526034/vietroute-web/pull/8), [#11](https://github.com/hieuv526034/vietroute-web/pull/11), [#13](https://github.com/hieuv526034/vietroute-web/pull/13) |
| JOB01 | Queue PostgreSQL, SKIP LOCKED/lease/retry/DEAD, API/UI xem và retry, shutdown/retention | Đã có trên main | Worker tắt mặc định. UI thêm lọc trạng thái/phân trang/chi tiết/retry có version; at least once, chưa có gia hạn lease cho tác vụ dài | [PR #8](https://github.com/hieuv526034/vietroute-web/pull/8), [#13](https://github.com/hieuv526034/vietroute-web/pull/13), [UI quản trị](QUAN_TRI_HE_THONG.md) |
| MAIL01 | Gmail HTTPS OAuth2 và SMTP adapter, enqueue/outbox, trạng thái job | Chờ cấu hình ngoài app | Mail tắt mặc định; chưa thử gửi thật, chưa có template thư nghiệp vụ hay UI gửi mail riêng | [PR #8](https://github.com/hieuv526034/vietroute-web/pull/8), [hướng dẫn](NEN_TANG_MO_RONG.md) |
| MEDIA01 | API + UI `/media`, kiểm JPEG/PNG thật, owner/permission, metadata và cleanup bền vững | Chờ cấu hình ngoài app | Media tắt mặc định; chưa thử Cloudinary thật. Chưa gắn ảnh vào nhà xe/xe; không dùng cho giấy tờ riêng tư | [PR #11](https://github.com/hieuv526034/vietroute-web/pull/11), [hướng dẫn](CLOUDINARY_VA_INTELLIJ.md) |
| MONITOR01 | UI `/monitoring` vi/en, SYSTEM_READ; JVM/heap/GC/CPU/HTTP/pool/worker, token scrape riêng và probe DB opt-in | Đã có trên main | UI chỉ đọc và giữ 30 mẫu trong tab; không phải UI sửa cấu hình hệ thống, heap không phải tổng RAM container | [PR #13](https://github.com/hieuv526034/vietroute-web/pull/13) |
| ALERT01 | Dashboard Grafana mẫu, 12 luật cảnh báo và kiểm thử rule | Chờ cấu hình ngoài app | Chưa có collector/receiver thật hoặc thử thông báo end-to-end; DB probe tắt mặc định | [PR #13](https://github.com/hieuv526034/vietroute-web/pull/13), [vận hành](GIAM_SAT_VA_VAN_HANH.md) |
| BUILD01 | Maven WAR/Docker, cache CI, Render main/checksPass, loại test/launcher khỏi production | Đã có trên main | Chưa xác nhận Render service Live trong tài khoản; CI xanh không tự chứng minh deploy hoàn tất | [PR #1](https://github.com/hieuv526034/vietroute-web/pull/1), [#13](https://github.com/hieuv526034/vietroute-web/pull/13) |
| DEV01 | Run/Debug IntelliJ, formatter, constants và quy hoạch common, tài liệu tiếng Việt | Đã có trên main | Nâng Spring major là đợt riêng; không gọi stack hiện tại là toàn bộ công nghệ mới nhất | [PR #10](https://github.com/hieuv526034/vietroute-web/pull/10), [#11](https://github.com/hieuv526034/vietroute-web/pull/11), [#12](https://github.com/hieuv526034/vietroute-web/pull/12), [#13](https://github.com/hieuv526034/vietroute-web/pull/13) |

Bằng chứng kiểm tra tại PR #13: 73 test Java, 2 test Swagger, formatter, WAR production, smoke HTTP, kiểm rule và Docker integration đã qua. CI có DB tạm/mất-phục hồi DB, hai node và dừng một node; đây không phải nghiệm thu tải production, backup hay provider thật. Không có dữ liệu nhà xe/chuyến/booking demo được tạo chỉ vì base seed đã chạy.

## 3. Tổng quan theo từng thành viên

| Thành viên | Phần đã được xác nhận trên main | Công việc tiếp theo theo phân công | Điểm phối hợp |
|---|---|---|---|
| `hieuv526034` | Phần nền tảng ở mục 2, theo lịch sử commit/PR | Tenant/scope và cấp tài khoản, đăng ký CUSTOMER; cơ chế chỗ theo chặng, transaction tích hợp, Geolocation/Maps, vận hành và nghiệm thu | Chốt schema/permission/service contract; review phần khó; không nhận thay đóng góp thực của nhóm |
| `BangPhamIT` | Chưa ghi nhận commit nghiệp vụ của thành viên này tại mốc đối chiếu | Chuyến/phân xe-tài xế/lịch chồng/snapshot; booking WEB/PHONE và vòng đời đơn; tổng hợp báo cáo cùng Hieu | Dùng chung transaction/tồn chỗ với Hieu, không viết bộ đếm chỗ khác |
| `anhhoang104` | Chưa ghi nhận commit nghiệp vụ của thành viên này tại mốc đối chiếu | Hoàn thiện UI catalog; CRUD nhà xe, xe và thông tin tài xế, validation/trạng thái/phân trang | Auth cấp credential/quyền; Anh quản lý hồ sơ nghiệp vụ, không sửa password hash |
| `hiencoi250404-design` | Chưa ghi nhận commit nghiệp vụ của thành viên này tại mốc đối chiếu | Địa điểm, tuyến một chiều, điểm dừng/giờ/giá vi/en; UI tìm/chi tiết/lịch sử đơn khách | Gọi service chung của Bang/Hieu; phối hợp Maps, không tự tính lại chỗ ở JS |

Nhánh đã chuẩn bị chưa phải bằng chứng đã viết code. Dùng đúng email trong [PHAN_CONG.md](PHAN_CONG.md), commit/PR tiếng Việt, không đổi author để chia đóng góp trên giấy.

## 4. Backlog nghiệp vụ và đầu ra bàn giao

Các dòng dưới là **chưa triển khai**, trừ CAT02 đã được Hieu bổ sung UI trong đợt hoàn thiện nền tảng; bằng chứng nghiệm thu tại NGHIEM_THU_NEN_TANG.md, trạng thái main theo PR đi kèm. Người chủ trì là người dự kiến triển khai thực tế; khi PR merge phải cập nhật bằng chứng và tác giả thực, không đánh dấu xong theo kế hoạch.

| Mã | Chủ trì / phối hợp | API, UI và quy tắc cần hoàn thành | Phụ thuộc | Nghiệm thu gắn đặc tả |
|---|---|---|---|---|
| SCOPE01 | Hieu / Anh, Bang | Chốt quan hệ tài khoản–nhà xe, nguồn scope từ DB/principal, quyền theo resource; đọc/ghi có WHERE scope và kiểm quan hệ cha/con. ADMIN tạo/cấp OPERATOR qua auth, operator không tự chọn nhà xe khác | DB01, AUTH01, ACCESS01 | F02/F04; T01/T11: hai nhà xe không đọc/sửa chéo bằng cách đổi ID |
| AUTH02 | Hieu / Hien làm UI theo hợp đồng | Đăng ký CUSTOMER, validation/trùng username, server tự gán role; dùng login/CSRF/envelope hiện tại. Không nhận role/audit/tenant client | AUTH01; không bắt buộc chờ module chuyến | F01 và kiểm giả mạo role; quên mật khẩu/OTP là backlog riêng, chưa cam kết trong bản đầu |
| AUTH03 | Hieu / Anh | API cấp/khóa tài khoản OPERATOR/DRIVER gắn membership và hồ sơ đúng nhà xe; giữ credential trong auth, quy tắc admin cuối/token revocation | SCOPE01, hợp đồng CARRIER01/DRIVER01 | F02/F04; không cấp tài khoản ngoài scope, khóa không xóa lịch sử |
| CAT02 | Hieu đã bổ sung source / Anh nghiệm thu, tích hợp tiếp | UI tạo/sửa/xóa mềm/xác nhận/version/conflict, lọc nhóm và phân trang đã có trong đợt nền tảng. Anh kiểm/tích hợp khi dùng cho xe; không phân bổ commit của Hieu thành đóng góp đã làm của Anh | CAT01 | F03; conflict 409, thiếu quyền bị chặn cả API, không phá tham chiếu |
| CARRIER01 | Anh / Hieu | Nhà xe CRUD, hồ sơ/liên hệ/trạng thái, UI ADMIN/OPERATOR theo phạm vi, code duy nhất và ảnh logo public qua mediaId được kiểm owner | SCOPE01, AUTH03 contract; MEDIA01 khi bật ảnh | F02; T01/T11, khóa thay vì xóa dữ liệu còn liên quan |
| VEHICLE01 | Anh / Hieu | Xe thuộc nhà xe: mã/biển số/loại xe/sức chứa/trạng thái/ảnh public, API+form+phân trang+version | CARRIER01, CAT02; MEDIA01 khi dùng ảnh | F04; biển số trùng, capacity <=0, đổi carrierId giả và xe đang được sử dụng |
| DRIVER01 | Anh / Hieu sở hữu auth | Hồ sơ tài xế/liên hệ/trạng thái, liên kết tài khoản do auth cấp, UI đúng nhà xe; không upload giấy tờ riêng tư qua media public | CARRIER01, AUTH03 | F04; tài khoản/hồ sơ khác nhà xe, khóa và giữ lịch sử |
| LOCATION01 | Hien / Hieu review schema | Tỉnh/thành/địa điểm, tên vi/en, địa chỉ/tọa độ hợp lệ, UI CRUD và trạng thái; dữ liệu đang tham chiếu không bị xóa mất | BASE01, chốt schema/quyền | F03; địa điểm không tồn tại, tọa độ sai, dữ liệu đang dùng |
| ROUTE01 | Hien / Anh, Bang | Tuyến một chiều thuộc nhà xe, >=2 điểm theo thứ tự, ngày-giờ tương đối qua đêm và giá theo mốc, API+UI thêm/sắp điểm; hai chiều là hai bản ghi riêng | SCOPE01, CARRIER01, LOCATION01 | F05; T02/T03/T09/T13; không đảo dữ liệu cũ khi sửa tuyến |
| TRIP01 | Bang / Anh, Hien | Tạo chuyến từ tuyến, snapshot điểm/giờ/giá, phân xe/tài xế, vòng đời DRAFT/OPEN/IN_PROGRESS/COMPLETED/CANCELLED, API+UI; kiểm lịch chồng trong transaction để hai request không lọt | VEHICLE01, DRIVER01, ROUTE01 | F06; T03/T13/T14/T17; Instant và cùng nhà xe, active, trạng thái đúng |
| CAPACITY01 | Hieu / Bang | Tồn chỗ theo từng chặng, min số chỗ còn trên đoạn; transaction/khóa dùng chung và rollback. Thống nhất thứ tự khóa trip → booking → segments tăng dần; API/service nhận cùng Connection | Chốt schema TRIP01/BOOKING01 trước, sau đó dữ liệu chuyến | T04–T08/T10/T15; xác nhận đồng thời chỗ cuối không oversell, trả chỗ đúng một lần |
| BOOKING01 | Bang / Hieu | Tạo yêu cầu WEB PENDING, OPERATOR xác nhận/từ chối, CUSTOMER/OPERATOR hủy, PHONE không bắt buộc tài khoản khách, history/idempotency/audit và UI; gọi CAPACITY01 cùng transaction | AUTH02, SCOPE01, TRIP01, CAPACITY01 | F09–F12/F19; T06–T12/T15/T19–T21; PENDING không giữ chỗ |
| SEARCH01 | Hien / Bang, Hieu | UI+API tìm theo ngày tại điểm đón/số chỗ/nhà xe/loại xe/giá, phân trang; đúng chiều và điểm chưa đóng. Chỗ/giá do server tính | TRIP01, LOCATION01, CAPACITY01 | F07; T02–T05/T09/T19; chuyến qua đêm và hết chỗ một chặng |
| DETAIL01 | Hien / Hieu phụ trách Maps | Trang danh sách/chi tiết nhà xe và chuyến, điểm dừng/giờ/giá/chỗ/liên hệ, form yêu cầu đặt; phần Maps/tracking ghép khi API có, luôn có danh sách văn bản | CARRIER01, SEARCH01, BOOKING01; TRACK01 cho vị trí thật | F08; T16; không giả định đã có Maps chỉ vì có khung giao diện |
| HISTORY01 | Hien / Bang | Lịch sử và chi tiết đơn khách, trạng thái/lý do/timeline/liên hệ, gọi hủy theo API chung; API chỉ đọc đơn của chủ sở hữu | BOOKING01 | F17; T11/T20; không lộ đơn người khác khi đổi ID |
| DRIVERUI01 | Hieu / Hien phối hợp giao diện | Chuyến được giao và khách đón/trả theo điểm, bắt đầu/đến/rời/kết thúc với quyền tài xế, cập nhật trạng thái đúng thứ tự | DRIVER01, TRIP01, BOOKING01; hợp đồng tích hợp Bang | F13/F14; T10/T12/T17; tài xế không thấy khách chuyến khác |
| TRACK01 | Hieu / Hien ghép chi tiết | Google Maps + Geolocation website, lấy vị trí khi tài xế cho phép, lưu thời điểm nhận/độ chính xác; marker thật, stale/fallback/mất mạng. Không suy luận đóng điểm hoặc hủy đơn chỉ từ tọa độ | TRIP01, DRIVERUI01, LOCATION01; cấu hình Maps/HTTPS bên ngoài | F15/F08 và mục 17; T16; thử từ chối quyền, tab nền, mẫu cũ/sai thứ tự và sai tài xế |
| INTEGRATION01 | Hieu + Bang / cả nhóm nghiệm thu | Hủy chuyến/đóng điểm và đơn liên quan trong luồng transaction thống nhất; không để xác nhận lọt, không trả chỗ hai lần; history/outbox nếu dùng thông báo | TRIP01, CAPACITY01, BOOKING01, DRIVERUI01 | F16; T07/T08/T10/T15/T17 |
| REPORT01 | Hieu + Bang / Anh, Hien phối hợp UI | Báo cáo cơ bản chuyến/đơn/chỗ/giá trị đặt và tiền thực thu nếu có nghiệp vụ ghi nhận thu; ADMIN toàn hệ thống, OPERATOR chỉ nhà xe mình | Dữ liệu TRIP01/BOOKING01 và quy tắc ghi nhận thu được chốt | F18; T18; không coi PENDING/giá niêm yết là tiền đã thu, không thêm cổng thanh toán vào bản đầu |
| ACCEPT01 | Cả nhóm, Hieu tổng hợp | Dữ liệu demo tách production, ca T01–T21 và tracking mục 17, hướng dẫn chạy/bảo vệ, minh chứng từng PR và báo cáo mỗi người | Các module liên quan đã merge | Chạy nhiều phiên, concurrency/rollback/scope thật, không gắn dữ liệu giả với doanh nghiệp thật |

## 5. Phần UI quản trị và công việc vận hành

OPSUI01/ACCESSUI01/CONFIGUI01 đã được Hieu bổ sung source UI theo các hàng dưới; việc phối hợp của thành viên khác vẫn là kế hoạch. Các dòng provider/cảnh báo/backup còn là backlog ngoài app, không coi đã vận hành chỉ vì có UI hoặc file mẫu.

| Mã | Chủ trì / phối hợp | Đã có | Cần làm và giới hạn |
|---|---|---|---|
| OPSUI01 | Hieu triển khai / Anh phối hợp nghiệm thu dự kiến | API và UI list/status filter/page/detail/retry DEAD có xác nhận, version và JOB_READ/JOB_RETRY | Đã có trên main qua PR #22. Không hiện payload/PII, không retry hàng loạt; cần bật worker để xử lý job |
| ACCESSUI01 | Hieu triển khai / Hien phối hợp nghiệm thu dự kiến | UI ma trận/gán quyền và tra/gán role tài khoản, version/conflict/CSRF, service kiểm quyền | Đã có trên main qua PR #22. ADMIN role bảo vệ, không tự sửa role; tra tài khoản chỉ ADMIN + ACCESS_READ; chưa tạo tài khoản mới hoặc scope nhà xe |
| CONFIGUI01 | Hieu triển khai | API/UI snapshot cấu hình whitelist với SYSTEM_READ | Đã có trên main qua PR #22, chỉ đọc, không serialize Settings/secret. Sửa Environment vẫn qua IDE/Render và restart; chưa có lưu cấu hình runtime hoặc API sửa env |
| MAILTPL01 | Hieu + Bang | Outbox và transport Gmail/SMTP | Template vi/en cho sự kiện booking theo scope đã chốt; thử bằng transport giả rồi staging, chưa có UI gửi mail tùy ý hay reset password/OTP |
| DEPLOY01 | Hieu | Docker/Blueprint và adapter | Xác nhận service Live/env, bật worker/mail/media khi đủ điều kiện; không tự mua dịch vụ hoặc coi credentials có sẵn |
| ALERTLIVE01 | Hieu | Metrics, dashboard/rule mẫu | Collector/receiver ngoài app, thử thông báo down/recovered và đối chiếu RAM container. Chưa kích hoạt nhận cảnh báo thật |
| RECOVERY01 | Hieu / cả nhóm nghiệm thu | Hướng dẫn xử lý sự cố | Chốt RPO/RTO/backup/retention, thử restore DB staging; chưa có backup tự động hoặc restore production trong CI |
| SCALE01 | Hieu + Bang | Khóa/lease/CI hai node và quota local | Kiểm tải staging, tổng pool, rolling deploy/schema tương thích, ingress quota toàn cụm khi cần; chưa cần Kafka/Redis |
| UPGRADE01 | Hieu | Stack Spring MVC 6/Tomcat 10.1 theo quy ước | Đánh giá vòng đời/nâng major bằng PR riêng và kiểm tương thích; chưa quyết định chuyển stack trong backlog nghiệp vụ |

## 6. Thứ tự bắt đầu và điểm bàn giao

```mermaid
flowchart TD
    Base[Nền tảng đã có trên main] --> Scope[SCOPE01 và hợp đồng schema quyền]
    Base --> Register[AUTH02 đăng ký khách]
    Base --> Catalog[CAT02 UI danh mục]
    Base --> Location[LOCATION01 địa điểm]
    Scope --> Carrier[CARRIER01 và AUTH03]
    Carrier --> Fleet[VEHICLE01 và DRIVER01]
    Carrier --> Route[ROUTE01 tuyến]
    Location --> Route
    Fleet --> Trip[TRIP01 chuyến và snapshot]
    Route --> Trip
    Trip --> Capacity[CAPACITY01 chỗ theo chặng]
    Capacity --> Booking[BOOKING01]
    Register --> Booking
    Capacity --> Search[SEARCH01 và DETAIL01]
    Booking --> History[HISTORY01]
    Booking --> Driver[DRIVERUI01]
    Driver --> Track[TRACK01 vị trí và Maps]
    Booking --> Integration[INTEGRATION01]
    Driver --> Integration
    Integration --> Accept[REPORT01 và ACCEPT01]
```

Theo yêu cầu ngày 23/10: hoàn thiện và nghiệm thu source nền tảng/UI quản trị trước khi khởi động các đợt nghiệp vụ dưới đây. Việc bổ sung UI không tự bật provider/cảnh báo thật hoặc bắt đầu module nhà xe.

**Đợt 1 sau nền tảng:** Hieu chốt SCOPE01 và hợp đồng cấp tài khoản, auth/schema; Anh nghiệm thu/tích hợp CAT02 đã bổ sung và chuẩn bị form nhà xe theo hợp đồng; Hien làm LOCATION01; Bang đọc luồng, chuẩn bị thiết kế trạng thái/snapshot/lịch chồng và ca test TRIP01/BOOKING01. Thiết kế chưa được ghi thành chức năng hoàn thành. Hieu có thể khảo sát rủi ro Geolocation sớm nhưng chưa ghép tracking giả vào nghiệp vụ.

**Đợt 2:** Nhà xe/xe/tài xế và tuyến; Hieu hỗ trợ AUTH03 và review scope. Trước khi merge chốt migration version mới, permission/key vi/en, ID/quan hệ và contract; không hai người cùng viết migration trùng số. Các migration đã có giữ nguyên.

**Đợt 3:** Bang triển khai chuyến, Hieu triển khai tồn chỗ dựa trên schema/hợp đồng thống nhất; sau đó booking, tìm kiếm và chi tiết. Frontend có thể dựng layout nhưng chưa đánh dấu API dùng được khi còn mock. Mọi dữ liệu mô phỏng phải gắn nhãn.

**Đợt 4:** Lịch sử khách, tài xế/Maps, hủy chuyến xuyên module, báo cáo và nghiệm thu. Chưa ấn định lại ngày hoàn thành cá nhân; kế hoạch 14 ngày trong đặc tả là phương án ban đầu, không tự suy diễn rằng các mốc ấy đã đạt.

## 7. Quy trình cập nhật và định nghĩa hoàn thành

1. Trước khi code: lấy main mới nhất, chọn một mã việc và tạo nhánh `<username>/<mã-việc>-<mô-tả>`. Nhánh mới của Bang dùng `BangPhamIT/`, không lấy nhãn cũ `bangpham.it/` làm username. Không tạo nhánh codex hoặc thay author cho người khác.
2. Ghi đầu ra: API/DTO/Swagger, quyền/scope, UI và trạng thái lỗi, migration mới nếu cần, luồng transaction, ca kiểm tra và tài liệu tiếng Việt. Chủ trì chịu cả code và phần giải thích để bảo vệ, không chỉ giao một người làm báo cáo.
3. PR ghi mã task, tác giả thực tế và kết quả kiểm tra; Hieu review schema/security/transaction, Bang review chuyến/booking, Anh/Hien review UI/validation theo phạm vi. Issue/assignee trên GitHub phải được tạo/gán thực tế mới ghi liên kết; bảng này không tự tạo Issue hoặc gửi thông báo cho thành viên.
4. Chỉ chuyển thành Đã có trên main khi PR đã merge, check liên quan qua, đủ các đầu ra cam kết. Task có UI thì API-only là Một phần; provider chưa cấu hình ghi Chờ cấu hình ngoài app. Docs/test-only không build Docker/Render; check tài liệu riêng chỉ kiểm liên kết nội bộ.
5. Cập nhật dòng task với ngày, PR/commit, người thực hiện, ca đã kiểm, phần còn thiếu và phụ thuộc được mở. Không sửa lùi số test lịch sử hoặc đánh dấu mọi ca đặc tả đã qua chỉ vì base CI xanh.

Mẫu ghi bàn giao cho mỗi task:

| Mã | Trạng thái | Người thực hiện | PR/commit | API/UI/SQL | Kiểm tra thực tế | Còn thiếu | Phụ thuộc được mở |
|---|---|---|---|---|---|---|---|
| `<mã>` | `<trạng thái>` | `<username thực>` | `<liên kết>` | `<đầu ra>` | `<lệnh/ca/kết quả>` | `<giới hạn>` | `<mã task>` |

Ưu tiên hiện tại là nghiệm thu nền tảng/UI quản trị. Khi chốt xong mới bắt đầu SCOPE01, AUTH02, LOCATION01 và các hạng mục theo phụ thuộc; lượt hoàn thiện UI này chưa bắt đầu code nghiệp vụ nhà xe/chuyến/booking.
