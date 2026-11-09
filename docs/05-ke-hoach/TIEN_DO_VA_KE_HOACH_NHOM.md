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

Ngày 08/11/2026, chủ dự án duyệt [PR176](https://github.com/hieuv526034/vietroute-web/pull/176), đã merge vào dev tại `52bb542`. [PR178](https://github.com/hieuv526034/vietroute-web/pull/178) bổ sung nghiệm thu tổng quan và kiểm migration, merge dev tại `40c6051`. [PR177 phát hành dev → main](https://github.com/hieuv526034/vietroute-web/pull/177) đã merge ngày 09/11/2026 tại `93883c091de76227997ff8af8d7d0c06ca5faa49`. PostgreSQL production `hieuboy/vietroute` đã được sao lưu bằng `pg_dump` trong snapshot nhất quán, mã hóa DPAPI CurrentUser và phục hồi sang DB loopback riêng. Số dòng và digest dữ liệu UTC khớp đủ 22 bảng. Bản sao lưu giữ local, không nằm trong Git.

Migration bước 11 đã diễn tập trên bản phục hồi rồi áp dụng production. Kiểm trước/sau xác nhận 20 bảng nghiệp vụ và quyền được bảo toàn, bốn chuyến nguồn Duy Long bật lịch hằng ngày. Dùng đúng truy vấn `TripScheduleRepo.materialize` bổ sung 24 chuyến cho cửa sổ bán còn thiếu. Chạy lại trên bản phục hồi thêm 0 chuyến. Hai ca kiểm tài khoản khách Duy Long trên PostgreSQL riêng tiếp tục đạt.

Trình duyệt trong Codex chạy bản mới trên DB phục hồi riêng đã kiểm tự tải danh sách, chọn Hà Nội → Hải Phòng, lọc ngày 23/12/2026 và giờ 09:00–10:00, trả đúng chuyến 09:30 với 16 chỗ. Modal chi tiết báo còn chỗ và dẫn tới đăng nhập. Đăng nhập khách thành công và Dashboard hiển thị đúng đơn của khách. Đã kiểm Dashboard và `/admin` trên desktop và viewport 390 px, không cuộn ngang toàn trang. PR178 sửa tiêu đề `/admin` bị ép hẹp trên desktop.

Lượt Production CI đầu của PR177 đạt 221 Java, build image và kiểm WAR/Tomcat. Drill khởi tạo dừng ở assertion history còn dùng mốc 10 trước migration lịch hằng ngày. PR178 cập nhật mốc 11 ở kiểm khởi tạo và phục hồi, giữ nguyên gate và toàn bộ drill. [Production CI của PR177](https://github.com/hieuv526034/vietroute-web/actions/runs/42496567356) và [main](https://github.com/hieuv526034/vietroute-web/actions/runs/42503753586) sau đó đều đạt, gồm PostgreSQL, image, WAR/Tomcat, JWT, restart, pooler và recovery.

Ngày 09/11/2026, [Render Singapore Live](https://dashboard.render.com/web/srv-dbk9elou01pc739ei3ug/deploys/dep-dboautvavr4c73b853o0) đúng SHA `93883c0`. Health trả 200; checksum `journeys.js`, `dashboard.js` và `app.css` khớp source phát hành. Trên ứng dụng thật, danh sách tự tải không cần chọn tuyến/ngày. Hà Nội → Hải Phòng ngày 10/11, khung 09:00–10:00 trả `DLG1R26D20261110` lúc 09:30, còn 13 chỗ. Đổi sang 10:00–11:00 trả rỗng đúng bộ lọc. Chọn 24/12 sau 45 ngày trả `D1559T20261224` lúc 09:30, còn 16 chỗ và chi tiết báo còn chỗ. Đăng nhập khách Duy Long thành công, Dashboard đọc đúng hai đơn của khách. Không tạo/xác nhận/hủy đơn hoặc ghi tiền thử trên production.

Rà API có xác thực trên production theo từng vai trò đều HTTP 200. Tại lúc đối chiếu: admin đọc 7 nhà xe, 25 xe, 25 tài xế, 28 tuyến, 674 chuyến, 3.878 đơn, 46 tài khoản sử dụng, 4 role, 4 function và 5 nhóm danh mục. Số chuyến tiếp tục tăng theo cửa sổ lịch/ngày được tìm. Điều hành Duy Long đọc đúng 1 nhà xe/1 xe/1 tài xế/4 tuyến/180 chuyến/926 đơn và có số liệu báo cáo. Tài xế Duy Long có 180 chuyến được giao; khách có 2 đơn. Lookup có 34 tỉnh/thành và 3 loại xe; chọn đúng xã/phường Hà Nội đọc được địa điểm con. Lookup địa điểm thiếu cha trả rỗng đúng contract, không phải thiếu seed. Báo cáo kỳ tháng 11 có dữ liệu theo scope. Số liệu cá nhân của admin có thể bằng 0 nếu không có đơn hoặc assignment riêng, không thêm giao dịch giả để đổi chỉ số.

API công khai tiếp tục đối chiếu chiều Hải Phòng → Hà Nội ngày 10/11 lúc 12:00, còn 13 chỗ. Quote của cả ba trường hợp ngày mai/hai chiều/ngày xa đều `accepting=true`; tìm lại giữ ID. Ngày đã qua và khung giờ không khớp trả 0. Tài khoản khách Duy Long có WEB PENDING cũ nên quy tắc một đơn WEB active vẫn áp dụng, cần xử lý/hủy theo luồng hợp lệ trước khi gửi đơn khác. Không sửa hoặc hủy giao dịch production trong lượt nghiệm thu.

Khách đăng nhập trên trình duyệt mở chi tiết lượt Hải Phòng → Hà Nội 05:00 ngày 09/11 thấy còn chỗ và link Gửi yêu cầu đặt chỗ. Nhật ký API đọc được 1.331 bản ghi, sau một GET công khai tăng lên 1.332; counter missed-write giữ 31 từ thời điểm kiểm trước, không tăng trong phép đối chiếu này. Đây là kiểm có dữ liệu và ghi mới, không khẳng định nhật ký đầy đủ mọi request từ startup.

## PowerPoint bảo vệ sản phẩm

DOCS05 do hieuv526034 thực hiện trên nhánh `hieuv526034/docs05-thuyet-trinh` từ dev `40c6051`. [PowerPoint](bao-cao/Thuyet_trinh_VietRoute.pptx) gồm 28 slide 16:9, chữ và 13 bảng chỉnh sửa được, 6 ảnh ứng dụng thật, Notes lời nói và nguồn đối chiếu. Thông tin trường/lớp/sinh viên/mã số/giảng viên để trống. Nội dung gồm tác nhân, toàn bộ nhóm chức năng, lịch hằng ngày, trạng thái đơn, tồn chỗ, thu/hoàn, kiến trúc Spring MVC, 22 bảng/340 cột, init, transaction, kiểm thử, triển khai, demo, giới hạn và hướng phát triển. Ảnh từ DB phục hồi riêng, phân biệt dữ liệu giả lập với hoạt động thương mại.

Đã render và đọc đủ 28 slide, sửa độ rộng bảng tồn chỗ và kiểm ảnh nhúng. Package, font Arial, hình học, import lại bằng Artifact Tool đạt, không finding hoặc warning; chưa kiểm bằng Microsoft PowerPoint. Các kiểm nghiệm thu/API của DAILY01 ở trên là bằng chứng ứng dụng, không suy từ việc xuất slide. Bản Word 338 cột thuộc lần nghiệm thu trước; Markdown và PowerPoint mới dùng cấu trúc 340 cột. Phạm vi DOCS05 là tài liệu, không cần phát hành image mới. Trạng thái tích hợp tra [PR DOCS05](https://github.com/hieuv526034/vietroute-web/pulls?q=is%3Apr+head%3Ahieuv526034%2Fdocs05-thuyet-trinh) và PR phát hành tài liệu tương ứng.

## Liên hệ tài xế trong đơn của khách

BOOKINGCONTACT01 do hieuv526034 thực hiện trên nhánh `hieuv526034/bookingcontact01-lien-he-tai-xe` từ dev `37fa81b`. Chi tiết Đơn của tôi có tên, điện thoại tài xế và biển số cùng link Gọi tài xế. SQL chỉ trả ba field khi đúng chủ đơn, booking CONFIRMED và chuyến OPEN/IN_PROGRESS; các field không đủ điều kiện bị bỏ khỏi JSON. Danh sách đơn, chi tiết staff và tìm chuyến công khai không trả liên hệ mới. Đọc snapshot phân công hiện hành của chuyến, đổi phân công qua API chuyến được phản ánh ở lần mở lại chi tiết. Luồng tài xế đọc liên hệ khách CONFIRMED/COMPLETED trên chuyến được giao giữ nguyên. Không thêm migration, endpoint hoặc grant.

Ngày 09/11/2026, Maven verify đạt 221 ca, gồm 84 PostgreSQL trên loopback15462/vietroute_business_test, không lỗi/skip. Regression booking kiểm owner/khách khác, PENDING/CONFIRMED/REJECTED/CANCELLED/COMPLETED, trạng thái chuyến, chi tiết/danh sách/public/staff và cập nhật phân công. Node đạt 80 ca, gồm 2 ca mới chạy client thật về link `tel:`, xóa liên hệ cũ và số điện thoại không hợp lệ. SQL exporter ghép đủ 188 query. Format, format:check, rules, docs và diff-check đạt. Lượt đầu test DB dùng sai cổng local bị từ chối kết nối, đã chạy lại với đúng 15462 và toàn bộ ca đạt; không kết nối production hoặc đọc root .env để kiểm.

Trình duyệt Codex kiểm vi/en, desktop và 390 px trên DB bản sao loopback riêng với liên hệ/biển số minh họa. Đơn xác nhận có link gọi đúng `tel:`, đơn hoàn tất không giữ liên hệ từ modal trước, trang 390 px không cuộn ngang. Không thực hiện cuộc gọi. Đã cập nhật hướng dẫn, contract dữ liệu và LH-01 đến LH-04 trong bộ test case. [PR181](https://github.com/hieuv526034/vietroute-web/pull/181) đã merge vào dev tại `15bf277` ngày 09/11/2026 sau cả ba CI đạt. [PR182 dev → main](https://github.com/hieuv526034/vietroute-web/pull/182) đã merge tại `c0d17ee552898cd05ca94dee201e1d2700101af5` sau khi Production Docker của PR đạt. [Production Docker của main](https://github.com/hieuv526034/vietroute-web/actions/runs/42550701437) cũng đạt.

Ngày 09/11/2026, Render trả health 200 và `bookings.js` khớp source phát hành. API theo bốn vai trò trả 200, quản trị thấy 7 nhà xe, 25 xe, 25 tài xế, 28 tuyến, 682 chuyến và 3.878 đơn tại thời điểm kiểm. Khách Duy Long đọc được hai đơn. Tìm công khai Hà Nội → Hải Phòng ngày 10/11 lúc 09:30 có 13 chỗ, ngày 24/12 cùng giờ có 16 chỗ và báo giá nhận đặt. Chiều ngược lại ngày 10/11 lúc 12:00 có 13 chỗ. Sai khung giờ và ngày đã qua trả rỗng. Lần kiểm Swagger quản trị đầu vượt timeout 60 giây. Chưa đối chiếu SHA trên bảng Render vì trình duyệt không xác minh được URL. Ca có liên hệ thật trên đơn đủ điều kiện được kiểm trên DB phục hồi riêng bằng dữ liệu minh họa.

DOCS06 do hieuv526034 thực hiện trên nhánh `hieuv526034/docs06-lien-he-va-phat-hanh` từ dev `15bf277`. PowerPoint giữ 28 slide, bổ sung liên hệ hai phía ở slide 8/12 và Notes demo, đồng bộ 80 test JavaScript và 20 ca thủ công ở slide 23. Đã render toàn bộ, so sánh các slide sửa với bản gốc và rà bố cục toàn bộ; package/font/hình học/import lại đạt, không warning. Chưa kiểm trong Microsoft PowerPoint. Kịch bản demo thêm thao tác mở lại chi tiết đơn và danh sách khách của tài xế, không thực hiện cuộc gọi. Bản Word là tài liệu nghiệm thu DOCS04, chưa đồng bộ đợt lịch hằng ngày/liên hệ; hướng dẫn Markdown và PowerPoint là tài liệu hiện hành.

[PR183](https://github.com/hieuv526034/vietroute-web/pull/183) đã đưa DOCS06 vào dev tại `450f244`. [PR184](https://github.com/hieuv526034/vietroute-web/pull/184) đã đưa tài liệu vào main tại `9fa9cf5`. Lần kiểm lại Render đọc Swagger thành công và có đủ ba trường mới. Chi tiết PENDING/COMPLETED của khách, danh sách đơn và chi tiết staff không trả liên hệ mới. Đọc đơn của người khác trả 404, chưa đăng nhập đọc đơn riêng trả 401. Tài khoản kiểm trên Render chưa có đơn đủ điều kiện hiện liên hệ, nên trường hợp hiện liên hệ vẫn đối chiếu trên DB riêng.

## Lịch bảy ngày cho bộ dữ liệu nhiều nhà xe

DAILY02 do hieuv526034 thực hiện trên nhánh `hieuv526034/daily02-lich-bay-ngay` từ dev `450f244`. Khởi tạo mới bật lịch cố định đủ bảy ngày cho 26 mẫu dưới 24 giờ, gồm bốn giờ Duy Long và 22 lịch của sáu nhà xe còn lại. Mỗi ngày có lượt, chỗ và đơn riêng. Hai mẫu TP.HCM–Hà Nội và Hà Nội–TP.HCM dài 34 giờ cần ca xe/tài xế luân phiên để có lượt mỗi ngày. Hướng dẫn local, vận hành và LC-10 ghi điều kiện này.

Ba ca PostgreSQL trên loopback15462 đạt. Ca mới tìm bảy ngày liên tiếp ngoài cửa sổ seed và kiểm đủ 26 lượt của bảy nhà xe, giữ ID khi tìm lại. Hai ca còn lại kiểm khởi tạo và tương thích dữ liệu trước bước 11. Format, format:check, rules, docs và diff-check đã chạy. Trước thay lịch production, đã sao lưu snapshot nhất quán, mã hóa DPAPI và phục hồi sang DB riêng. Số dòng và fingerprint khớp đủ 22 bảng. Bản phục hồi dùng grant hiện hữu chưa có endpoint bật lịch. Diễn tập bật đúng 22 lịch mẫu bằng transaction bảo trì có khóa lịch và sự kiện audit system. Chạy lại thêm 0 lịch. Từ 08/01 đến 14/01/2027, mỗi ngày API công khai tìm thấy 26 lượt của bảy nhà xe và báo giá một lượt của từng nhà xe nhận đặt ở ngày kiểm đầu tiên.

Đã áp dụng đúng 22 lịch mẫu trên PostgreSQL production ngày 09/11/2026 sau khi đối chiếu ID, tuyến, xe, tài xế và giờ với bản phục hồi. Production hiện có 26 nguồn ở đủ bảy nhà xe. API công khai Render xác nhận từng ngày 08/01–14/01/2027 có đủ 26 lượt dự kiến và báo giá ở ngày 08/01 nhận đặt cho cả bảy nhà xe. Transaction chỉ bật lịch nguồn và ghi sự kiện audit. Render sinh chuyến mới qua cơ chế đang chạy. Grant bật lịch trên DB hiện hành vẫn cần cấu hình theo phạm vi nếu điều hành cần bật thêm từ giao diện.

Trợ giúp Tìm chuyến tiếng Việt/Anh giải thích lọc tuyến, ngày và giờ là tùy chọn. PowerPoint bổ sung 26 nguồn, bảy nhà xe và 21 kịch bản thủ công. Đã soát slide sửa, package/font/hình học/import đạt, giữ 28 slide và 13 bảng chỉnh sửa được. Hai bản Word vẫn thuộc mốc DOCS04.
