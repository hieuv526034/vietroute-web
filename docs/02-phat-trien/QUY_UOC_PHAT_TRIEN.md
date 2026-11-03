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

Mỗi API có @Operation; field Request/DTO/Search có @Schema và validation theo operation. Principal/HttpServletRequest là server-injected, không là query/body. Paging page từ 0, size tối đa 100; count/page phải lọc cùng scope, sort/identifier whitelist phía server. Dữ liệu bind PreparedStatement, không nối giá trị client vào SQL. Swagger/OpenAPI lấy từ MVC; inventory quyền cũng lấy MVC nhưng không dùng JSON Swagger làm nguồn cấp grant.

Đọc payload chi tiết tại API source của khối mình hoặc Swagger đúng bản đang chạy. Trạng thái yêu cầu Swagger chỉ quản trị nằm tại [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md); không hứa bản chưa merge đã được triển khai.

## JPA và JDBC chung transaction

JpaConfig dùng JpaTransactionManager, SharedEntityManagerCreator và TransactionAwareDataSourceProxy. JdbcTransactions.execute tham gia transaction hiện tại hoặc tạo transaction qua manager; JPA saveAndFlush và JDBC mapping/history cùng rollback. Repo không bật autocommit hoặc commit connection riêng. Sau SQL atomic đổi version, refresh entity đang managed trước save tiếp.

SQL dài dùng text block, format SELECT/FROM/JOIN/WHERE/CTE/CASE/khóa/paging rõ ràng. Loop tính dữ liệu đã tải, bind addBatch hoặc lặp tập enum hữu hạn để dựng SQL whitelist là hợp lệ; tránh loop query/save theo từng bản ghi. Khóa nghiệp vụ theo trip → booking → segments; không gọi provider mạng khi giữ khóa. Lỗi history nghiệp vụ phải rollback mutation; journal là cơ chế ghi riêng có thể thất bại mà nghiệp vụ vẫn thành công.

Connection bắt đầu transaction trước SET LOCAL search_path; schema validated, pg_temp cuối, không fallback public/$user hoặc session state qua pooler. Hibernate không tự DDL, không OSIV. Entity chỉ dùng annotation JPA chuẩn; regression PostgreSQL đối chiếu PK/index với migration và metadata JPA. Startup không quét PK/index bằng validator riêng; DatabaseInitializer vẫn giữ migration/checksum/advisory lock/transaction và seed. Checksum kiểm file SQL đã áp, không chứng minh DB chưa bị sửa tay.

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
