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
