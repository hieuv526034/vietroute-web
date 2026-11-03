# Quy ước và luồng backend

Đọc [AGENTS](../../AGENTS.md) cho quy tắc bắt buộc, [schema/quyền](SCHEMA_VA_PHAN_QUYEN.md) cho ràng buộc DB và [phần thành viên](../05-ke-hoach/PHAN_CONG.md) cho đường đọc source. Tài liệu này là điểm tra cứu chung, không liệt kê lại mọi DTO/endpoint đã có Swagger.

## Package theo nhóm nghiệp vụ

```text
vn.vietroute/
  auth/
  business/{catalog,carrier,trip,booking,report,bootstrap}/
    controller/           PageController
    controller/api/       REST API
    dto/{request,response,search}/
    entity/ repo/ service/
    constant/ initializer/   chỉ tạo khi có trách nhiệm thật
  common/
    config/{application,database,web}/
    initializer/ jdbc/ base/ web/ api_journal/
    constant/ exception/ validation/
```

Tên bám tài nguyên: User/UserRepo/UserService/UserAPI/UserPageController. Auth UserDTO/RoleDTO/FunctionDTO ở auth.dto; *Request ở dto/request, *Search ở dto/search. Không entity hậu tố Entity, không *SearchRequest. Không tạo service/API rỗng cho đủ số bảng nối; không giấu nghiệp vụ trong common để giảm thống kê.

Một Request/tài nguyên cho create/update/state theo RequestGroups; response tách public/PII. Không record/var production, wrapper rỗng hoặc nested DTO để giấu số class. Mỗi class dưới 1000 dòng; xem phương án tách khi thêm trách nhiệm, không tăng trần. Initializer ngang service; hằng nghiệp vụ ở module/constant, hằng môi trường/route/regex/HTTP ở ApplicationConstants.

## Luồng request và trách nhiệm mỗi tầng

| Tầng | Làm gì | Không đặt ở đây |
|---|---|---|
| Filter/Security/EndpointAccess | Trace/body limit, CSRF/JWT, account và grant endpoint từ DB | Quyết định giá/chỗ, tin role client |
| API | Bind Request/Search, validation và Swagger, lấy actor server, gọi execute | SQL và transaction nghiệp vụ |
| Service | Trạng thái, version, trần ủy quyền, transaction, phối hợp repo | Ghép SQL hoặc loop query/save từng row |
| Repo | Query, scope/owner/parent, count/page, khóa/CAS, batch/JPA | Commit riêng hoặc cấp quyền theo tên role |
| DTO/envelope | Projection được phép, lỗi vi/en và traceId | Entity/hash/tokenVersion/PII không được công bố |

Page dùng @Controller trong controller; API @RestController trong controller/api. Auth/business API kế thừa BaseController, trả ApiResult<T> qua execute; HTTP body thực là ApiResponse<T>. Lỗi trước controller dùng ApiExceptionMapper/GlobalExceptionHandler để giữ status, locale và traceId. Service gọi UserPrincipal.current/requireCurrent đúng ranh giới đã duyệt; không truyền principal giả vào helper ghi dữ liệu.

## Hợp đồng API

Envelope có success/code/messageKey/message/data/errors/timestamp/path/traceId/locale. Không dùng HTTP200 chứa cờ lỗi để thay status. BusinessException(ErrorCode,args), key thông báo có đủ vi/en; lỗi không phản chiếu password, SQL hoặc stack trace. Chọn ngôn ngữ bằng lang, rồi X-Locale, theo SupportedLocaleResolver.

| Status thường gặp | Ý nghĩa cần trình bày |
|---|---|
| 400 | Request/quan hệ field sai; sửa field và chủ động gửi lại |
| 401 | Chưa xác thực hoặc phiên không hợp lệ |
| 403 | Thiếu grant, CSRF hoặc quyền phù hợp; đọc code/messageKey để phân biệt |
| 404 | Không tồn tại hoặc ngoài scope/owner nhằm tránh lộ dữ liệu |
| 409 | Stale version, xung đột trạng thái, mã hoặc requestKey |
| 413/429/503 | Body quá lớn, giới hạn đồng thời hoặc tài nguyên tạm không sẵn sàng |

Mỗi API có @Operation; field Request/DTO/Search có @Schema và validation theo operation. Principal/HttpServletRequest là server-injected, không là query/body. Paging page từ 0, size tối đa 100; count/page phải lọc cùng scope, sort/identifier whitelist phía server. Giá trị bind parameter qua JPA; PreparedStatement dành cho batch/hạ tầng, không nối giá trị client vào SQL. Swagger/OpenAPI lấy từ MVC; inventory quyền cũng lấy MVC nhưng không dùng JSON Swagger làm nguồn cấp grant.

Đọc payload chi tiết tại API source của khối mình hoặc Swagger đúng bản đang chạy. Trạng thái yêu cầu Swagger chỉ quản trị nằm tại [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md); không hứa bản chưa merge đã được triển khai.

## JPA và JDBC chung transaction

Quy ước bổ sung03/11/2026: query nghiệp vụ tra cứu, CRUD và khóa dùng Spring Data/JPA/Hibernate; JDBC nghiệp vụ chỉ dành cho ghi batch/bulk theo tập. Repo interface dùng @Query; Repo có implementation riêng dùng EntityManager/native SQL. Giữ SQL đầy đủ bằng text block, named parameter rõ nghĩa; không nối giá trị client hoặc giấu query trong executor tự viết. Hạ tầng migration/schema/transaction và journal pool riêng tiếp tục dùng JDBC. Đây là hướng chuyển đổi: ngoài bốn Repo ở QUERY01, source còn query JDBC cũ; trạng thái và phạm vi chuyển tiếp ghi trong tiến độ, không trình bày như đã chuyển hết.

JpaConfig dùng JpaTransactionManager, SharedEntityManagerCreator và TransactionAwareDataSourceProxy. JdbcTransactions.execute tham gia transaction hiện tại hoặc tạo transaction qua manager; JPA saveAndFlush và JDBC mapping/history cùng rollback. Repo không bật autocommit hoặc commit connection riêng. Sau SQL atomic đổi version, refresh entity đang managed trước save tiếp.

JdbcTransactions ở service dùng TransactionTemplate với REQUIRED, cả khi đã có transaction: callback con lỗi sẽ đánh dấu rollback-only; caller bắt lỗi không làm phần ghi trước đó được commit. Template không phụ thuộc lời gọi qua proxy. Với @Transactional, đặt annotation tại entry method được bean khác gọi qua Spring proxy; không đặt ở private method hoặc trông chờ hàm cùng class gọi nhau sẽ mở transaction. Các default method Repo đọc theo tập mở transaction ở hàm ngoài; query con không tự mở transaction. FunctionRepo.lock và TripStopRepo.mark dùng MANDATORY: thiếu transaction của service phải lỗi trước SQL, giữ khóa/ghi tiến độ/history tới cuối nghiệp vụ.

SQL dài dùng text block, format SELECT/FROM/JOIN/WHERE/CTE/CASE/khóa/paging rõ ràng. Viết HOA từ khóa SQL/JPQL, hàm SQL dựng sẵn và kiểu dữ liệu; giữ nguyên literal, tên tham số/thuộc tính và identifier có quote. Không đổi kiểu chữ migration đã áp hoặc checksum. Loop tính dữ liệu đã tải, bind addBatch hoặc lặp tập enum hữu hạn để dựng SQL whitelist là hợp lệ; tránh loop query/save theo từng bản ghi. Khóa nghiệp vụ theo trip → booking → segments; không gọi provider mạng khi giữ khóa. Lỗi history nghiệp vụ phải rollback mutation; journal là cơ chế ghi riêng có thể thất bại mà nghiệp vụ vẫn thành công.

Connection bắt đầu transaction trước SET LOCAL search_path; schema validated, pg_temp cuối, không fallback public/$user hoặc session state qua pooler. Hibernate không tự DDL, không OSIV. Entity chỉ dùng annotation JPA chuẩn; regression PostgreSQL đối chiếu PK/index với migration và metadata JPA. Startup không quét PK/index bằng validator riêng; DatabaseInitializer vẫn giữ migration/checksum/advisory lock/transaction và seed. Checksum kiểm file SQL đã áp, không chứng minh DB chưa bị sửa tay.

## Query khai báo và SQL để kiểm DB

Theo lựa chọn giữ số file, FunctionRepo, FunctionResourceRepo, RoleFunctionRepo và TripStopRepo là interface Spring Data được đăng ký bởi @EnableJpaRepositories trong JpaConfig. Có13 khai báo @Query native và một countQuery; SQL viết đầy đủ bằng text block, named parameter có @Param. Không tạo Custom/Impl hoặc executor annotation riêng. Các Repo còn cần implementation/dependency riêng giữ JPA/JDBC hiện hành; không coi toàn bộ source đã chuyển sang @Query.

Native query dùng `{h-schema}`; Hibernate thay bằng schema từ cấu hình, ví dụ `vietroute.auth_functions`. Entity không gắn schema cố định. Các query gán quyền vẫn giữ toàn bộ điều kiện user/role/function hiện hành, không cache hoặc suy grant từ tên role. Query trả Map chỉ là projection nội bộ repo, không trả thẳng ra API. Batch gán mapping, khóa trip/segments và history vẫn dùng JDBC trên cùng transaction JPA; cập nhật điểm chuyến bằng @Modifying giữ kiểm phân công/khóa trip tại service và CAS một dòng tại repo.

Xuất SQL có schema cụ thể từ chính text block trong Repo bằng [exporter](../../scripts/development/export-repository-queries.py):

```powershell
python -X utf8 scripts/development/export-repository-queries.py --schema vietroute
```

Mở `target/repository-queries.sql`:14 câu gồm cả count, table đã có schema, giữ `:thamSo` để bind trong DB editor hoặc thay giá trị khi kiểm. Query nhận Pageable có LIMIT/OFFSET tương ứng để không mất phần phân trang do Spring Data áp dụng. Giá trị `page` bắt đầu từ0, không là số trang hiển thị trên UI.

Người mới có thể tạo bản ví dụ đã điền tham số và chạy trực tiếp bằng:

```powershell
python -X utf8 scripts/development/export-repository-queries.py --schema vietroute --example --user-id 1 --page-size 20 --page 0
```

Mở `target/repository-queries-example.sql`:11 SELECT, gồm BEGIN READ ONLY/ROLLBACK, có schema/giá trị mẫu và LIMIT/OFFSET. Mẫu dùng userId1, role CUSTOMER, code ADMIN_CONSOLE; đổi theo dữ liệu cần kiểm. `active=NULL` nghĩa là không lọc trạng thái, `keyword=%` nghĩa là không lọc từ khóa. Bản này bỏ mutation và SELECT giữ khóa; giữ lệnh khóa/cập nhật tại bản template14 câu. Không coi giá trị mẫu là thông tin đăng nhập.

Công cụ không đọc .env, kết nối DB hoặc thực thi SQL; không đưa password/token/PII vào file. Chạy lại sau khi sửa query, không duy trì bản SQL chép tay thứ hai. Query JDBC cũ còn lại lấy từ Repo cùng transaction SET LOCAL search_path tới schema được chọn; không thêm tên schema từ input client.

Đối chiếu cơ chế với [Spring Data JPA3.5](https://docs.spring.io/spring-data/jpa/reference/3.5/jpa/query-methods.html) và [schema placeholder của Hibernate6.6](https://docs.hibernate.org/orm/6.6/userguide/html_single/#sql-global-catalog-schema). Oracle vẫn chưa hỗ trợ runtime; native SQL PostgreSQL không tự tương thích Oracle nhờ annotation.

## Entity, audit và thời gian

Entity đủ @Id/@Column/@Version/@Index theo migration, không schema cứng. Mỗi field có chú thích tiếng Việt: ngắn dùng //, giải thích dài dùng Javadoc. Generated column chỉ đọc, projection @Transient; DTO không phải entity. Thời gian nghiệp vụ/audit dùng Instant/TIMESTAMPTZ; Timestamp JDBC chỉ chuyển tại repo. Ngày tìm chuyến/báo cáo hiểu theo Asia/Ho_Chi_Minh, không mặc định timezone của browser.

| Thao tác | Actor audit |
|---|---|
| Startup/seed/bảo trì, bộ đếm sai password | SystemActor.ID=0, username system |
| CRUD/gán quyền/đơn/tiền/tiến độ | ID principal đã xác thực, kiểm endpoint/scope |
| Đổi password/logout | ID tài khoản đã xác thực |
| Journal | Actor kỹ thuật0; request_user_id riêng từ xác thực server |

System inactive/không role/không JWT; không dùng NULL hoặc hardcode ID admin. Giữ created_at/created_by khi update; updated_* và version từ server, không tin audit client. AccessChangeRepo chỉ append whitelist state cùng mutation; không có màn/API auth history.

Locale mặc định luôn vi, kể cả browser gửi Accept-Language tiếng Anh; chỉ đổi qua ?lang=en hoặc X-Locale: en. Chọn query ưu tiên header; locale tường minh không hỗ trợ trả400.

## Cấu trúc common và quy trình kiểm

RootConfig/WebApplicationInitializer ở gốc config; config con theo application/database/web. DatabaseInitializer/MigrationCatalog ở initializer; BaseRepository ở base, không alias JDBC cũ. common.web chỉ controller/response/filter/locale/advice/openapi/routing; journal ở api_journal. Không BaseRequest/BaseResponse rỗng hoặc khôi phục job/mail/media/metrics/GPS.

Import tường minh, không wildcard/FQCN trong code. Comment/Javadoc luồng và transaction bằng tiếng Việt; lambda chỉ chuyển tiếp invocation nguyên vẹn dùng method reference. Formatter khóa trong .idea/codeStyles/Project.xml và .editorconfig; không sửa rule để xanh.

Sau thay đổi: npm ci → format → format:check → check:rules → check:docs → kiểm thử phù hợp → git diff --check. Stage file mới sau review để checker đọc Git thấy đủ. H2 cho phần portable; PostgreSQL test loopback riêng cho array/JSONB/lock/index/FK/migration, không dùng .env/DB thật. Lệnh cụ thể và chuẩn bị demo tại [demo–hỏi đáp](../05-ke-hoach/DEMO_VA_HOI_DAP.md#kiểm-tra-trước-buổi-demo). Git/PR theo [CONTRIBUTING](../../CONTRIBUTING.md).
