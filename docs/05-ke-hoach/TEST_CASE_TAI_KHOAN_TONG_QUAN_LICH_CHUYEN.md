# Test case tài khoản, tổng quan và lịch chuyến hằng ngày

Chạy trên PostgreSQL test riêng đã áp dụng migration `trip/002_daily_service.sql`. Bật bộ dữ liệu khởi tạo đầy đủ theo [chạy local](../01-bat-dau/CHAY_LOCAL.md). Dùng tài khoản quản trị, điều hành có quyền lịch chuyến, tài xế và khách. Ghi commit, trình duyệt, thời điểm Việt Nam, mã chuyến và kết quả thực tế vào biên bản nghiệm thu.

| Mã | Điều kiện và thao tác | Kết quả mong đợi |
|---|---|---|
| TK-01 | Tạo tài khoản với email liên hệ, đăng nhập bằng username và mật khẩu tạm, đổi mật khẩu | Sau khi đổi mật khẩu, mở được chức năng đã cấp. Email vẫn hiển thị trong hồ sơ và không có bước xác minh |
| TK-02 | Dùng tài khoản active có `email_verified_at` rỗng, mật khẩu đã đổi và quyền hợp lệ | Đăng nhập và truy cập API/menu được cấp. Truy cập endpoint thiếu quyền vẫn nhận 403 |
| TK-03 | Mở form tạo tài khoản và thử email sai định dạng, email hợp lệ và bỏ trống | Báo lỗi đúng trường nếu sai. Email hợp lệ hoặc bỏ trống đều tạo được tài khoản và không chuyển đến màn xác minh |
| TK-04 | Cấp tài khoản cho tài xế, đổi mật khẩu tạm, bật lại hồ sơ và phân công | Tài xế dùng được tài khoản và thấy đúng chuyến đã giao, không phải xác minh email |
| TQ-01 | Mở `/dashboard` với quản trị, điều hành, tài xế và khách, lần lượt ở tiếng Việt và tiếng Anh | Thẻ, lối tắt và số liệu tương ứng với quyền. Tải lại và xử lý lỗi API hiển thị rõ |
| TQ-02 | Mở `/admin` với tài khoản có quyền và nhập URL bằng tài khoản thiếu grant | Nhóm tác vụ hiển thị rõ theo quyền. Phiên thiếu grant bị từ chối |
| TQ-03 | Mở cả hai trang ở 390 px và desktop, dùng Tab qua các nút | Các thẻ tự xuống hàng, focus rõ, không cuộn ngang toàn trang |
| LC-01 | Mở `/journeys`, để trống ngày và tuyến, chuyển trang | Danh sách tự hiện các chặng còn nhận đặt trong 31 ngày. Các trang giữ cùng bộ lọc |
| LC-02 | Chọn Hà Nội → Hải Phòng vào ngày mai, giờ 09:00–10:00, nhà xe Duy Long | Có lượt 09:30 nếu chưa tới giờ, chưa hủy và còn đủ chỗ. Mở chi tiết thấy còn chỗ, có thể gửi đơn |
| LC-03 | Đổi giờ sang 10:00–11:00 hoặc đảo chiều tuyến | Không thấy lượt 09:30 ở bộ lọc sai giờ hoặc sai chiều. Hải Phòng → Hà Nội có lượt 05:00 và 12:00 khi lọc giờ phù hợp |
| LC-04 | Chọn cùng tuyến vào ngày sau 45 ngày, lọc đúng 15:30, gửi lại tìm kiếm hoặc tìm đồng thời từ hai phiên | Cùng một ID chuyến cho khung giờ đó. Ngày khác có ID, chỗ và đơn riêng |
| LC-05 | Mở chi tiết một kết quả, hủy chuyến từ tài khoản điều hành rồi bấm tải lại chi tiết | Báo chặng không nhận đặt, ẩn nút đặt. Gửi trực tiếp yêu cầu đặt bị từ chối |
| LC-06 | Tạo chuyến nguồn, mở bán, bật lịch hằng ngày. Thử bật thêm lịch chồng cùng xe hoặc tài xế | Lịch hợp lệ sinh chuyến, lịch chồng báo xung đột. URL bật lịch yêu cầu grant và scope tương ứng |
| LC-07 | Hủy một chuyến con rồi tìm lại ngày đó. Tắt lịch nguồn rồi tìm một ngày chưa sinh | Chuyến đã hủy không xuất hiện lại. Tắt lịch không tạo ngày mới và các chuyến đã công bố vẫn có lịch sử riêng |
| LC-08 | Xác nhận một đơn trên chuyến ngày A, rồi kiểm cùng giờ ngày B | Tồn chỗ giảm đúng các đoạn của chuyến A. Chuyến B không bị trừ chỗ |
| LC-09 | Khởi tạo lại bộ dữ liệu mẫu, kiểm bốn lịch Duy Long và tìm chuyến theo giờ | Chỉ có một nguồn cho mỗi khung giờ 05:00, 09:30, 12:00, 15:30. Không tạo lặp chuyến, đơn hoặc cấp lại quyền đã thu hồi |

## Liên hệ tài xế từ đơn của khách

| Mã | Thao tác | Kết quả mong đợi |
|---|---|---|
| LH-01 | Chủ đơn mở chi tiết WEB PENDING rồi điều hành xác nhận trên chuyến OPEN | PENDING không có liên hệ tài xế. Sau xác nhận, chi tiết có tên, SĐT và biển số, bấm Gọi tài xế dùng `tel:` |
| LH-02 | Khách khác truy cập ID đơn đã xác nhận; kiểm API danh sách và tìm chuyến công khai | Ngoài chủ đơn trả 404; danh sách/tìm công khai không trả liên hệ tài xế |
| LH-03 | Điều hành cập nhật phân công của chuyến rồi chủ đơn đóng/mở lại chi tiết | Liên hệ lấy từ snapshot phân công hiện hành, không giữ dữ liệu cũ trên modal |
| LH-04 | Hủy/từ chối/hoàn tất đơn hoặc kết thúc/hủy chuyến rồi đọc lại | Không trả liên hệ tài xế. SĐT nhà xe vẫn dùng để hỗ trợ |

Các ca LH thuộc BOOKINGCONTACT01, trạng thái kiểm/tích hợp theo [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md). Bộ DAILY01 trước đó có 16 ca, không cộng các ca mới vào kết quả nghiệm thu cũ.

## Kết quả đã đối chiếu ngày 09/11/2026

Source phát hành `93883c0`, Render Singapore deployment `dep-dboautvavr4c73b853o0`. Các ca thay đổi dữ liệu dùng PostgreSQL riêng hoặc bản phục hồi, không tạo đơn/thu/hoàn thử trên production. Bộ 16 ca phía trên là kịch bản nghiệm thu, không mặc nhiên là 16 ca thủ công đã đạt.

| Phần đã kiểm | Kết quả thực tế | Phạm vi |
|---|---|---|
| TK-02 và grant độc lập | `assignedAdminCanUseGrantedFunctionsWithoutVerifiedEmail` cùng nhóm security đạt | JUnit/MVC, cả hai Spring context |
| TQ-01, TQ-02, phần bố cục TQ-03 | Khách đăng nhập thấy 2 đơn, quản trị có các nhóm tác vụ; hai màn ở desktop/390 px không cuộn ngang | Trình duyệt vi trên DB phục hồi, khách đăng nhập lại trên production |
| LC-01 | Trang tự tải, API trả 574 chặng còn nhận đặt tại thời điểm kiểm | Production, không chọn tuyến/ngày |
| LC-02 | Hà Nội → Hải Phòng ngày 10/11, 09:00–10:00 có `DLG1R26D20261110` lúc 09:30, còn 13 chỗ; quote `accepting=true` | Trình duyệt và API production |
| LC-03 | Khung 10:00–11:00 trả 0; chiều Hải Phòng → Hà Nội ngày 10/11 có lượt 12:00, còn 13 chỗ và quote khả dụng | Trình duyệt/API production |
| LC-04, phần tìm và chống tạo lặp khi gửi lại | Ngày 24/12 sau 45 ngày có `D1559T20261224` lúc 09:30, còn 16 chỗ; gửi lại tìm giữ ID, quote khả dụng | Trình duyệt/API production; ca tạo đơn trên DB test |
| Nâng cấp DB cũ và init | Regression nâng cấp giữ số chuyến/đơn, nhận đủ 4 nguồn. Bổ sung 24 lượt ở production sau backup; trên phục hồi chạy lại thêm 0 | PostgreSQL riêng, sau đó thao tác nâng cấp đã được chủ dự án cho phép |
| Dữ liệu các nhóm màn | Admin, điều hành, tài xế, khách đều đọc được dữ liệu đúng vai trò; các API đã rà trả HTTP 200 | Số lượng và kỳ báo cáo ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md#tài-khoản-tổng-quan-và-lịch-hằng-ngày) |
| LH-01/LH-04, phần hiển thị và dọn modal | Đơn CONFIRMED có tên, điện thoại, biển số minh họa và link `tel:` đúng. Mở tiếp đơn COMPLETED không còn liên hệ từ đơn trước | Trình duyệt Codex vi/en, desktop và 390 px, DB phục hồi riêng; không gọi điện |
| LH-01 đến LH-04, regression API | Kiểm owner/khách khác, trạng thái đơn/chuyến, đổi phân công, không lộ liên hệ mới qua danh sách/public/staff | PostgreSQL test riêng trong bộ 221 ca Java; hai ca client thuộc bộ 80 test Node. Không thay thế nghiệm thu thủ công toàn bộ |

Các ca còn lại cần người nghiệm thu ghi thao tác, mã dữ liệu và kết quả riêng, đặc biệt bàn phím, đầy đủ vai trò/tiếng Anh, xung đột lịch, tắt lịch và nhánh hủy chuyến con. Regression Java hiện có kiểm quyền, rollback, trạng thái, tồn chỗ và đồng thời không thay thế việc ký nghiệm thu thủ công từng ca. Tài khoản `customduylong` đang có một WEB PENDING cũ; muốn tạo yêu cầu mới phải xử lý/hủy đơn cũ theo luồng của ứng dụng. Đây là quy tắc một đơn WEB active, không phải kết quả tìm chuyến bị sai giờ. Không sửa SQL hoặc tự hủy đơn production để vượt quy tắc.
