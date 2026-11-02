# Đặc tả VietRoute theo khối nghiệp vụ

Phạm vi hiện hành ngày 02/11/2026: một ứng dụng web Spring MVC/Thymeleaf cho quản lý nhà xe, tìm chuyến và đặt chỗ theo chặng. Java 17+, JPA/Hibernate CRUD + JDBC batch/query/khóa phức tạp, PostgreSQL, WAR/Tomcat 10.1; không Spring Boot. Trạng thái nghiệm thu thật ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), hợp đồng HTTP ở [API](../02-phat-trien/API_BASE.md).

## Khối và dữ liệu

| Khối | Bảng | Phạm vi |
|---|---|---|
| auth | 8 bảng auth_* | Tài khoản, role/function, grant menu/API, scope và history |
| catalog | catalog_items, location_provinces, location_places | Loại xe/nguồn đặt, tỉnh/thành, địa điểm đón/trả |
| carrier | carriers, carrier_vehicles, carrier_drivers | Nhà xe, phương tiện, hồ sơ tài xế |
| trip | carrier_routes, carrier_route_stops, carrier_trips, trip_stops, trip_segments, trip_events | Tuyến một chiều, snapshot chuyến, chặng/tồn chỗ, tiến độ |
| booking | bookings, booking_events | Đặt chỗ WEB/PHONE, xác nhận/hủy, thu/hoàn, lịch sử |
| report | Không thêm bảng | Chỉ tiêu chuyến/đơn/thu-hoàn theo ngày/nhà xe và dashboard |
| bootstrap/common | api_request_journal và history hạ tầng | Khởi tạo opt-in, nền MVC/DB và debug request |

Tổng 24 bảng gồm history. Không theo dõi GPS/thiết bị hoặc yêu cầu app mobile; không tích hợp gửi thư/upload/payment gateway. Địa điểm có tọa độ tĩnh nhập bởi người quản lý; link địa điểm không phải vị trí xe hay ETA tự tính.

## Tác nhân và quyền

Khách công khai tìm chuyến/nhà xe; người dùng đăng nhập đặt và xem đơn của chính mình. Nhân viên xử lý dữ liệu trong nhà xe được gán. Tài xế chỉ thao tác chuyến được phân công qua quan hệ DB. Quản trị cấp tài khoản và gán quyền. Tên role mặc định chỉ là bộ cấu hình khởi tạo, không quyết định grant trong Java.

Quyền theo User → Role → Function → method + route template, menu và API độc lập. SQL vẫn kiểm scope/owner/parent/driver; role toàn cục không tự cho đọc mọi nhà xe. all_scopes chỉ mở phạm vi của endpoint do chính role đó cấp. ID thay trên URL không vượt scope, thường trả 404. Thiếu quyền endpoint trả 403; token không hợp lệ trả 401. Thu hồi có hiệu lực ở lần kiểm tiếp theo.

## Luồng nghiệp vụ

1. Chuẩn bị danh mục, tỉnh/địa điểm và nhà xe; tạo xe/hồ sơ tài xế, gán account/scope hợp lệ.
2. Tạo tuyến một chiều gồm 2–32 điểm theo thứ tự, phút đến/rời và giá cộng dồn. Chiều về là tuyến riêng.
3. Tạo chuyến từ tuyến, phân công xe/tài xế và thời gian khởi hành; lưu snapshot lịch/giá/thông tin, không sửa lịch sử khi mẫu thay đổi.
4. Công bố chuyến hợp lệ. Khách tìm theo điểm đi/đến đúng chiều, ngày xuất phát, nhà xe/loại xe và số chỗ.
5. Khách gửi yêu cầu WEB hoặc nhân viên tạo PHONE trong phạm vi. Giá/chỗ/owner/audit lấy server, không tin client.
6. Nhân viên xác nhận/hủy đơn, ghi nhận tiền thực thu/hoàn có requestKey và version. PENDING chưa chiếm chỗ; CONFIRMED giữ đủ mọi đoạn trong giao dịch nguyên tử.
7. Tài xế xác nhận khởi hành, đến/rời điểm theo thứ tự và hoàn tất chuyến. Thời gian server ghi, không phát tọa độ nền. Tiến độ không tự biến thành xác nhận thanh toán.
8. Báo cáo/dashboard đọc số liệu theo scope, tách giá đặt khỏi tiền thực thu/hoàn; không sinh số ngẫu nhiên hoặc polling GPS.

## Chỗ, tiền và transaction

Mỗi trip_segment là một đoạn giữa hai điểm liên tiếp. Chỗ bán cho yêu cầu là nhỏ nhất của capacity - reserved_seats trên các đoạn đi qua. Giá là chênh lệch giá cộng dồn tại điểm trả/đón nhân số chỗ. Hai yêu cầu không giao đoạn có thể dùng lại cùng sức chứa; không đánh số ghế cố định trong bản này.

Khóa trip → booking → segments theo thứ tự, kiểm đủ đoạn/capacity/version và CAS trước commit. Xác nhận/hủy/đổi trạng thái, trả chỗ, thu-hoàn và history phải cùng thành công hoặc cùng rollback. Không loop query/save từng đơn khi hủy chuyến hoặc cập nhật nhóm hành khách. Request trùng không ghi thu tiền hai lần; tranh chấp hoặc version cũ trả lỗi rõ ràng, không tự retry mutation.

Giữ lịch sử, ID, mã và quan hệ cha bất biến theo contract; ngừng dùng thay xóa vật lý khi model yêu cầu. Tài khoản/nhà xe inactive không tự giải phóng nghĩa vụ xử lý đơn tồn; chỉ cho thao tác phù hợp trạng thái đã được service kiểm. Không hardcode nhà xe/tuyến/ngày/giá trong luồng API.

## Khởi tạo và nghiệm thu

Admin bootstrap riêng; 12 khách/24 tài xế/6 điều hành tạo một lần active/verified khi bật cấu hình tương ứng. Bộ mới có 494 chuyến/2.952 đơn và mapping theo nhà xe; [chạy local](../01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo) liệt kê account/key mật khẩu và luồng dùng thử. Seed không đặt lại hash hoặc khôi phục grant/membership đã thu hồi. Dữ liệu nền do dự án xây dựng theo yêu cầu, không xác nhận là hoạt động thương mại thật; người vận hành phải thay thông tin đơn vị trước sử dụng thực tế.

Ca bắt buộc: thiếu quyền URL trực tiếp/hai context, ngoài scope/owner, version/unique/FK, dữ liệu đầu vào sai, rollback history, race giữ/trả chỗ, request thu tiền trùng, tiến độ sai thứ tự, lọc đúng chiều/ngày/chặng, pagination/count, lỗi 401/403/404/409 và vi/en. Dùng H2 cho phần portable, PostgreSQL riêng cho SQL/lock/migration/batch; không seed/load test Neon. Chi tiết ở [nghiệm thu](NGHIEM_THU_NEN_TANG.md).

Phân công theo trọn khối tại [PHAN_CONG](PHAN_CONG.md); mỗi người chịu backend/UI/test. Kế hoạch không là bằng chứng đóng góp hoặc đã deploy. Không bao gồm GPS/mobile, thanh toán trực tuyến, refresh token/SSO/MFA hoặc gửi email trong phạm vi hiện hành.
