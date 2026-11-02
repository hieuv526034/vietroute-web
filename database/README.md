# Database VietRoute

PostgreSQL, schema cấu hình bằng VIETROUTE_DB_SCHEMA (mặc định vietroute) trong database của môi trường. Production chủ dự án chỉ định neondb trên Neon. Không prefix schema trong entity/query runtime. [000-create-database.sql](000-create-database.sql) chỉ tạo PostgreSQL local, không chạy trên Neon.

## Baseline theo cụm

Tám SQL trong src/main/resources/db/migration/postgresql: auth/catalog/carrier/trip/booking/common mỗi cụm 001_schema.sql; foundation có 001_system_account.sql và 002_references.sql. Manifest quyết định thứ tự bảng/PK/index → system → FK. Có 24 bảng cuối cùng: auth 8, catalog 3, carrier 3, trip 6, booking 2, journal 1, history 1. Report/bootstrap không có bảng riêng.

Database trắng chạy startup khi DB_INIT bật có chủ đích. Không giữ SQL/adapter lịch sử trong runtime. Database đã có history khác cần backup, kiểm phục hồi, dừng node, dùng công cụ rebaseline --check rồi --apply --confirm-stopped theo [hướng dẫn chính](../docs/02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#database-đã-có-dữ-liệu). Công cụ đối chiếu cấu trúc trước khi chỉ đổi history; không xóa bảng/tài khoản hoặc chạy seed. Source không đồng nghĩa Neon đã nhận baseline.

## Connection và transaction

JPA CRUD + JDBC batch/query phức tạp dùng cùng JpaTransactionManager/connection. Transaction bắt đầu trước LOCAL search_path schema đã validate với pg_temp cuối; không public/$user fallback, không bật autocommit hoặc dựa session state qua transaction pooler. JdbcTransactions chịu commit/rollback; history lỗi rollback mutation.

Pool mặc định bốn connection nghiệp vụ và một journal/node; minimumIdle=0, không ping giữ DB thức. Main connect 15s/socket 30s/acquire 20s; journal connect/socket 2s/acquire 250ms/statement 1s, writer chờ 100ms và backoff 30s. Failed-write counter công bố số lượt ghi thất bại, không queue RAM hoặc đảm bảo đủ trace khi DB lỗi.

Instant/TIMESTAMPTZ, audit principal hoặc system=0, bind PreparedStatement và sort/fragment whitelist. .env gốc được phép theo dõi Git nhưng không vào WAR/image/docs/log; launcher local chủ động đọc, OS/IDE ưu tiên, WAR/Render chỉ nhận env. Test/build không tự đọc .env hoặc ghi Neon.

Xem [schema/quyền](../docs/02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [luồng request](../docs/02-phat-trien/LUONG_XU_LY_BASE.md), [nhật ký](../docs/04-van-hanh/NHAT_KY_API_VA_DEBUG.md). Dữ liệu nền do dự án tạo theo yêu cầu, chưa xác nhận hoạt động thật của một đơn vị.
