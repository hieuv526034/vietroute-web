# Nền tảng tối thiểu và hướng mở rộng

## Phạm vi hiện hành — COMMON01

Giữ nền MVC/JPA/JDBC, bảo mật và nhật ký request. Chức năng mới chỉ bổ sung khi có yêu cầu và trách nhiệm rõ ràng; không giữ adapter/cờ cấu hình cho phần không còn caller.

Giữ Spring MVC, JPA/JDBC, BCrypt/JWT/CSRF, khóa tài khoản đăng nhập sai, giới hạn body/BCrypt đồng thời và [nhật ký API](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md). GPS01 đã bỏ toàn bộ theo dõi vị trí thiết bị và business/tracking. Giữ tìm chuyến theo tuyến/ngày và tiến độ đến/rời điểm do tài xế xác nhận; các luồng này không cần GPS hoặc app mobile. Cleanup journal/auth history theo hoạt động/startup, có giới hạn lô và advisory lock; không RAM queue.

## Phân quyền và transaction

User → Role → Function → menu/endpoint được quản lý trong tám bảng auth. Endpoint chưa gán mặc định bị từ chối. Giữ scope/owner/version/lock order và history cùng transaction, không sửa guard vì lỗi DB hoặc để giảm query. JPA và JDBC mutation dùng cùng JpaTransactionManager; journal có pool riêng để lỗi log không làm lỗi nghiệp vụ.

Đọc [schema/quyền](SCHEMA_VA_PHAN_QUYEN.md), [cấu trúc common](CONSTANTS_VA_CAU_TRUC_WEB.md) và [API base](API_BASE.md). Khi cần mail/upload/worker thực tế, thiết kế lại theo yêu cầu đã chốt, không bật lại bằng env cũ. Không thêm hệ thống phân tán hoặc lớp wrapper khi chưa có chức năng cần nó.
