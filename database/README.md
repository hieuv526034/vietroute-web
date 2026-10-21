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

## Schema bắt buộc và nâng cấp

Database production vẫn là `neondb`, **schema ứng dụng là `vietroute`**. Mọi connection đặt schema `vietroute`; SQL repository chỉ rõ `vietroute.<table>`. Bật initializer cùng các biến DB/JWT/bootstrap trong cấu hình chạy để app tự chuẩn bị cấu trúc trước khi phục vụ đăng nhập.

- DB mới: tạo schema và `vietroute.vietroute_schema_history`, chạy V001 trong search_path `vietroute`, chạy V002 tạo index, kiểm chứng entity rồi seed.
- DB cũ đã có V001 ở `public`: chuyển history sang `vietroute` giữ checksum; bỏ qua V001; V002 dùng `ALTER TABLE ... SET SCHEMA` để chuyển bốn bảng ứng dụng, giữ ID, foreign key, sequence/index và dữ liệu. Không reset admin/hash hiện có.
- Nếu có hai history, bảng cùng tên ở cả hai schema, checksum sai hoặc bảng legacy thiếu history, startup dừng và rollback. Cần đối chiếu dữ liệu trước khi xử lý; runner không tự xóa hay gộp bảng.
- V001 giữ nguyên nội dung đã áp dụng. Thay đổi tiếp theo phải có migration version mới; không chạy DDL từ Maven build.

`UserAccount` và `CatalogItem` có `@JdbcTable`/`@JdbcIndex` để đọc rõ schema, table, PK và index. Đây là metadata JDBC, không phải ORM. `JdbcSchemaValidator` đối chiếu tên/thứ tự cột/tính unique của index thực tế trong transaction startup, trước seed. Mỗi module mới cần migration, metadata entity và thêm class vào danh sách kiểm chứng.

Index hiện tại:

| Bảng | PK/UNIQUE | Index truy vấn/FK |
|---|---|---|
| `vietroute.auth_users` | PK `id`; UNIQUE `username` | `created_by`, `updated_by` |
| `vietroute.auth_roles` | PK `code` | PK đủ cho tra cứu mã role |
| `vietroute.auth_user_roles` | PK `(user_id, role_code)` | `role_code`; prefix PK phục vụ tra theo user |
| `vietroute.catalog_items` | PK `id`; UNIQUE `(type, code)` | `(type, active, sort_order, id)`, `created_by`, `updated_by` |
| `vietroute.vietroute_schema_history` | PK `version` | PK đủ cho kiểm checksum theo version |

CI kiểm tra DB mới, khởi động đồng thời/restart và DB legacy tạo từ đúng V001 trên PostgreSQL tạm; không truy cập Neon production. Tham khảo [PostgreSQL ALTER TABLE SET SCHEMA](https://www.postgresql.org/docs/17/sql-altertable.html) về việc giữ index/constraint/sequence sở hữu bởi bảng khi chuyển schema.
