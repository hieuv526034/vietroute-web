# Trạng thái nghiệm thu VietRoute

Bản mô tả hệ thống dùng chung cho nhóm. Phân công và đường học ở [PHAN_CONG](PHAN_CONG.md), thao tác trình bày ở [DEMO_VA_HOI_DAP](DEMO_VA_HOI_DAP.md). Lịch sử thay đổi tra Git và PR.

## Phạm vi sản phẩm

| Khối | Chức năng |
|---|---|
| Auth | Username/password, JWT cookie, khóa sai, đổi mật khẩu, User → Role → Function → Menu/API, scope nhà xe |
| Catalog | Một bảng Category, nhóm theo ref_type, props JSONB, tab và cây tải theo cha, tìm recursive kèm tổ tiên, ghim tọa độ địa điểm |
| Carrier | Nhà xe, xe, tài xế và tài khoản được gán |
| Trip | Mẫu tuyến, điểm dừng, snapshot chuyến, tồn chỗ từng chặng, tiến độ do tài xế xác nhận |
| Booking | WEB/PHONE, PENDING/CONFIRMED, hủy, ghi nhận thu/hoàn thủ công, khóa và idempotency khi đồng thời |
| Report | Dashboard và tổng hợp theo ngày/nhà xe trên bảng nghiệp vụ |
| Common | Cấu hình, migration, transaction, validation, locale, lỗi API và journal đã lọc |

Schema có 22 bảng gồm auth8, business12, journal1 và history1. Java production gồm auth46, business125 và common58. Business có14 projection với getter theo alias. Tám interface Repo auth và10 business dùng Spring Data native SQL, JDBC nghiệp vụ chỉ ghi batch trong CustomRepo. PostgreSQL là môi trường chạy, Oracle26ai mới có cấu hình driver và đang chặn runtime.

Dữ liệu nền được tạo cho việc học và trình bày, gồm7 nhà xe/25 xe/25 tài xế/28 mẫu tuyến/646 chuyến/3866 đơn và46 tài khoản sử dụng trên DB mới khi bật đủ cờ init. Duy Long là một nhà xe trong bộ dữ liệu. Tỉnh/thành34 và xã/phường3321 lấy từ JSON đóng trong source. Init chạy khi bật cờ, idempotent, giữ mật khẩu và grant đã thu hồi.

## Kiểm chứng source

Mã việc CATALOGTABS01, người thực hiện hieuv526034, nhánh hieuv526034/category01-danh-muc-chung, [PR166](https://github.com/hieuv526034/vietroute-web/pull/166). Phạm vi nghiệm thu gồm native Repo/projection, chống ghi chỗ/tiền lặp, giao diện tab/cây và bộ tài liệu Word.

Các kiểm tra bao gồm format/rules/docs, Node, Java/H2/PostgreSQL, tìm cây và quyền, rollback JPA/JDBC,8 request đồng thời cho đặt/xác nhận/hủy/thu/hoàn. Môi trường kiểm là PostgreSQL loopback15462/vietroute_business_test, không đọc root .env. Maven verify đạt220/220 Java, gồm83 PostgreSQL,0 lỗi/skip. Node77/77 đạt. npm ci/format/format:check/rules/docs và diff-check đạt. Edge headless kiểm34 nút gốc, mở cha tải con, tìm xã kèm tỉnh, xóa tìm, chuyển tab, modal và viewport390px;0 lỗi JavaScript. Exporter sinh182 SQL,100 ví dụ chỉ đọc chạy/rollback trên PostgreSQL preview. Báo cáo và hướng dẫn sử dụng có kết quả kiểm riêng tại mục Tài liệu sử dụng và từ điển dữ liệu. ZIP70 SVG/PNG được kiểm.

## Trạng thái tích hợp và phát hành

Ngày04/11/2026, [PR166](https://github.com/hieuv526034/vietroute-web/pull/166) đã merge vào dev025166b. [PR167](https://github.com/hieuv526034/vietroute-web/pull/167) phát hành dev → main, commit `7d21f30e3767771004037026cebc6f7a41af485d`. Production CI của PR và main đạt, gồm Java/PostgreSQL, Docker/Tomcat, bootstrap/JWT và recovery. [Render Singapore Live](https://dashboard.render.com/web/srv-dbk9elou01pc739ei3ug/deploys/dep-dbla8qon74is739hc8q0) đối chiếu đúng commit. [Mở ứng dụng](https://vietroute-web-sg.onrender.com/journeys?lang=vi). Các URL/API công khai và checksum category.js được đối chiếu sau triển khai.

DB hieuboy/vietroute đã chuyển Category sau khi dừng node, sao lưu mới và kiểm phục hồi khớp24 bảng. Transaction chuyển giữ dữ liệu giao dịch/tài khoản/hash, đổi FK theo mã, kiểm typed FK và quyền rồi nạp JSON hành chính bằng CategoryCustomRepo. Kết quả22 bảng/history10,34 tỉnh/thành/3321 xã/phường. Bộ công cụ được diễn tập rollback/commit trên bản phục hồi trước thao tác thật. Backup mã hóa DPAPI CurrentUser giữ local, không nằm trong Git.

## Giới hạn và vận hành

Deployment Singapore được kích hoạt sau khi resume và sau toàn bộ main CI thành công. Node Virginia hiện tạm dừng để code cũ không dùng schema Category. Hostname vietroute-web.onrender.com vẫn thuộc service Virginia; chưa có xác nhận chuyển hostname sang Singapore. Địa chỉ đang nghiệm thu là vietroute-web-sg.onrender.com.

Ngày 04/11/2026, địa chỉ Virginia trả HTTP 503 và địa chỉ Singapore trả HTTP 200. Render Support đã nhận câu hỏi về khả năng cấp lại hostname cũ. Chủ dự án chốt dùng địa chỉ Singapore để nghiệm thu. [Mở VietRoute tại Singapore](https://vietroute-web-sg.onrender.com/).

Thu/hoàn là ghi nhận thủ công, chưa kết nối ngân hàng hoặc ví. Tiến độ tài xế là xác nhận thao tác. Bản đồ dùng Leaflet/OpenStreetMap cần mạng và không xác minh ranh giới hành chính. PostgreSQL hiện hành chưa có TLS, đã được chủ dự án chấp thuận. Journal có thể mất bản ghi khi pool/DB lỗi, có counter để theo dõi.

Regression đồng thời chứng minh các bất biến trong ca kiểm, chưa là benchmark tải production. Kiểm không thay dữ liệu thật để thử build. Mật khẩu xem cấu hình local được cấp riêng, không ghi trong báo cáo hoặc ảnh. Hostname Render và trạng thái release tra [Vận hành](../04-van-hanh/VAN_HANH.md) cùng PR phát hành thực tế.

## Tài liệu sử dụng và từ điển dữ liệu

DOCS04 do hieuv526034 thực hiện trên nhánh hieuv526034/docs04-tu-dien-va-huong-dan. Báo cáo Word có tên tiếng Việt, mục đích và thành phần quản lý cho22 bảng,338 cột có tên diễn giải, kiểu, NULL, PK/FK và mô tả nghiệp vụ. Cột DB sinh chỉ đọc, cột @Transient không đưa vào schema vật lý. Từ điển đồng bộ tại [Schema và phân quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#từ-điển-bảng-và-cột). Metadata chỉ đọc trên PostgreSQL test loopback15462/vietroute_catalogtabs_maintenance, đối chiếu entity và migration.

[Hướng dẫn A–Z](../01-bat-dau/HUONG_DAN_SU_DUNG.md) có18 mục cho khách, điều hành, tài xế và quản trị, bao gồm tìm/đặt, xác nhận/hủy, thu/hoàn, danh mục cây/bản đồ, nguồn/tuyến/chuyến, báo cáo, quyền, hỗ trợ và bài thực hành. Có [bản Word hướng dẫn](../01-bat-dau/Huong_dan_su_dung_VietRoute.docx) để in hoặc chia sẻ. Hướng dẫn thao tác theo contract/UI source, không ghi mật khẩu và không ghi giao dịch thử lên production.

Báo cáo97 trang, hướng dẫn9 trang đã xuất bằng Word, cập nhật mục lục và số trang, kiểm ảnh toàn bộ106 trang và đọc các trang bảng chi tiết. Đối chiếu đủ22 bảng/338 cột giữa metadata, entity, Markdown và Word, không ô mô tả trống. npm ci/format/format:check/rules/docs và diff-check đạt,221 liên kết trong23 tài liệu được kiểm. Không chạy lại Java/Node vì chỉ sửa tài liệu. Phạm vi DOCS04 gồm tài liệu. Bản ứng dụng nghiệm thu đối chiếu theo deployment Render Singapore ở trên. Ô danh tính bìa giữ để nhóm điền.

## Danh mục và tra cứu vị trí trên bản đồ

UI01 do hieuv526034 thực hiện trên nhánh hieuv526034/ui01-danh-muc-ban-do từ dev. Thanh tìm và các nút quản lý cùng hàng khi đủ chỗ, thao tác CRUD hiện biểu tượng và tooltip. Hộp chọn bản đồ gọi Photon khi người dùng gửi truy vấn, chọn kết quả để đặt ghim và điền tọa độ. Ghim tay vẫn dùng được khi tra cứu lỗi. Hướng dẫn thao tác ở [Hướng dẫn sử dụng](../01-bat-dau/HUONG_DAN_SU_DUNG.md#8-quản-trị-danh-mục-chung).

Edge kiểm template vi/en với dữ liệu fixture, căn nút trên desktop, bố cục 390 px, tra cứu thực tế Bến xe Gia Lâm, nhấn Enter, chọn tọa độ và giữ ghim khi mất mạng. Node 77/77 và Java CategorySecurityTest/EndpointSecurityTest 9/9 đạt. Đối chiếu CI và trạng thái tích hợp tại [PR của UI01](https://github.com/hieuv526034/vietroute-web/pulls?q=is%3Apr+head%3Ahieuv526034%2Fui01-danh-muc-ban-do), phiên bản đang chạy tại [Render Singapore](https://dashboard.render.com/web/srv-dbk9elou01pc739ei3ug/deploys).

UI02 do hieuv526034 thực hiện trên nhánh hieuv526034/ui02-mau-nut-danh-muc từ dev. Các nút trên thanh tìm kiếm dùng chung màu xanh lá VietRoute với biểu tượng trắng. Nút thùng rác dùng nền đỏ và biểu tượng trắng. Edge kiểm desktop, 390 px, hover và độ tương phản các nút trên thanh tìm kiếm trên 4,5:1. Format, rules, docs và diff-check đạt. Đối chiếu tại [PR UI02](https://github.com/hieuv526034/vietroute-web/pulls?q=is%3Apr+head%3Ahieuv526034%2Fui02-mau-nut-danh-muc) và [Render Singapore](https://dashboard.render.com/web/srv-dbk9elou01pc739ei3ug/deploys).

## Tài khoản, tổng quan và lịch hằng ngày

Mã việc DAILY01, nhánh `hieuv526034/auth-dashboard-lich-co-dinh` từ `dev` tại `c278bcb`. Form tạo tài khoản nhận email liên hệ tùy chọn. JWT, quyền endpoint và điều kiện khách/tài xế sử dụng tài khoản dựa vào vòng đời, quyền và phạm vi, bỏ điều kiện xác minh email. Dashboard và trang `/admin` có bố cục mới với thẻ và lối tắt theo quyền.

Trang tìm tự tải các chuyến còn nhận đặt trong 31 ngày. Người dùng có thể lọc theo tuyến, ngày và giờ đón. Chuyến lặp dùng snapshot đã công bố, sinh chuyến và tồn chỗ riêng theo ngày. Bộ init có bốn nguồn Duy Long cho Hà Nội–Hải Phòng hai chiều. Migration `trip/002_daily_service.sql` chuyển dữ liệu mẫu đúng mã ở DB cũ nếu có. Quy trình thao tác và điều kiện nghiệm thu nằm trong [hướng dẫn sử dụng](../01-bat-dau/HUONG_DAN_SU_DUNG.md) và [bộ test case](TEST_CASE_TAI_KHOAN_TONG_QUAN_LICH_CHUYEN.md).

Maven `verify` đạt 221 kiểm thử trên PostgreSQL loopback và fixture Java. Node đạt 78 ca. Chạy lại 18 ca liên quan sau khi bổ sung kiểm nâng cấp từ DB cũ. Migration nhận bốn lịch Duy Long và giữ số chuyến, đơn trước khi sinh lượt mới. Đã kiểm luồng tìm sau 45 ngày, khoảng giờ không khớp, báo giá và tạo đơn. Hai template tổng quan render trong Spring MVC và WAR profile production đóng gói thành công.

Ngày 08/11/2026, chủ dự án duyệt [PR176](https://github.com/hieuv526034/vietroute-web/pull/176), đã merge vào dev tại `52bb542`. [PR177 phát hành dev → main](https://github.com/hieuv526034/vietroute-web/pull/177) đang chờ CI production. PostgreSQL production `hieuboy/vietroute` đã được sao lưu bằng `pg_dump` trong snapshot nhất quán, mã hóa DPAPI CurrentUser và phục hồi sang DB loopback riêng. Số dòng và digest dữ liệu UTC khớp đủ 22 bảng. Bản sao lưu giữ local, không nằm trong Git.

Migration bước 11 đã diễn tập trên bản phục hồi rồi áp dụng production. Kiểm trước/sau xác nhận 20 bảng nghiệp vụ và quyền được bảo toàn, bốn chuyến nguồn Duy Long bật lịch hằng ngày. Dùng đúng truy vấn `TripScheduleRepo.materialize` bổ sung 24 chuyến cho cửa sổ bán còn thiếu. Chạy lại trên bản phục hồi thêm 0 chuyến. Hai ca kiểm tài khoản khách Duy Long trên PostgreSQL riêng tiếp tục đạt.

Trình duyệt trong Codex chạy bản mới trên DB phục hồi riêng đã kiểm tự tải danh sách, chọn Hà Nội → Hải Phòng, lọc ngày 23/12/2026 và giờ 09:00–10:00, trả đúng chuyến 09:30 với 16 chỗ. Modal chi tiết báo còn chỗ và dẫn tới đăng nhập. Đăng nhập khách thành công và Dashboard hiển thị đúng đơn của khách. Đã kiểm Dashboard và `/admin` trên desktop và viewport 390 px, không cuộn ngang toàn trang. Nhánh nghiệm thu sửa tiêu đề `/admin` bị ép hẹp trên desktop. Trạng thái Render Live của bản mới sẽ được ghi sau khi đối chiếu SHA và HTTP.

Lượt Production CI đầu của PR177 đạt 221 Java, build image và kiểm WAR/Tomcat. Drill khởi tạo dừng ở assertion history còn dùng mốc 10 trước migration lịch hằng ngày. Nhánh nghiệm thu cập nhật mốc 11 ở kiểm khởi tạo và phục hồi, giữ nguyên gate và toàn bộ drill; phải chạy lại Production CI trước merge main.
