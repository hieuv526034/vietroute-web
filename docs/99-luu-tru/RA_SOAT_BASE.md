# Rà soát nền tảng trước khi triển khai nghiệp vụ

> Bản rà soát lịch sử; trạng thái hiện tại xem [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), không suy ra tính năng còn thiếu từ danh sách cũ bên dưới.

Ngày rà soát: **22/10/2026**. Người phụ trách: **hieuv526034**. Phạm vi: common/auth/catalog mẫu, cấu hình Java/JDBC/Thymeleaf, schema, Swagger, Docker và CI/Render. Không kiểm tra hoặc sửa dữ liệu Neon production.

Đây là kết quả tại PR #7. Bổ sung sau đó ở [nền tảng mở rộng](../02-phat-trien/NEN_TANG_MO_RONG.md) và [schema/quyền theo hành động](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md): schema đã cấu hình tập trung, catalog đã có sửa/xóa mềm cùng optimistic version và quyền READ/CREATE/UPDATE/DELETE riêng.

## Đánh giá bộ công nghệ

Bộ công nghệ hiện tại phù hợp để nhóm bắt đầu triển khai nghiệp vụ theo mẫu catalog: Java 17+ (Docker dùng 21), Spring MVC 6.2.19, Security 6.5.11/Nimbus JWT, Thymeleaf 3.1.5, JDBC PostgreSQL 42.7.14, HikariCP 7.1.0, WAR/Tomcat 10.1, Bootstrap/Axios/WebJars. Hibernate Validator chỉ phục vụ Bean Validation, không có ORM. Không cần thêm Spring Boot, JPA, Redis hoặc microservice để hoàn thiện base đồ án hiện tại.

**Điểm chưa thể kết luận ổn cho vận hành lâu dài: vòng đời Spring 6.2.** Hỗ trợ mã nguồn mở đã kết thúc tháng 06/2026 theo [Spring Framework Versions](https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-Versions). Bản 6.2.19 đang dùng tương thích với stack bắt buộc của dự án; cập nhật bản vá sau mốc hỗ trợ có thể cần kênh thương mại. Cần lên kế hoạch riêng chuyển sang dòng còn được hỗ trợ, kiểm tra đồng bộ Spring Security, Servlet/Tomcat và Thymeleaf, hoặc có hỗ trợ thương mại nếu giữ Spring 6. Không tự đổi major trong lượt sửa base này.

Đã đối chiếu thông báo bảo mật chính thức theo điều kiện sử dụng thực tế: [CVE-2026-59313](https://spring.io/security/cve-2026-59313/) yêu cầu SSE qua functional MVC; [CVE-2026-47892](https://spring.io/security/cve-2026-47892/) liên quan functional WebFlux; [CVE-2026-59283](https://spring.io/security/cve-2026-59283/) cần SimpleEvaluationContext cùng SpEL compiler. Source hiện không dùng các luồng đó; Thymeleaf compiler cũng được tắt. Đây là đối chiếu phạm vi source, không thay thế bản vá framework, quét toàn bộ CVE hoặc kiểm thử xâm nhập.

## Các thiếu sót đã xử lý

| Phần | Trước rà soát | Kết quả |
|---|---|---|
| Kết nối JDBC | Mỗi thao tác mở kết nối vật lý qua DriverManager; chưa có giới hạn đồng thời chung | HikariCP tối đa 4 connection/instance, mượn/trả qua DataSource, đóng pool khi undeploy |
| Timeout | Chưa đặt connect/socket/acquisition timeout tập trung | DatabaseSettings kiểm tra khoảng cấu hình; mặc định chờ pool 20s, connect 15s, socket 30s |
| Chi phí Neon/Render | Chưa có chính sách pool cho instance RAM nhỏ/DB có thể ngủ | Pool tạo lười, minimumIdle=0, idleTimeout=60s, không keepalive ping; không mở DB chỉ vì tạo Spring context |
| Cấu hình môi trường | Gõ sai ENV có thể rơi vào development; production có thể dùng DB mặc định hoặc JWT placeholder | Chỉ nhận development/production; production bắt buộc khai báo DB credentials và JWT secret thật |
| Transaction lỗi nghiêm trọng | Callback ném Error chưa rollback tường minh | Rollback rồi ném tiếp Error, trả connection; HTTP vẫn chỉ bắt Exception |
| JSON input | Key trùng/trailing object/null primitive có thể được chấp nhận | Trả COM002/400 trước service; giữ chặn field ngoài DTO/audit |
| Lỗi HTTP | Client yêu cầu XML có thể làm mất envelope 406; 405 thiếu Allow | Ép JSON cho envelope lỗi, giữ header giao thức do MVC cung cấp |
| Swagger cookie | CSRF chỉ lấy một lần khi mở trang, có thể cũ sau login/logout | Lấy CSRF hiện hành trước mỗi mutation; không lưu Authorization qua reload |
| Bộ lọc CI | Sửa riêng script kiểm tra có thể không chạy workflow | `.github/scripts/**` được thêm đồng bộ ở GitHub Actions và Render build filter |
| Khả năng đọc code | Luồng pool/config mới chưa có tài liệu | Comment tiếng Việt, tài liệu config/API/transaction và báo cáo này; format chung |

Tham khảo thiết lập pool tại [HikariCP](https://github.com/brettwooldridge/HikariCP). `socketTimeout` là timeout đọc mạng, không phải giới hạn tổng thời gian một giao dịch. Query/index và cạnh tranh dữ liệu cần được thiết kế theo từng nghiệp vụ; không tăng pool để che query chậm. URL JDBC có timeout trùng thuộc tính driver sẽ ưu tiên giá trị URL theo [pgJDBC connection parameters](https://jdbc.postgresql.org/documentation/use/).

## Những nền tảng đã có và được giữ đúng

- Cấu trúc common/auth/business, BaseController cùng execute/try-catch, response factory, mapper/advice chung, mã lỗi/key vi/en, locale và traceId. DTO có Swagger, audit/timestamp dùng Instant.
- JWT có issuer/audience/thời hạn/tokenVersion, BCrypt, kiểm tra trạng thái/quyền từ DB; cookie HttpOnly/SameSite, CSRF cho mutation; Bearer không lưu localStorage. Login sai commit bộ đếm trước khi trả lỗi; logout/đổi mật khẩu thu hồi token.
- Schema vietroute, PreparedStatement, whitelist phân trang/sort, PK/UNIQUE/FK/index và metadata entity. Migration có checksum/advisory transaction lock, seed lặp không trùng/không reset password; V001/V002 giữ nguyên.
- Production Docker chỉ copy src/main, dùng -Pproduction, không có Node/test compiler/test dependency trong WAR. Cache Maven/layer và Render checksPass được giữ; docs/test-only không kích hoạt build.

## Bằng chứng kiểm tra

- `npm ci`, `npm run format`, `npm run format:check`: bộ formatter khóa phiên bản, không tham gia build production.
- `mvnw verify`: **28 kiểm thử Java** qua, gồm HTTP/envelope/locale/CSRF/JWT/validation, cấu hình sai, pool tạo lười, commit/rollback và đóng connection. Không dùng DB thật.
- `node --test src/test/resources/dev/swagger.test.cjs`: **2 kiểm thử** qua; kiểm token CSRF được làm mới cho từng mutation, GET không tải token và mutation dừng nếu refresh lỗi. Node chỉ dùng khi phát triển.
- `mvnw -Pproduction clean package`: kiểm WAR có PostgreSQL/HikariCP, không có test/fixture/.env/JUnit/spring-test; production bỏ cả testCompile lẫn test execution.
- Actionlint, Bash syntax và Render schema/build filters: hợp lệ. CI Docker thực thi tiếp trên PostgreSQL tạm: tạo mới, hai instance bootstrap đồng thời, migration legacy public → vietroute, giữ hash/ID/sequence, role/lock/JWT/CSRF/catalog, JSON không hợp lệ và nhiều request dùng pool tối đa 4 connection.

Kết quả Docker/PostgreSQL phải xem check **Build and check production container** của PR tương ứng; chỉ merge khi check hoàn tất thành công. Máy hiện tại không có Docker; không thay việc kiểm container bằng kết quả Maven local. Chưa kiểm thử tải dài hạn, khôi phục backup hoặc deploy lên tài khoản Render của chủ dự án.

## Các phần cần làm theo module tiếp theo

1. Chốt vòng đời framework trước khi vận hành dài hạn. Theo dõi dependency mới theo nguồn chính thức, không coi Spring 6.2 là còn được hỗ trợ miễn phí.
2. Khi thêm nhà xe và người dùng nhân viên: gắn phạm vi nhà xe từ principal phía server, kiểm quyền tại service/repository; chưa có module/tenant thì không tuyên bố base đã có phân quyền đa nhà xe.
3. Khi thêm API sửa/xóa: dùng version trong điều kiện UPDATE và trả STALE_VERSION khi không còn khớp; hiện catalog mới có tạo/đọc nên chưa có luồng optimistic update thực tế.
4. Booking/tồn chỗ cần transaction, khóa dữ liệu và idempotency theo đặc tả. Maps/tracking cần cấu hình nhà cung cấp và hợp đồng dữ liệu riêng. Không dùng generic CRUD để thay các quy tắc này.
5. `mustChangePassword` hiện là cờ hướng dẫn đổi mật khẩu trên tài khoản; chưa phải chính sách chặn mọi API khi chưa đổi. Nếu yêu cầu bắt buộc, cần thống nhất các endpoint được phép trước khi thêm kiểm soát.
6. Khi đưa vào sử dụng thực: nghiệm thu login/danh mục trên Render, quyền DB và quy trình backup/restore. `/health` chỉ kiểm web availability; không kiểm DB. Cấu hình production và kiểm thử vận hành không được suy ra từ việc build thành công.

Thứ tự/người phụ trách nghiệp vụ xem [PHAN_CONG.md](../05-ke-hoach/PHAN_CONG.md); luồng học base xem [LUONG_XU_LY_BASE.md](../02-phat-trien/LUONG_XU_LY_BASE.md).
