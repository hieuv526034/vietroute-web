# Database VietRoute

1. Dùng **PostgreSQL**. Production dùng Neon với database `neondb` đã có sẵn; không chạy `000-create-database.sql` trên Neon. File này chỉ tạo database `vietroute` cho PostgreSQL local, chạy một lần bằng tài khoản quản trị.
2. Đặt `VIETROUTE_DB_URL`, `VIETROUTE_DB_USER`, `VIETROUTE_DB_PASSWORD` trong cấu hình chạy Tomcat/IDE hoặc Render. Thông tin Neon của dự án đã lưu trong `.env` local bị Git bỏ qua; không dùng giá trị mẫu local khi deploy production.
3. Auth và catalog đã có migration `src/main/resources/db/migration/V001__auth_and_catalog.sql`. Bật `VIETROUTE_DB_INIT_ENABLED=true` khi khởi động để ứng dụng chạy DDL + seed trong transaction có khóa. Cần cấu hình password bootstrap mạnh và JWT secret; xem `docs/API_BASE.md`.
4. Thay đổi schema mới đặt trong resource migration có version mới và thêm vào danh sách runner `DatabaseInitializer`. File đã áp dụng được kiểm checksum, không sửa lại. Các bảng nghiệp vụ trong đặc tả chưa được tạo; thống nhất thiết kế trước khi thêm migration.
5. Dữ liệu demo, tên nhà xe, giờ và giá phải được ghi rõ là minh họa.

`JdbcConnectionFactory.open()` mượn Connection từ HikariCP, đặt lại search_path theo schema cấu hình; close trả connection về pool. Pool mặc định tối đa 4 connection/instance, minimumIdle=0, không ping giữ DB thức, đóng khi Spring undeploy. DAO dùng PreparedStatement/ResultSet/try-with-resources. Nghiệp vụ nhiều DAO dùng một connection của service cho toàn giao dịch, commit/rollback xong mới trả; DAO tham gia không tự đóng connection đó. Lỗi SQL/runtime/Error đều rollback, Error vẫn ném tiếp.

Giới hạn cấu hình: `VIETROUTE_DB_POOL_SIZE` mặc định 4 (1–10), `VIETROUTE_DB_ACQUIRE_TIMEOUT_MS` 20000 (1000–60000), `VIETROUTE_DB_CONNECT_TIMEOUT_SECONDS` 15 (1–60), `VIETROUTE_DB_SOCKET_TIMEOUT_SECONDS` 30 (1–300). PostgreSQL timeout là thuộc tính driver; nếu thêm timeout cùng tên vào JDBC URL, pgJDBC ưu tiên URL. Không dùng query timeout vô hạn cho luồng nghiệp vụ dài; tách tác vụ dài và đặt timeout phù hợp thay vì tăng pool tùy ý. Mỗi instance có pool riêng nên tổng connection cần tính theo số instance. Khởi tạo bean/đóng gói Maven không mở kết nối; initializer bật hoặc request cần DAO mới dùng DB.

Không đưa mật khẩu database hoặc dữ liệu khách thật vào Git. `.env.example` chỉ là mẫu, Java không tự đọc `.env`.

URL PostgreSQL JDBC có dạng `jdbc:postgresql://<host>/neondb?sslmode=require&channelBinding=require&connectTimeout=15`; user/password truyền riêng. Không dán nguyên lệnh `psql` vào `VIETROUTE_DB_URL`. Với pgJDBC, tham số đúng là `channelBinding`, khác `channel_binding` của psql.

Các file SQL mới dùng cú pháp PostgreSQL (`GENERATED ... AS IDENTITY`, `BOOLEAN`, `TIMESTAMPTZ` khi phù hợp), không dùng `AUTO_INCREMENT`, backtick hoặc `ENGINE=InnoDB`. CI build container không cần truy cập Neon và không tự chạy migration/seed vào production.

Initializer tạo `auth_roles`, `auth_users`, `auth_user_roles`, `catalog_items` và `vietroute_schema_history`; seed 4 role, admin và 5 mục loại xe/nguồn đặt chỗ. Chạy lặp không tạo trùng hoặc reset mật khẩu. Phần này là runtime được bật có chủ đích, không chạy trong Maven package. CI xác minh bằng PostgreSQL container tạm riêng.

## Schema bắt buộc và nâng cấp

V003 tạo `auth_permissions`, `auth_role_permissions`, audit/version quyền trên `auth_roles` và `system_jobs` có index nhận việc/lease/retention/audit. V001/V002 giữ nguyên checksum. Seed role thường chỉ cấp mặc định một lần, không ghi đè quyền đã sửa; ADMIN là role cứu hộ cố định. `JobRepository.enqueue` nhận Connection của service để outbox commit/rollback cùng dữ liệu nghiệp vụ. Worker dùng SKIP LOCKED và lease token; xem [NEN_TANG_MO_RONG.md](../docs/NEN_TANG_MO_RONG.md).

Database production vẫn là neondb. **Schema cấu hình bằng VIETROUTE_DB_SCHEMA**, mặc định vietroute từ db.schema trong database.properties. Mỗi connection mượn pool đặt lại search_path; query runtime chỉ ghi tên bảng, entity không ghi schema. Bật initializer để bootstrap schema cấu hình. Giữ byte V001–V003, LegacyMigrationSql render hai migration lịch sử ở RAM trước thực thi với checksum nguồn. Đọc [schema và quyền theo hành động](../docs/SCHEMA_VA_PHAN_QUYEN.md) để đổi schema mới hoặc rename schema đang có dữ liệu; startup chặn history khác nơi để tránh seed nhầm.

- DB mới: tạo schema cấu hình và bảng vietroute_schema_history, chạy V001–V003 theo path/render tương thích, kiểm chứng metadata rồi seed.
- DB cũ V001 ở public: chuyển history sang schema cấu hình giữ checksum, bỏ qua V001; V002 chuyển bốn bảng ứng dụng theo OID, giữ ID/FK/sequence/index/dữ liệu, V003 bổ sung quyền/job. Không reset admin/hash.
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
