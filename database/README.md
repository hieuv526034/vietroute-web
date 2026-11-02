# Database VietRoute

DB hiện hành là PostgreSQL hieuboy, schema mặc định vietroute; không fallback Neon. Profile [postgresql.properties](../src/main/resources/database/postgresql.properties) giữ URL/user/schema, password trống; env ưu tiên. Oracle26ai mới chuẩn bị driver/profile và còn chặn runtime.

- [Schema, tám bước migration và khởi tạo](../docs/02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md).
- [JPA/JDBC, transaction, SQL và audit](../docs/02-phat-trien/QUY_UOC_PHAT_TRIEN.md).
- [Backup, rebaseline và vận hành](../docs/04-van-hanh/VAN_HANH.md#database-backup-và-rollback).

[000-create-database.sql](000-create-database.sql) chỉ dựng PostgreSQL local; không chạy trên DB thật. .env chỉ local, không Git/WAR/image; .env.example giữ đầy đủ cấu hình theo chủ dự án, chỉ VIETROUTE_DB_PASSWORD là XXX. Không chạy seed/cleanup hoặc đổi history DB thật để thử build.
