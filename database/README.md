# Database VietRoute

1. Dùng MySQL và chạy `000-create-database.sql`.
2. Tạo user ứng dụng phù hợp môi trường local; đặt `VIETROUTE_DB_URL`, `VIETROUTE_DB_USER`, `VIETROUTE_DB_PASSWORD` trong cấu hình chạy Tomcat/IDE.
3. Trang chủ của khung source chưa truy vấn database, nên có thể kiểm tra Spring MVC/Thymeleaf trước khi cả nhóm hoàn thiện schema.
4. Thống nhất schema theo đặc tả trong `docs/` trước khi thêm bảng. Các thay đổi SQL đặt thành file có số thứ tự mới, không sửa file người khác đã chạy mà không thống nhất.
5. Dữ liệu demo, tên nhà xe, giờ và giá phải được ghi rõ là minh họa.

`JdbcConnectionFactory.open()` trả một Connection mới. DAO dùng `PreparedStatement`, `ResultSet` và `try-with-resources`. Với nghiệp vụ nhiều DAO, service sở hữu một Connection và truyền cùng Connection cho toàn bộ giao dịch, rồi commit/rollback; các DAO tham gia không tự đóng Connection đó.

Không đưa mật khẩu database hoặc dữ liệu khách thật vào Git. `.env.example` chỉ là mẫu, Java không tự đọc `.env`.
