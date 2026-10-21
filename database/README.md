# Database VietRoute

1. Dùng **PostgreSQL**. Production dùng Neon với database `neondb` đã có sẵn; không chạy `000-create-database.sql` trên Neon. File này chỉ tạo database `vietroute` cho PostgreSQL local, chạy một lần bằng tài khoản quản trị.
2. Đặt `VIETROUTE_DB_URL`, `VIETROUTE_DB_USER`, `VIETROUTE_DB_PASSWORD` trong cấu hình chạy Tomcat/IDE hoặc Render. Thông tin Neon của dự án đã lưu trong `.env` local bị Git bỏ qua; không dùng giá trị mẫu local khi deploy production.
3. Auth và catalog đã có migration `src/main/resources/db/migration/V001__auth_and_catalog.sql`. Bật `VIETROUTE_DB_INIT_ENABLED=true` khi khởi động để ứng dụng chạy DDL + seed trong transaction có khóa. Cần cấu hình password bootstrap mạnh và JWT secret; xem `docs/API_BASE.md`.
4. Thay đổi schema mới đặt trong resource migration có version mới và thêm vào danh sách runner `DatabaseInitializer`. File đã áp dụng được kiểm checksum, không sửa lại. Các bảng nghiệp vụ trong đặc tả chưa được tạo; thống nhất thiết kế trước khi thêm migration.
5. Dữ liệu demo, tên nhà xe, giờ và giá phải được ghi rõ là minh họa.

`JdbcConnectionFactory.open()` trả một Connection mới. DAO dùng `PreparedStatement`, `ResultSet` và `try-with-resources`. Với nghiệp vụ nhiều DAO, service sở hữu một Connection và truyền cùng Connection cho toàn bộ giao dịch, rồi commit/rollback; các DAO tham gia không tự đóng Connection đó.

Không đưa mật khẩu database hoặc dữ liệu khách thật vào Git. `.env.example` chỉ là mẫu, Java không tự đọc `.env`.

URL PostgreSQL JDBC có dạng `jdbc:postgresql://<host>/neondb?sslmode=require&channelBinding=require&connectTimeout=15`; user/password truyền riêng. Không dán nguyên lệnh `psql` vào `VIETROUTE_DB_URL`. Với pgJDBC, tham số đúng là `channelBinding`, khác `channel_binding` của psql.

Các file SQL mới dùng cú pháp PostgreSQL (`GENERATED ... AS IDENTITY`, `BOOLEAN`, `TIMESTAMPTZ` khi phù hợp), không dùng `AUTO_INCREMENT`, backtick hoặc `ENGINE=InnoDB`. CI build container không cần truy cập Neon và không tự chạy migration/seed vào production.

Initializer tạo `auth_roles`, `auth_users`, `auth_user_roles`, `catalog_items` và `vietroute_schema_history`; seed 4 role, admin và 5 mục loại xe/nguồn đặt chỗ. Chạy lặp không tạo trùng hoặc reset mật khẩu. Phần này là runtime được bật có chủ đích, không chạy trong Maven package. CI xác minh bằng PostgreSQL container tạm riêng.
