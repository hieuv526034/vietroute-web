# Common gọn và cấu trúc web — COMMON01

Common hiện có **61 file Java**, giảm từ 148 trước quy hoạch và gồm một class chuẩn bị JDBC Oracle 26ai theo ORACLECFG01. Auth giữ 40 file; business 121 vì đã gỡ module GPS. Mọi class production dưới 1000 dòng; không record/var.

## Phần giữ và phần bỏ

| Nhóm | File Java | Trách nhiệm |
|---|---:|---|
| api_journal | 10 | Xem request, user đã xác thực, thời gian, payload/response đã lọc; UI xuất cURL/Postman |
| config | 10 | Root/WAR, cấu hình ứng dụng, datasource/JPA và Spring MVC |
| initializer | 2 | DatabaseInitializer và MigrationCatalog: chuẩn bị schema/history, manifest/checksum, migration/seed startup |
| jdbc | 9 | Connection/schema/transaction, metadata/index, PostgreSQL và cấu hình JDBC Oracle 26ai |
| base | 7 | Controller, entity/audit DTO, JPA repo, JDBC BaseRepository và phân trang |
| web | 13 | Home, model/menu/breadcrumb, trace/body guard, locale, Swagger và envelope |
| constant | 4 | ApplicationConstants và mã lỗi/thành công/validation |
| exception | 3 | BusinessException, mapper và MVC advice |
| validation | 3 | BusinessCode, BusinessRules và RequestGroups |

Đã bỏ toàn bộ job/mail/media/monitoring/traffic cùng settings/wiring/provider; không chuyển adapter cũ sang package khác. Bỏ upload/avatar/logo/photo, thống kê online, quota IP/Redis, Prometheus/metrics và màn cấu hình vận hành. Giữ bộ đếm failedWrites của journal, không có collector bên ngoài. Tiến độ chuyến và phân quyền/scope/ownership của business vẫn giữ.

## Vì sao common.web còn cần

Spring MVC cần controller trang, locale, converter/error envelope, layout/menu và Swagger. Bỏ chúng sẽ làm API và Thymeleaf mất hợp đồng dùng chung. Chỉ giữ 13 file có caller thật; ApiErrorWriter gộp vào ApiResponses, PageBreadcrumbs gộp vào PageModelAdvice. Request-body advice gộp vào GlobalExceptionHandler. Ba lớp OpenAPI tách theo kiểu thư viện để tránh trùng tên Schema/Operation/Parameter và tránh tên lớp đầy đủ trong thân hàm.

ApiResponse, PageResponse, FieldViolation và journal DTO là class có getter/constructor. BaseResponse và BaseRequest rỗng đã bỏ; response có audit kế thừa BaseDTO trực tiếp. Request không nhận audit, actor hoặc scope tin từ client. BusinessRules chứa quy tắc mật khẩu/email/sinh mã; không tạo annotation password hoặc util chỉ để bọc một method.

## Cấu trúc nhật ký

```text
common/api_journal/
  controller/ApiJournalPageController
  controller/api/ApiJournalAPI
  dto/response/ApiJournalEntry, ApiJournalDetail, ApiJournalResponse
  dto/search/ApiJournalSearch
  entity/ApiJournal
  repo/ApiJournalRepo
  service/ApiJournalService
  filter/ApiJournalFilter
```

Repo giữ SQL; service giữ truy vấn có principal/target đã duyệt, projection và ghi trực tiếp có backoff. Filter giữ state nội bộ theo request. Pool journal một connection được cấu hình ngay DatabaseConfig. Cleanup journal/history chạy theo lô cùng lần bảo trì, không worker hoặc RAM queue. Xem [hợp đồng nhật ký](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md).

## Hằng số và cấu hình

Env/Routes/Regex/Http ở ApplicationConstants; dùng ConfigValues và HttpHeaders chuẩn. Key seed email/password được JSON tham chiếu gián tiếp vẫn phải có trong Env và `.env.example`. Không tự loại key chỉ vì không thấy caller Java trực tiếp. WebRoutePolicy sở hữu public/self-service/JWT; thêm constant không cấp quyền. RequestGuardFilter giữ giới hạn JSON 64 KiB, tối đa hai thao tác BCrypt đồng thời; CSRF, JWT và khóa tài khoản sai mật khẩu vẫn giữ.


Khi thêm chức năng mới, chỉ thêm class khi có trách nhiệm thật; DTO search tên *Search trong dto/search. Không thêm compatibility wrapper/record/accessor cũ hoặc phục hồi module đã bỏ. Chạy formatter, rules, docs và kiểm thử phù hợp trước bàn giao.

GPS01: RootConfig/WebApplicationInitializer ở common/config, DatabaseInitializer ở common/initializer; không còn TrackingSettings hoặc bean GPS. Query/transaction và WebRoutePolicy giữ trách nhiệm hiện hành.

## JDBC và khởi tạo database

common.jdbc giữ sáu file JdbcConnectionFactory, JdbcTransactions, JdbcSchema, JdbcSchemaValidator, JdbcTable, JdbcIndex; dialect/ có JdbcDialect, PostgreSqlDialect và OracleJdbcDialect, tổng chín file. Factory mượn connection/chọn schema, Transactions quản lý commit/rollback hoặc tham gia JPA; không gộp hai trách nhiệm này. Annotation metadata giữ độc lập để entity dễ khai báo.

DatabaseSettings chọn database/{postgresql,oracle}.properties bằng VIETROUTE_DB_VENDOR hoặc suy từ URL cũ; chỉ nhận hai tên cố định. Driver, Hibernate dialect, schema và thuộc tính timeout theo cùng vendor. OracleJdbcDialect mới chuẩn bị driver/options/validation; JdbcSchema chặn runtime Oracle trước SQL vì chưa có migration/repo/schema transaction Oracle. Không tạo migration Oracle rỗng hoặc dùng ALTER SESSION để giả lập LOCAL search_path. Cách cấu hình và giới hạn tại [chạy local](../01-bat-dau/CHAY_LOCAL.md#chọn-postgresql-hoặc-chuẩn-bị-oracle-26ai).

BaseRepository ở common.base cùng JpaRepositorySupport; các repo/import dùng trực tiếp package mới. MigrationCatalog ở common.initializer cùng DatabaseInitializer. Phần chuẩn bị schema/history trước đây tách lớp nay là private prepareSchema/hasTargetTables trong DatabaseInitializer, dưới cùng advisory lock/transaction. Giữ kiểm nhiều history/sai schema/thiếu history, checksum và seed; không class alias hoặc nested wrapper, không đổi SQL/dữ liệu.
