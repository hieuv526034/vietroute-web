# Phân công VietRoute

Các email dưới đây do nhóm cung cấp. Username GitHub phải được từng người xác nhận khi mời vào repo; email có dấu chấm không phải bằng chứng về username GitHub.

| Thành viên/email | Module | Phạm vi | Nhánh chức năng mẫu |
|---|---|---|---|
| bangpham.it@gmail.com | identity | Đăng nhập/quyền, nhà xe, loại xe, xe, tài xế | feat/identity-login |
| anhhoang104@gmail.com | network | Địa điểm, tuyến hai chiều, điểm dừng, chuyến, phân công | feat/network-trip-management |
| hiencoi250404-design@gmail.com | booking | Tìm chuyến, yêu cầu/xác nhận/hủy, đặt qua điện thoại, chỗ theo chặng | feat/booking-segment-capacity |
| hieuv526034@gmail.com | tracking | Chi tiết chuyến, tiến độ/vị trí, Google Maps, báo cáo đơn giản | feat/tracking-trip-progress |

Mỗi người viết Controller, Service, DAO, Thymeleaf, SQL và ca nghiệm thu của phần mình. Đường dẫn Controller: `vn/vietroute/web/<module>`. Giao diện: `WEB-INF/views/<module>`. Service/DAO/model: `vn/vietroute/<module>/...`.

## Danh sách Issue ban đầu để tạo trên GitHub

| Mã | Issue | Module | Hoàn thành khi |
|---|---|---|---|
| ID-01 | Đăng nhập, đăng xuất và kiểm tra vai trò | identity | Session đúng; chặn URL không có quyền; mật khẩu hash. |
| ID-02 | Quản lý nhà xe, tài xế và phương tiện | identity | CRUD hợp lệ; nhà xe không sửa dữ liệu đơn vị khác. |
| NW-01 | Địa điểm, tuyến một chiều và điểm dừng | network | Thứ tự/giờ/giá hợp lệ; tạo được hai chiều riêng. |
| NW-02 | Tạo chuyến và phân xe/tài xế | network | Có lịch từng điểm qua ngày, snapshot tuyến và chặn trùng lịch. |
| BK-01 | Tìm chuyến tại điểm trung gian | booking | Đúng chiều, ngày lên xe và số chỗ trên các chặng. |
| BK-02 | Đặt, xác nhận và hủy bằng transaction | booking | Không bán vượt chỗ; không trừ/trả lặp; hỗ trợ nguồn điện thoại. |
| TR-01 | Chi tiết chuyến và Google Maps | tracking | Đúng lộ trình, điểm dừng, thông tin liên hệ và lỗi Maps có xử lý. |
| TR-02 | Theo dõi tiến độ và thử nghiệm vị trí web | tracking | Hiển thị thời gian cập nhật; thử Geolocation khi trang hoạt động; báo dữ liệu cũ. |
| INT-01 | Hủy chuyến và đóng đơn liên quan | network + booking | Giao dịch nhất quán; có lịch sử và không nhận đơn sau hủy. |
| INT-02 | Nghiệm thu nhiều nhà xe và luồng hoàn chỉnh | cả nhóm | Chạy bộ ca nghiệm thu trong đặc tả và ghi kết quả thực tế. |

Đây là danh sách để tạo Issue, chưa phải Issue đã tạo trên GitHub. Assign theo username thật sau khi thành viên nhận lời mời. Mỗi Issue có người phụ trách chính; phối hợp/review được ghi trong PR.

## Nguyên tắc công bằng

- Khung source chung là phần chuẩn bị; không tạo commit rỗng hoặc đổi Author để chia đều số commit.
- Mỗi thành viên tự phát triển phần được giao, commit và mở PR của mình.
- Mỗi người review ít nhất các phần giao tiếp trực tiếp với module của mình.
- Người phụ trách booking không kiêm tổng hợp slide; người phụ trách tracking tổng hợp slide từ nội dung cả nhóm, không viết thay báo cáo nghiệp vụ cho cả 4.
- Cuối ngày 2 thống nhất schema và hợp đồng service. Cuối ngày 7 chạy được một luồng từ nhà xe đến đặt chỗ và xem tiến độ.
