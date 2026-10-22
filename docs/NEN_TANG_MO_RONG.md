# Nền tảng chống spam, phân quyền, worker và mail

Cập nhật 22/10/2026, phụ trách hieuv526034. Đọc cùng [luồng base](LUONG_XU_LY_BASE.md) và [API_BASE.md](API_BASE.md). Các phần dưới đây là code đã triển khai, còn kết quả chạy Docker xem PR/Actions tương ứng. Không chạy thử trên Neon production hoặc gửi thư thật trong CI.

## Kiến trúc và giới hạn chịu tải

Giữ Java 17+ (Docker Java 21), **Spring MVC 6/Tomcat 10.1** theo quy ước chủ dự án vừa xác nhận; không có Boot/ORM. Không dùng bản milestone/snapshot để có nhãn “mới nhất”. Spring 6.2.19/Security 6.5.11 vẫn có rủi ro vòng đời đã ghi tại [RA_SOAT_BASE.md](RA_SOAT_BASE.md); yêu cầu stack hiện tại chưa cho phép kết luận đã dùng dòng framework OSS còn hỗ trợ. Bổ sung Lettuce 7.9.0.RELEASE và Angus Mail 2.0.5 bản stable từ Maven Central.

Luồng HTTP: connector giới hạn tài nguyên → TrafficGuardFilter → CSRF/JWT → controller/base → service kiểm quyền chức năng → repository/transaction → response. Luồng việc nền: transaction lưu system_jobs → commit → poller nhận lease → handler chạy ngoài transaction nhận việc → ACK hoặc retry/dead.

Các giới hạn mặc định cho RAM 512 MB:

| Tài nguyên | Giới hạn | Mục đích |
|---|---|---|
| Tomcat | 64 thread, 256 connection, backlog 64 | Tránh tạo vô hạn thread/connection |
| Connector | header 16 KiB, 100 parameter, timeout 10s, keepalive 5s | Giới hạn đầu vào trước tầng ứng dụng |
| Tầng ứng dụng | 16 request đang xử lý, 2 login đang xử lý | Bảo vệ DB và chi phí BCrypt |
| Body JSON | 64 KiB kể cả khi không có Content-Length | Không để request chunked đọc vô hạn |
| HikariCP | 4 connection/instance | Chốt mức cạnh tranh vào DB |
| Worker | 2 thread, hàng chờ RAM bằng 0 | Việc chờ nằm trong DB, không chất vào heap |
| Quota | 300 request/phút/IP, 20 login/phút/IP | Giảm spam trước JWT/SQL; login sai còn khóa theo tài khoản |

Đây là điểm bắt đầu có kiểm soát, **không phải cam kết số người online/RPS**. CI có baseline 48 request/8 luồng trên PostgreSQL container; throughput/p95 phụ thuộc máy CI, không đại diện Render/Neon hoặc nghiệp vụ booking chưa có. Khi tăng tải phải đo p95/p99, RAM, số thread chờ pool, queue lag, query plan/index; cân đối pool × số instance với giới hạn DB. Tăng thread/pool tùy ý thường làm DB chậm hơn.

Render Free giới hạn một instance, có thể ngủ sau 15 phút không có traffic, không có worker luôn chạy được bảo đảm và chặn outbound SMTP 25/465/587. Muốn xử lý job đúng hạn liên tục hoặc tải cao thực tế cần compute luôn chạy/scale và DB/Redis phù hợp. Xem [Render Free](https://render.com/docs/free). Source đã chuẩn bị chia sẻ trạng thái cho nhiều instance, cấu hình miễn phí không biến thành hạ tầng chịu tải cao.

## Chống spam và quá tải

`TrafficGuardFilter` chạy trước CSRF/JWT. Nó lấy IP nguồn đáng tin, băm IP làm key, kiểm quota toàn request và quota login, lấy semaphore rồi mới đọc body và cho đi tiếp. Vượt quota trả COM012/429 + Retry-After; hết slot hoặc Redis lỗi trả COM013/503 + Retry-After; body lớn trả COM014/413. Lỗi vẫn có locale/traceId và không log URL Redis/credentials.

MemoryRateLimitStore dùng một khóa đồng bộ để check/increment nguyên tử, tối đa 10000 key. Khi đầy chỉ dọn key hết hạn; không evict key đang có quota vì sẽ mở đường né giới hạn. Đây là bộ đếm **một instance**, reset khi restart. Đây là fixed window tính từ request đầu tiên, có thể có burst gần ranh giới cửa sổ; semaphore và connector vẫn chặn tài nguyên đồng thời.

Đặt `VIETROUTE_REDIS_URL=rediss://...` để dùng Redis chung. Lua thực hiện kiểm/increment/TTL nguyên tử giữa các instance; Redis không sẵn sàng thì đóng cổng request được bảo vệ (fail closed), không âm thầm chuyển sang quota local. Dùng mạng riêng/TLS, Redis có auth và giới hạn memory phù hợp. Quota là dữ liệu tạm; worker/outbox không phụ thuộc Redis và vẫn nằm PostgreSQL.

`X-Forwarded-For` chỉ được đọc nếu `request.remoteAddr` thuộc `VIETROUTE_TRUSTED_PROXY_CIDRS`. Duyệt chuỗi từ phải sang trái, dừng ở hop không tin cậy; header client tự gửi trực tiếp không làm thay IP nguồn. Không dùng `0.0.0.0/0` hoặc toàn Internet làm trusted proxy. Render/reverse proxy cần cấu hình CIDR peer thực tế của môi trường; khi chưa cấu hình, các client đi chung proxy có thể chia sẻ cùng quota. Đừng tăng quota để che việc nhận diện sai IP.

`/health`, asset và WebJars không đi qua quota ứng dụng; nên dùng CDN/WAF/rate limit ở ingress để chặn DDoS trước khi tới Java. Bảo vệ ứng dụng hiện tại không thay WAF/chống DDoS của hạ tầng. Không log raw IP trong key quota hoặc body chứa password/token. API gửi mail còn giới hạn 10 yêu cầu/phút/user, dùng cùng adapter quota.

## Phân quyền chức năng và dữ liệu

Role gom các quyền `Permission`: CATALOG_WRITE, ACCESS_READ, ACCESS_WRITE, JOB_READ, JOB_RETRY, MAIL_SEND, SYSTEM_READ. Bảng auth_permissions là registry; auth_role_permissions gán quyền cho role. ADMIN có quyền cứu hộ cố định; các role khác ban đầu không có các quyền quản trị này. Không tự cấp quyền quản lý dữ liệu chỉ vì tên role OPERATOR.

JWT filter kiểm chữ ký/thời hạn rồi đọc active/tokenVersion/roles/permissions từ DB mỗi request. Authorities gồm ROLE_... và mã Permission. Không cache quyền và không tin claim role/quyền từ client. Thay mapping role có hiệu lực từ request tiếp theo, kể cả JWT cũ; request đang chạy có thể đã qua kiểm quyền. Service dùng `@PreAuthorize("hasAuthority('CATALOG_WRITE')")`, nên gọi qua bean mà thiếu quyền vẫn bị chặn, không chỉ chặn URL.

| API | Quyền | Quy tắc |
|---|---|---|
| GET /api/v1/admin/access/permissions | ACCESS_READ | Registry quyền có tên vi/en |
| GET /api/v1/admin/access/roles | ACCESS_READ | Tập quyền, version và audit của từng role |
| PUT /api/v1/admin/access/roles/{role}/permissions | ACCESS_WRITE | Body permissions + version; ADMIN không sửa được |
| PUT /api/v1/admin/access/users/{id}/roles | ACCESS_WRITE | Body roles + version; không tự sửa role, không bỏ admin hoạt động cuối cùng |
| POST /api/v1/admin/catalogs | CATALOG_WRITE | Quyền chức năng thay điều kiện role ADMIN cứng |

API `/api/v1/admin/**` yêu cầu xác thực ở URL và quyền cụ thể ở service; không đồng nghĩa mọi API dưới đó chỉ chấp nhận role ADMIN. Trang `/admin` tổng quản trị vẫn dành cho ADMIN; giao diện theo quyền chi tiết của module sẽ triển khai cùng module. `/auth/me` có field permissions để UI quyết định hiển thị, còn backend luôn kiểm lại quyền.

Sửa mapping lấy khóa role qua UPDATE có version, rồi xóa/chèn mapping cùng transaction. Version cũ trả COM008/409. Gán role cho user dùng advisory transaction lock chung, khóa user, kiểm admin cuối, cập nhật version/audit/tokenVersion và mapping cùng transaction; token cũ của user đích bị thu hồi. Tự sửa role bị chặn để tránh tự làm mất đường quản trị. Seed giữ nguyên quyền role thường đã chỉnh sau restart; ADMIN cố định được bổ sung quyền mới của hệ thống.

Quyền chức năng **không thay quyền trên dữ liệu**. Khi có nhà xe/chuyến/booking, service phải tra quan hệ nhà xe/owner từ DB theo principal, đưa scope vào WHERE, kiểm quan hệ cha/con và tenant ở cả đọc/ghi. Chưa có bảng membership/module nghiệp vụ thì không dựng companyId giả hoặc tin companyId HTTP. Không dùng permission để bỏ qua kiểm quyền đối tượng. Thêm Permission mới phải có bundle vi/en, migration khi cần đổi cấu trúc và kiểm thử deny/allow ở service cùng kiểm tra scope của module.

## Worker, lịch và transaction outbox

`system_jobs` lưu UUID, loại handler, dedup_key duy nhất, payload JSONB, QUEUED/RUNNING/SUCCEEDED/DEAD, số lần chạy, available_at, lease_token/locked_until, mã lỗi sạch, audit và version. Entity metadata khai báo table/PK/index; DDL ở V003, không sửa V001/V002.

API/mail hoặc service nghiệp vụ gọi enqueue trong transaction. Cùng transaction có thể vừa đổi booking vừa thêm job; rollback thì cả hai mất. Worker chưa thấy bản ghi chưa commit. Không gửi mạng hoặc mở transaction lồng trong callback nghiệp vụ. Đọc ví dụ `MailService.enqueue(Connection, MailPayload, dedup, actor)`; hàm này là điểm tích hợp nội bộ, caller nghiệp vụ chịu trách nhiệm kiểm quyền người dùng.

`VIETROUTE_WORKER_ENABLED=true` bật SmartLifecycle sau initializer. Poller một thread kiểm mỗi 10s; mỗi lượt dispatch tối đa số worker, chỉ khi còn slot. Executor cố định 2 thread, không có queue memory. Mỗi worker lấy một job qua `FOR UPDATE SKIP LOCKED`, đổi trạng thái/lease/attempts rồi commit. Handler chạy ngoài transaction nhận việc; ACK/NACK dùng transaction khác và chỉ được ghi nếu lease token còn đúng, chưa hết hạn. Worker cũ không ghi đè kết quả worker mới.

Lease mặc định 180s. Job RUNNING hết lease được instance khác nhận; hết số lần chạy thì DEAD. Handler mail có timeout mạng; handler tự bổ sung phải có timeout tổng nhỏ hơn lease, hỗ trợ interrupt và xử lý lặp an toàn. Không tạo callback chạy vô hạn hoặc dùng @Async để tự tạo pool khác. Chưa có heartbeat gia hạn lease cho tác vụ kéo dài; tác vụ dài phải chia nhỏ hoặc thiết kế gia hạn/cancel trước khi đăng ký handler.

Failure retry tối đa 5 lần, backoff mũ + jitter; lỗi cấu hình vĩnh viễn có thể DEAD ngay. Chỉ lưu code whitelist, không lưu exception message chứa email/token/response provider. Job thành công được xóa payload, giữ receipt; dọn receipt SUCCEEDED quá 14 ngày mỗi giờ, tối đa 500 dòng/lượt dưới advisory lock. Job DEAD giữ payload để điều tra/chạy lại: người vận hành phải xử lý/xóa theo chính sách lưu dữ liệu của dự án, không giữ PII vô thời hạn.

Lịch hiện tại là polling và bảo trì, dùng khoảng chờ cố định để không chồng một poller với chính nó. Nhiều instance có poller riêng và chia việc qua PostgreSQL; dọn dẹp có khóa chung. Không tuyên bố đã có cron nghiệp vụ bất kỳ. Khi thêm lịch nhắc chuyến, tạo job với available_at và dedup theo định danh lịch; không đặt nhiều @Scheduled gửi mail trực tiếp trên mỗi instance. Shutdown dừng nhận việc rồi chờ handler tối đa 45s; nếu tiến trình bị kill, lease cho phép tiếp tục lần sau.

Hệ thống có ngữ nghĩa **at least once**. Nếu Gmail đã nhận thư nhưng app chết trước ACK, lần retry có thể gửi lặp. Message-ID và dedup_key ổn định hỗ trợ đối chiếu/chống enqueue lặp, không bảo đảm Gmail dedup. Sau khi receipt được dọn theo retention, dedup key đó cũng hết hiệu lực. Nghiệp vụ quan trọng phải lưu idempotency receipt theo thời hạn phù hợp và dùng provider idempotency khi có; không gọi đây là exactly once.

| API | Quyền | Hành vi |
|---|---|---|
| GET /api/v1/admin/jobs?page=0&size=20 | JOB_READ | Phân trang, không trả payload/dedup/email |
| GET /api/v1/admin/jobs/{uuid} | JOB_READ | Trạng thái, audit, mã lỗi đã làm sạch |
| POST /api/v1/admin/jobs/{uuid}/retry | JOB_RETRY | Body version, chỉ DEAD; giữ UUID/dedup, reset attempts |
| GET /api/v1/admin/system/status | SYSTEM_READ | Kiểm DB thật, queue counts và pool/worker của instance |

SYSTEM_CLEANUP là handler bảo trì có thật để làm mẫu handler idempotent; không có handler gửi mail giả để che kết quả test. `/health` tiếp tục chỉ kiểm web, không dùng endpoint system/status làm health check Render Free.

## Gmail/Workspace và SMTP

Provider mặc định `gmail`: OAuth2 refresh access token qua HTTPS rồi gọi Gmail messages.send. MIME dựng bằng Jakarta Mail, UTF-8, một recipient, plain text; không nối header tự do, không nhận sender/credential từ client. Access token chỉ cache RAM, refresh được đồng bộ giữa các worker; client secret/refresh token ở Environment. Provider không tự retry gửi; worker quyết định retry để không nhân số lần gửi. Gmail 401 xóa token cache; 429/5xx và 403 có reason quota được retry; OAuth 400/config lỗi đưa DEAD để sửa cấu hình. Xem [Gmail errors](https://developers.google.com/workspace/gmail/api/guides/handle-errors).

Các bước cấu hình Gmail một lần:

1. Tạo Google Cloud project, bật Gmail API, cấu hình OAuth consent (Workspace có thể dùng Internal nếu tổ chức cho phép). Chọn mailbox gửi của dự án và scope tối thiểu `https://www.googleapis.com/auth/gmail.send`.
2. Tạo OAuth client. Lấy refresh token bằng luồng OAuth có `access_type=offline`, xác thực chính tài khoản gửi và redirect URI khớp. Có thể dùng OAuth Playground của Google với **Use your own OAuth credentials** và client của chính dự án; giữ token ở máy/Environment, không dán lên issue/chat/source. Đọc [OAuth web server flow](https://developers.google.com/identity/protocols/oauth2/web-server). Chế độ External/Testing có giới hạn token/consent cần kiểm tra trước vận hành; Workspace có thể cần admin chấp thuận scope.
3. Trên Render/IDE, đặt VIETROUTE_GMAIL_CLIENT_ID, VIETROUTE_GMAIL_CLIENT_SECRET, VIETROUTE_GMAIL_REFRESH_TOKEN và VIETROUTE_MAIL_FROM bằng mailbox hoặc send-as alias đã được Gmail cho phép.
4. Đặt VIETROUTE_MAIL_ENABLED=true, VIETROUTE_MAIL_PROVIDER=gmail, VIETROUTE_WORKER_ENABLED=true. Restart rồi xem system/status và trạng thái job. Thiếu credentials khi mail bật sẽ chặn startup; mail tắt không yêu cầu Google credentials.

`POST /api/v1/admin/mail` yêu cầu MAIL_SEND và header `Idempotency-Key` 8–64 ký tự chữ/số/_/-. Body gồm to, subject, text. HTTP 202/OK005 nghĩa là đã lưu việc, không có nghĩa mail đã được giao. Response chỉ có JobResponse; xem job SUCCEEDED/DEAD. Key được gắn với actor để hai user không dùng trùng receipt của nhau. Không có API gửi mail công khai, không gửi reset password/OTP chưa có chính sách nghiệp vụ. Mail tắt trả MAIL001/503.

Provider `smtp` là tùy chọn cho local/hạ tầng cho SMTP, STARTTLS bắt buộc, TLS 1.2/1.3, kiểm hostname và timeout mạng. Gmail dùng smtp.gmail.com:587, tài khoản và App Password khi chính sách Google cho phép; không dùng mật khẩu đăng nhập thường. **Không dùng SMTP này trên Render Free**. Không ghi password/token vào Properties debug hoặc log. Khi cần số lượng email lớn, Gmail/Workspace còn có quota; thay adapter MailTransport sang dịch vụ email phù hợp sau khi chọn nhà cung cấp, không tăng thread để vượt quota.

## Cấu hình và cách học/mở rộng

Tất cả biến có mẫu trong `.env.example`. Java không tự đọc .env. Render Blueprint để mail/worker tắt ban đầu; thêm OAuth secrets rồi bật trong Environment. Worker có thể chạy ở cùng web cho đồ án hoặc cùng WAR/image ở service luôn chạy riêng; web không cần sticky session, các instance dùng cùng PostgreSQL/Redis/JWT secret và ingress đáng tin. Không tự cấp tài nguyên Render trả phí trong source.

- TrafficSettings: RATE_REQUESTS 1–100000, RATE_LOGINS 1–1000, RATE_MAX_KEYS 100–100000, HTTP_IN_FLIGHT 1–512, LOGIN_IN_FLIGHT 1–16, HTTP_MAX_BODY_BYTES 1024–1048576. Đây là dải kiểm tra cấu hình, không phải giá trị đề nghị tăng tối đa.
- JobSettings: WORKER_THREADS 1–8, POLL_SECONDS 1–300, LEASE_SECONDS 120–900, RETENTION_DAYS 1–90. Khi tắt worker không có polling DB; khi bật, poller sẽ giữ Neon hoạt động hơn và tính chi phí tương ứng.
- MailSettings: bật/tắt, gmail/smtp, sender và credentials; lỗi cấu hình không ghi giá trị secret. SMTP_PORT 1–65535, mặc định 587 STARTTLS.

Học code theo thứ tự TrafficGuardFilter → Memory/RedisRateLimitStore → JwtAuthenticationFilter/UserRepository → CatalogService/AccessService → JobRepository/JobWorker → MailService/MailJobHandler/MailTransport. Comment tiếng Việt giải thích thứ tự khóa, transaction, chỗ commit, ACK và xử lý lỗi; không chỉ diễn giải tên hàm.

Khi thêm module: tạo permission hành động, endpoint @Operation, DTO @Schema/validate, service method security và scope dữ liệu, SQL có schema/metadata/index và migration mới; nếu cần mail, enqueue cùng transaction. Kiểm thử quyền thiếu/đủ, cạnh tranh version, rollback/outbox và dữ liệu ngoài phạm vi trước PR. Không gửi email trực tiếp trong controller, không tin audit/role/companyId từ HTTP và không dùng Thread mới cho từng request.

Kiểm chứng tự động: bộ test Java không dùng DB thật; Gmail client giả không gửi mail; CI PostgreSQL tạm kiểm V001→V003/fresh/legacy, quyền có hiệu lực và thu hồi với JWT cũ, optimistic update, hai worker chia việc, lease hết hạn, DEAD/retry, rollback enqueue, pool/baseline. Redis container tạm kiểm quota hai instance, giả XFF và fail closed khi mất Redis. Production WAR/image không chứa test/fixtures/Node. Sau khi cấu hình mailbox thực, cần nghiệm thu riêng một email được chủ tài khoản cho phép; CI không chứng minh tài khoản Google/Render đã cấu hình thành công.
