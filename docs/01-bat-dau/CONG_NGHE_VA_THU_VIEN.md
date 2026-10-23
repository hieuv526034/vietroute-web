# Công nghệ và thư viện VietRoute

Kho tài liệu Markdown của nhóm là thư mục **docs/** trong Git; cửa vào duy nhất là [mục lục và lộ trình đọc](../README.md). Trang này dùng để tra công nghệ, giải thích lựa chọn khi trao đổi/bảo vệ đồ án và tìm code minh họa. Đọc cùng [tổng quan kiến trúc](TONG_QUAN_HE_THONG.md), [luồng một request](../02-phat-trien/LUONG_XU_LY_BASE.md) và [tiến độ thực tế](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

Đối chiếu source ngày **23/10/2026**. Phiên bản bên dưới lấy từ [pom.xml](../../pom.xml), [Dockerfile](../../Dockerfile), [Maven Wrapper](../../.mvn/wrapper/maven-wrapper.properties) và [package-lock.json](../../package-lock.json), không phải tuyên bố các thư viện đều là bản mới nhất. Khi nâng dependency, cập nhật bảng cùng PR và kiểm tương thích trước merge. Dependency gián tiếp lấy theo BOM; dùng `mvnw dependency:tree` khi cần đối chiếu đầy đủ, không tự khóa thêm phiên bản chỉ để khớp tài liệu.

## 1. Giới thiệu dự án trong một phút

VietRoute là ứng dụng Java server render giao diện bằng Thymeleaf, cung cấp API JSON qua Spring MVC. Spring Security kiểm JWT/CSRF và quyền chức năng, service giữ transaction, repository dùng JDBC/PreparedStatement để đọc ghi PostgreSQL. Giao diện dùng Bootstrap, JavaScript và Axios. Ứng dụng đóng gói WAR, chạy Tomcat trong Docker, GitHub Actions kiểm tra trước khi Render triển khai nhánh main. Tác vụ nền/mail dùng hàng đợi PostgreSQL; ảnh công khai có adapter Cloudinary. Nền hệ thống đã có; nhà xe, tuyến/chuyến, đặt chỗ và Maps theo [kế hoạch nhóm](../05-ke-hoach/PHAN_CONG.md).

## 2. Backend, HTTP và xử lý dữ liệu

| Công nghệ/thư viện | Phiên bản trong source | Dùng để làm gì | Điểm đọc code |
|---|---|---|---|
| Java | Target 17; Docker build/runtime 21 | Ngôn ngữ backend, `Instant`, collections, executor và HTTP client | [pom](../../pom.xml), [launcher local](../../src/dev/java/vn/vietroute/dev/VietRouteApplication.java) |
| Spring Framework / Spring MVC | 6.2.19 | Dependency injection, mapping controller, binding/validation, response, MVC và interceptor | [RootConfig](../../src/main/java/vn/vietroute/common/config/RootConfig.java), [WebConfig](../../src/main/java/vn/vietroute/common/config/web/WebConfig.java) |
| Jakarta Servlet | 6.0.0 API, provided | Chuẩn request/response/filter/multipart; Tomcat cung cấp implementation | [khởi tạo WAR](../../src/main/java/vn/vietroute/common/config/WebApplicationInitializer.java) |
| Apache Tomcat | 10.1; embedded local 10.1.61 | Servlet container; chạy WAR production và Run/Debug local | [Dockerfile](../../Dockerfile), [cấu hình local](CHAY_LOCAL_VA_CLOUDINARY.md) |
| Jackson | BOM 2.22.4 | JSON request/response, `JavaTimeModule` cho Instant; chặn field lạ/key trùng/JSON dư | [RootConfig](../../src/main/java/vn/vietroute/common/config/RootConfig.java) |
| Jakarta Bean Validation / Hibernate Validator | Hibernate Validator 8.0.6.Final | `@NotBlank`, `@Size`, `@Valid` và annotation kiểm mã/mật khẩu; chỉ là validation, không ORM | [validation](../../src/main/java/vn/vietroute/common/validation/BusinessCode.java), [quy ước](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md) |
| Expressly | 5.0.0 | Jakarta Expression Language phục vụ thư viện validation | [pom](../../pom.xml) |
| Lombok | 1.18.42, provided | Sinh getter/setter/builder/constructor khi compile; đọc annotation để hiểu code được sinh | [BaseEntity](../../src/main/java/vn/vietroute/common/base/BaseEntity.java) |
| Swagger Core Jakarta | 2.2.55 | `@Operation`/`@Schema` mô tả hợp đồng API và DTO; bộ generator trong source dựng OpenAPI | [OpenAPI](../../src/main/java/vn/vietroute/common/web/openapi/OpenApiController.java), [API base](../02-phat-trien/API_BASE.md) |
| SLF4J + JDK logging | slf4j-jdk14 2.0.18 | Bridge log thư viện sang logging JVM; log theo traceId và dữ liệu đã lọc | [trace filter](../../src/main/java/vn/vietroute/common/web/filter/RequestTraceFilter.java) |

Source giữ Spring MVC/JDBC thuần; không có Spring Boot, Spring Data hoặc Hibernate ORM/JPA. `BaseController`, response factory, `BusinessException`, constants, DTO và locale là code nền tảng của dự án, không phải dependency riêng. Mỗi API/nghiệp vụ mới kế thừa và áp dụng quy ước này; đọc [API base](../02-phat-trien/API_BASE.md).

## 3. Xác thực và phân quyền

| Thành phần | Phiên bản/cách dùng | Trách nhiệm |
|---|---|---|
| Spring Security | 6.5.11 | Filter chain, method security tại root/servlet context, CSRF, cookie/Bearer và xử lý 401/403 |
| Nimbus JOSE JWT | Qua `spring-security-oauth2-jose` theo BOM | Ký/xác minh HS256, issuer/audience/exp/iat/jti; không tự viết thuật toán JWT |
| BCrypt | PasswordEncoder của Spring Security | Hash mật khẩu; không mã hóa hai chiều và không trả hash qua DTO |
| Quyền chức năng | Enum Permission + resource/action và mapping PostgreSQL | READ/CREATE/UPDATE/DELETE/ASSIGN; role gom quyền, scope/owner kiểm riêng ở service/SQL |
| Thu hồi JWT | tokenVersion và trạng thái tài khoản đọc từ DB | Logout/đổi mật khẩu/đổi role thu hồi token cũ; quyền cập nhật không chờ JWT hết hạn |

Điểm bắt đầu: [SecurityConfig](../../src/main/java/vn/vietroute/auth/config/SecurityConfig.java) → [JwtAuthenticationFilter](../../src/main/java/vn/vietroute/auth/security/JwtAuthenticationFilter.java) → [JwtService](../../src/main/java/vn/vietroute/auth/service/JwtService.java). Website dùng cookie HttpOnly; JavaScript không đọc cookie auth hoặc giữ access token trong localStorage. cURL/Postman xuất **URL thực + token placeholder** theo [hướng dẫn debug](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md); không mượn lại token của request lịch sử.

## 4. Database, transaction và tác vụ nền

| Công nghệ/thành phần | Phiên bản/cấu hình source | Vai trò và giới hạn |
|---|---|---|
| PostgreSQL | CI dùng PostgreSQL 17; runtime kết nối DB được cấu hình | SQL, FK/UNIQUE/CHECK/index, TIMESTAMPTZ, advisory lock và SKIP LOCKED; phiên bản Neon thật kiểm tại môi trường vận hành |
| PostgreSQL JDBC driver | 42.7.14 | Mở connection, PreparedStatement/ResultSet; chuyển Timestamp/Instant tại biên JDBC |
| HikariCP | 7.1.0 | Pool nghiệp vụ mặc định 4 connection/node; journal có pool riêng tối đa 1/node |
| Neon | Dịch vụ PostgreSQL production do chủ dự án cấu hình | DB `neondb`; schema cấu hình một nơi qua `VIETROUTE_DB_SCHEMA`, mặc định vietroute; credential ngoài Git |
| Migration/seed tự quản lý | V001–V009, checksum/history và transaction lock | Chạy lúc startup khi bật initializer; giữ mật khẩu cũ, không chạy khi Maven package |
| Job/outbox PostgreSQL | Code dự án, lease/ACK/NACK/retry/idempotency | Enqueue cùng transaction; nhiều worker/node chia việc qua SKIP LOCKED; handler cần chịu việc chạy lại |
| Executor JVM | Số thread/lease/poll cấu hình qua JobSettings | Giới hạn worker đang xử lý, không tạo thread mới cho mỗi request; không tăng thread/pool vô hạn |
| Nhật ký API | PostgreSQL, retention 7 ngày, mục tiêu 10.000 dòng | Snapshot được whitelist, query/paging trên DB; không queue/cache lịch sử trong RAM; có counter ghi lỡ khi DB/slot lỗi |

Đọc [database/migration](../../database/README.md), [schema và quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [audit system](../02-phat-trien/AUDIT_VA_SYSTEM.md), [worker/outbox](../02-phat-trien/NEN_TANG_MO_RONG.md) và [journal](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md). Code migration/transaction/queue là thành phần nội bộ; không dùng Flyway/Liquibase hoặc message broker trong luồng hiện tại.

## 5. Giao diện từ server đến trình duyệt

| Thư viện/kỹ thuật | Phiên bản/cách dùng | Dùng ở đâu |
|---|---|---|
| Thymeleaf Spring 6 | 3.1.5.RELEASE | Render HTML/locale/principal trên server, fragment layout/pagination |
| Bootstrap | 5.3.8 qua WebJar | Grid, form, table, pagination; CSS đóng gói cùng WAR |
| Axios | 1.20.0 qua WebJar | HTTP client chung, cookie/locale, lấy CSRF trước mutation, xử lý lỗi API |
| Swagger UI | 5.34.0 qua WebJar | `/docs` để xem/thử API theo OpenAPI tại `/v3/api-docs` |
| JavaScript thuần | Module IIFE, DOM API | Tương tác UI, tải đúng trang dữ liệu, form/modal và export debug |
| Native `<dialog>` + workspace.js | Code dự án | Modal/focus/busy; tab bàn phím/ARIA; không cần Bootstrap JS cho modal hiện tại |
| table.js | Code dự án | Cửa sổ số trang giới hạn, đổi size/chọn trang, icon thao tác có tên, đọc lại trang cuối bị mất |
| forms.js | Code dự án | Validation cạnh field theo vi/en, aria-invalid, reset lỗi và map lỗi server |
| CSS app.css | Code dự án | Màu/nhịp chữ/layout; cột thao tác 96px, icon 32px, toolbar/footer căn phải |
| WebJars | Đóng gói thư viện browser trong Maven/WAR | Tải Bootstrap/Axios/Swagger từ app; UI không phụ thuộc CDN |

Layout/script chung tại [fragments/layout](../../src/main/webapp/WEB-INF/views/fragments/layout.html), [app.js](../../src/main/webapp/assets/js/app.js); module cụ thể dùng catalog.js/operations.js/media.js/api-journal.js. UI hiện không dùng React/Vue/Angular, jQuery hoặc DataTables. Thêm thư viện mới chỉ khi có nhu cầu chưa được component hiện tại đáp ứng; đọc [quy ước UI](../03-giao-dien/QUY_UOC_UI.md) cho tab/modal/bảng/validation.

## 6. Ảnh, email, quota và giám sát

| Thành phần | Phiên bản/cách dùng | Trạng thái trong source |
|---|---|---|
| Cloudinary SDK HTTP5 | 2.5.0 | Adapter ảnh công khai qua backend, kiểm bytes/pixel/owner và cleanup bền vững; mặc định chưa bật |
| Gmail API + OAuth2 | JDK HTTP client qua HTTPS | Transport mail cho Gmail/Workspace, token từ Environment; đi qua outbox/worker, mặc định chưa bật |
| Angus Jakarta Mail | 2.0.5 | Tạo mail/MIME và SMTP TLS tùy chọn qua cấu hình provider |
| Quota/admission guard | Bộ nhớ từng node, giới hạn in-flight/body | Chống spam/quá tải; nhiều node không có quota tập trung khi adapter Redis tắt |
| Lettuce Redis client | 7.9.0.RELEASE | Dependency/adapter tương thích cũ được giữ; Redis không bật và không phải điều kiện chạy giai đoạn này |
| Micrometer + Prometheus registry | BOM 1.17.2 | JVM/HTTP/pool/worker metrics; nhãn giới hạn, scrape token riêng và không truy vấn DB/JWT |
| UI giám sát/configuration | Code dự án | Đọc RAM/CPU/lưu lượng/pool/worker và cấu hình whitelist; DB probe opt-in |
| Prometheus/Alertmanager/Grafana | Mẫu cấu hình trong repo | Collector/dashboard/cảnh báo bên ngoài chưa được coi đã triển khai chỉ vì có file mẫu |

Kafka hiện chưa dùng. Cloudinary, Gmail, cảnh báo ngoài app và backup/restore cần cấu hình/nghiệm thu môi trường riêng; có adapter không có nghĩa provider đã kết nối production. Đọc [Cloudinary](CHAY_LOCAL_VA_CLOUDINARY.md), [mail/worker](../02-phat-trien/NEN_TANG_MO_RONG.md) và [giám sát/vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md).

## 7. Build, kiểm thử và triển khai

| Công cụ | Phiên bản/phạm vi | Mục đích |
|---|---|---|
| Maven Wrapper | Maven 3.9.11, wrapper 3.3.4 | Build thống nhất; profile local cho IDE, production bỏ test compile/run và launcher |
| Maven compiler / WAR / Surefire | 3.14.0 / 3.4.0 / 3.5.3 | Compile Java, đóng gói WAR, chạy test phát triển |
| JUnit Jupiter | 5.12.2, test scope | Unit/regression test, không đóng gói production |
| Spring Test / Security Test | Theo BOM tương ứng, test scope | MockMvc, kiểm auth/quyền/context/locale/template mà không cần Neon |
| Node.js test runner | Development/CI; workflow dùng Node 22 | Kiểm helper browser/export; tách khỏi Docker production |
| Prettier + Java/XML plugins | 3.9.10 + 2.11.0 / 3.4.2 | Format Java/HTML/CSS/JS/XML; lockfile chung, không format SQL migration đã áp dụng |
| IntelliJ Project scheme + EditorConfig | File cấu hình trong repo | Soạn code cùng chuẩn, nút Run Format/Format Check; cấu hình env cá nhân không commit |
| Docker multi-stage | Temurin 21 JDK → Tomcat 10.1 JRE21 | Cache Maven/dependency layer, chỉ copy src/main, chạy non-root; image tag theo Dockerfile |
| GitHub Actions | Workflow trong `.github/workflows` | Container/PostgreSQL tạm, format/helper test và liên kết docs; branch/PR tiếng Việt |
| Render Blueprint | Docker, branch main, checksPass | Cấu hình build/deploy và env; triển khai thực tế phụ thuộc service được chủ dự án kết nối |

Đọc [format IntelliJ](FORMAT_INTELLIJ.md), [quy trình Git/PR](../../CONTRIBUTING.md) và [deploy Render](../04-van-hanh/DEPLOY_RENDER.md). Không đưa Node/test/credential vào image để tiện chạy. Không seed/test/cleanup production để kiểm một PR.

## 8. Các câu dễ được hỏi

| Câu hỏi | Cách trả lời theo source hiện có |
|---|---|
| Frontend và backend giao tiếp thế nào? | HTML ban đầu render bằng Thymeleaf; Axios gọi API JSON, envelope/error/locale/traceId chung. |
| Vì sao có Hibernate trong pom nhưng lại nói JDBC? | Hibernate Validator chỉ thực thi Bean Validation; repository SQL dùng JDBC, không có Hibernate ORM/JPA. |
| Bootstrap tự tạo gì? | Migration/table/index, role/quyền, admin nếu chưa có, danh mục mẫu và actor system; giữ mật khẩu admin cũ. |
| Token/role lưu ở đâu? | Cookie HttpOnly cho web, JWT cho API client; account/role/permission/tokenVersion trong PostgreSQL; không giữ token trong localStorage. |
| Worker nhiều node có nhận trùng không? | Claim có khóa/lease; mô hình at least once vẫn cần handler/idempotency chống tác động lặp khi ACK lỗi. |
| App restart thì traceId còn không? | Bản ghi journal đã commit còn trong DB đến khi hết retention/cleanup; lúc DB/slot lỗi hoặc process bị kill trước commit có thể không ghi được. |
| Chạy nhiều node đã bảo đảm chịu tải chưa? | Có nền migration lock, DB queue và JWT dùng chung; quota memory từng node, tổng pool phải tính mọi node, cần đo tải staging để nghiệm thu. |
| Ảnh và mail đang chạy thật chưa? | Source có adapter; xem cờ Environment/health và bằng chứng provider ở môi trường thực, không suy ra từ dependency. |
| Vì sao export chưa có token thật? | Chủ dự án chọn URL thực + `{{accessToken}}`; token của tài khoản debug điền riêng trong Postman, journal không ghi JWT. |

Khi được hỏi một tính năng “đã có chưa”, dẫn [tiến độ và PR bằng chứng](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md) cùng màn/code liên quan. Không trình bày kế hoạch như tính năng đã hoàn thành; phiên bản dependency, khả năng chịu tải và trạng thái deploy là ba thông tin cần kiểm chứng riêng.


FOUNDATION02 không thêm framework/provider mới. Registry auth/policy và online/bucket tiếp tục JDBC/PostgreSQL; Role/Permission dùng value object JSON chuỗi để nhận mã mở rộng, hằng lõi dùng bootstrap. Media core chung và config theo trách nhiệm dùng các thư viện hiện có. Chi tiết cơ chế/giới hạn xem [nền tảng mở rộng](../02-phat-trien/NEN_TANG_MO_RONG.md) và [vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md).
