# Nền tảng chống spam, phân quyền, worker và mail

## Phạm vi hiện hành

Auth dùng40 class/tám bảng theo AUTHDB04/AUTHDTO02. [Schema/phân quyền](SCHEMA_VA_PHAN_QUYEN.md) là nguồn chính; không còn permission/action registry, policy bổ sung, auth mail outbox hoặc recovery riêng. Tài liệu này chỉ giữ quota, worker/system_jobs và provider mail chung. Trạng thái chạy/provider/CI ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

Launcher local đọc `.env` gốc theo OS/IDE precedence; chủ dự án cho phép theo dõi file trong repo. WAR/Render nhận env, không đọc file local; không in giá trị vào docs/log/image. Media dùng common.media, quan sát/journal dùng common.monitoring; không chuyển chức năng auth đã cắt sang các module này để che số class.

## Kiến trúc và giới hạn chịu tải

Giai đoạn hiện tại chưa dùng Kafka/Redis: quota mặc định memory theo từng node. Redis adapter ở phần dưới chỉ là khả năng tương thích tùy chọn, không phải dịch vụ đã bật. Đọc [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md) và [giám sát/vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md) cho cấu trúc job/mail mới và metrics/dashboard/cảnh báo.

Giữ Java 17+ (Docker Java 21), **Spring MVC 6/Tomcat 10.1** theo quy ước chủ dự án vừa xác nhận; không Spring Boot; auth CRUD dùng Spring Data JPA/Hibernate, JDBC phục vụ batch/query phức tạp và các module nghiệp vụ. Không dùng bản milestone/snapshot để có nhãn “mới nhất”. Spring 6.2.19/Security 6.5.11 vẫn có rủi ro vòng đời đã ghi tại [RA_SOAT_BASE.md](../99-luu-tru/RA_SOAT_BASE.md); yêu cầu stack hiện tại chưa cho phép kết luận đã dùng dòng framework OSS còn hỗ trợ. Bổ sung Lettuce 7.9.0.RELEASE và Angus Mail 2.0.5 bản stable từ Maven Central.

Luồng HTTP: connector giới hạn tài nguyên → TrafficGuardFilter → CSRF/JWT → EndpointAccess → controller/base → service kiểm principal/nghiệp vụ → repository/transaction → response. Luồng việc nền: transaction lưu system_jobs → commit → poller nhận lease → handler chạy ngoài transaction nhận việc → ACK hoặc retry/dead.

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

EndpointAccess thu thập method/template và kiểm DB trước handler. User → Role → Function → Menu/API là mapping hiện hành; endpoint chưa gán mặc định deny, source constants/annotation Operation không cấp quyền. Service kiểm context/actor/nghiệp vụ; repo lọc scope/owner trên cùng connection, all_scopes phải thuộc chính role cấp endpoint. Không giữ rule AccessFunction/PreAuthorize(action)/permission enum cũ. [Luồng và ranh giới](SCHEMA_VA_PHAN_QUYEN.md#luồng-xác-thực-và-kiểm-endpoint), [scope](SCHEMA_VA_PHAN_QUYEN.md#phạm-vi-dữ-liệu-và-ownership) và [UI cấp quyền](../04-van-hanh/QUAN_TRI_HE_THONG.md#cấp-quyền-cho-một-nhân-viên) mô tả cách mở rộng.

## Worker, lịch và transaction outbox

`system_jobs` lưu UUID, loại handler, dedup_key duy nhất, payload JSONB, QUEUED/RUNNING/SUCCEEDED/DEAD, số lần chạy, available_at, lease_token/locked_until, mã lỗi sạch, audit và version. Entity metadata khai báo table/PK/index; DDL ở V003, không sửa V001/V002.

API/mail hoặc service nghiệp vụ gọi enqueue trong transaction. Cùng transaction có thể vừa đổi booking vừa thêm job; rollback thì cả hai mất. Worker chưa thấy bản ghi chưa commit. Không gửi mạng hoặc mở transaction lồng trong callback nghiệp vụ. Đọc ví dụ `MailService.enqueue(Connection, MailPayload, dedup, actor)`; hàm này là điểm tích hợp nội bộ, caller nghiệp vụ chịu trách nhiệm kiểm quyền người dùng.

`VIETROUTE_WORKER_ENABLED=true` bật SmartLifecycle sau initializer. Poller một thread kiểm mỗi 10s; mỗi lượt dispatch tối đa số worker, chỉ khi còn slot. Executor cố định 2 thread, không có queue memory. Mỗi slot xử lý tối đa 32 job nối tiếp rồi nhường lượt; backlog có sẵn không phải chờ poll delay sau từng email. Chỉ nhận lease cho job sắp chạy, không nhận trước toàn bộ batch. Mỗi worker lấy một job qua `FOR UPDATE SKIP LOCKED`, đổi trạng thái/lease/attempts rồi commit. Handler chạy ngoài transaction nhận việc; ACK/NACK dùng transaction khác và chỉ được ghi nếu lease token còn đúng, chưa hết hạn. Worker cũ không ghi đè kết quả worker mới.

Lease mặc định 180s. Job RUNNING hết lease được instance khác nhận; hết số lần chạy thì DEAD. Handler mail có timeout mạng; handler tự bổ sung phải có timeout tổng nhỏ hơn lease, hỗ trợ interrupt và xử lý lặp an toàn. Không tạo callback chạy vô hạn hoặc dùng @Async để tự tạo pool khác. Chưa có heartbeat gia hạn lease cho tác vụ kéo dài; tác vụ dài phải chia nhỏ hoặc thiết kế gia hạn/cancel trước khi đăng ký handler.

Failure retry tối đa 5 lần, backoff mũ + jitter; lỗi cấu hình vĩnh viễn có thể DEAD ngay. Chỉ lưu code whitelist, không lưu exception message chứa email/token/response provider. Job thành công được xóa payload, giữ receipt; dọn receipt SUCCEEDED quá 14 ngày mỗi giờ, tối đa 500 dòng/lượt dưới advisory lock. Job DEAD giữ payload để điều tra/chạy lại: người vận hành phải xử lý/xóa theo chính sách lưu dữ liệu của dự án, không giữ PII vô thời hạn.

Lịch hiện tại là polling và bảo trì, dùng khoảng chờ cố định để không chồng một poller với chính nó. Nhiều instance có poller riêng và chia việc qua PostgreSQL; dọn dẹp có khóa chung. Không tuyên bố đã có cron nghiệp vụ bất kỳ. Khi thêm lịch nhắc chuyến, tạo job với available_at và dedup theo định danh lịch; không đặt nhiều @Scheduled gửi mail trực tiếp trên mỗi instance. Shutdown dừng nhận việc rồi chờ handler tối đa 45s; nếu tiến trình bị kill, lease cho phép tiếp tục lần sau.

Hệ thống có ngữ nghĩa **at least once**. Nếu Gmail đã nhận thư nhưng app chết trước ACK, lần retry có thể gửi lặp. Message-ID và dedup_key ổn định hỗ trợ đối chiếu/chống enqueue lặp, không bảo đảm Gmail dedup. Sau khi receipt được dọn theo retention, dedup key đó cũng hết hiệu lực. Nghiệp vụ quan trọng phải lưu idempotency receipt theo thời hạn phù hợp và dùng provider idempotency khi có; không gọi đây là exactly once.

| API | Quyền | Hành vi |
|---|---|---|
| GET /api/v1/admin/jobs?page=0&size=20 | Grant GET đúng template | Phân trang, không trả payload/dedup/email |
| GET /api/v1/admin/jobs/{uuid} | Grant GET đúng template | Trạng thái, audit, mã lỗi đã làm sạch |
| POST /api/v1/admin/jobs/{uuid}/retry | Grant POST đúng template | Body version, chỉ DEAD; giữ UUID/dedup, reset attempts |
| GET /api/v1/admin/system/status | Grant GET đúng template | Kiểm DB thật, queue counts và pool/worker của instance |

SYSTEM_CLEANUP là handler bảo trì có thật để làm mẫu handler idempotent; không có handler gửi mail giả để che kết quả test. `/health` tiếp tục chỉ kiểm web, không dùng endpoint system/status làm health check Render Free.

## Gmail/Workspace và SMTP

Provider mặc định `gmail`: OAuth2 refresh access token qua HTTPS rồi gọi Gmail messages.send. MIME dựng bằng Jakarta Mail, UTF-8, một recipient; mail quản trị hiện dùng bản chữ thuần; không còn composer/template email tài khoản. Không nối header tự do, không nhận sender/credential từ client. Access token chỉ cache RAM, refresh được đồng bộ giữa các worker; client secret/refresh token ở Environment. Provider không tự retry gửi; worker quyết định retry để không nhân số lần gửi. Gmail 401 xóa token cache; 429/5xx và 403 có reason quota được retry; OAuth 400/config lỗi đưa DEAD để sửa cấu hình. Xem [Gmail errors](https://developers.google.com/workspace/gmail/api/guides/handle-errors).

Các bước cấu hình Gmail một lần:

1. Tạo Google Cloud project, bật Gmail API, cấu hình OAuth consent (Workspace có thể dùng Internal nếu tổ chức cho phép). Chọn mailbox gửi của dự án và scope tối thiểu `https://www.googleapis.com/auth/gmail.send`.
2. Tạo OAuth client kiểu Web application; nếu lấy token qua [OAuth Playground](https://developers.google.com/oauthplayground/), thêm redirect URI `https://developers.google.com/oauthplayground`. Trong cấu hình Playground chọn **Use your own OAuth credentials**, nhập client của chính dự án, **Access type: Offline**; authorize duy nhất scope gmail.send bằng đúng mailbox gửi, rồi **Exchange authorization code for tokens**. Giữ client secret/refresh token ở máy/Environment, không dán lên issue/chat/source. Xem [Gmail server authorization](https://developers.google.com/workspace/gmail/api/auth/web-server). Không dùng client mặc định Playground: token có thể bị thu hồi sau 24 giờ.
3. Trên Render/IDE, đặt VIETROUTE_GMAIL_CLIENT_ID, VIETROUTE_GMAIL_CLIENT_SECRET, VIETROUTE_GMAIL_REFRESH_TOKEN; đặt VIETROUTE_MAIL_FROM bằng mailbox hoặc send-as alias đã được Gmail cho phép. Sender chủ dự án yêu cầu ngày 24/10/2026 là `hieuv526034@gmail.com`, cấu hình trong Environment, không hardcode ở adapter hoặc template. Mật khẩu đăng nhập Google không thay được bộ OAuth này. Chế độ External/Testing cấp refresh token cho scope Gmail hết hạn sau 7 ngày; chỉ bật vận hành sau khi kiểm chính sách publishing/verification của Google phù hợp. Đổi mật khẩu Google hoặc thu hồi quyền cũng có thể vô hiệu token; xem [vòng đời refresh token](https://developers.google.com/identity/protocols/oauth2#expiration). Workspace có thể cần admin chấp thuận scope.
4. Đặt VIETROUTE_MAIL_ENABLED=true, VIETROUTE_MAIL_PROVIDER=gmail, VIETROUTE_WORKER_ENABLED=true; cờ này không mở luồng email auth đã bỏ. Restart rồi xem system/status và trạng thái job. Thiếu credentials khi mail bật sẽ chặn startup; mail tắt không yêu cầu Google credentials. Local launcher đọc `.env`, nhưng biến OS/IDE trong `.run` ưu tiên hơn: cần bật/tắt đồng bộ, không đưa secret vào file `.run` chia sẻ.
5. Nghiệm thu bằng một mail thử tới địa chỉ đã được chủ hộp thư cho phép, đối chiếu job SUCCEEDED và inbox (cả Spam), xác nhận From, UTF-8 và nội dung mail nghiệp vụ trên môi trường thử riêng. SUCCEEDED chỉ chứng minh provider chấp nhận; không bảo đảm vào Inbox hoặc đến đúng hạn khi Render ngủ. Không chạy migration/seed/register/reset trên Neon chỉ để kiểm build. Chưa có bước này thì ghi rõ provider chưa nghiệm thu, dù `/options` báo đã đủ cấu hình.

`POST /api/v1/admin/mail` yêu cầu grant endpoint POST này và header `Idempotency-Key` 8–64 ký tự chữ/số/_/-. Body gồm to, subject, text. HTTP 202/OK005 nghĩa là đã lưu việc, không có nghĩa mail đã được giao. Response chỉ có JobResponse; xem job SUCCEEDED/DEAD. Key được gắn với actor để hai user không dùng trùng receipt của nhau. Không có API gửi mail tùy ý công khai; không có API phát mã tài khoản hoặc auth outbox ở phiên bản hiện hành. Mail tắt trả MAIL001/503.

Provider `smtp` là tùy chọn cho local/hạ tầng cho SMTP, STARTTLS bắt buộc, TLS 1.2/1.3, kiểm hostname và timeout mạng. Gmail dùng smtp.gmail.com:587, tài khoản và App Password khi chính sách Google cho phép; không dùng mật khẩu đăng nhập thường. **Không dùng SMTP này trên Render Free**. Không ghi password/token vào Properties debug hoặc log. Khi cần số lượng email lớn, Gmail/Workspace còn có quota; thay adapter MailTransport sang dịch vụ email phù hợp sau khi chọn nhà cung cấp, không tăng thread để vượt quota.

### SMTP tài khoản và mật khẩu — MAIL03

Ngày 25/10/2026 chủ dự án chọn tạm hoãn OAuth và dùng SMTP. Adapter SmtpTransport đã có, dùng MailMessages chung; chỉ cần chọn provider trong môi trường phù hợp, không viết bộ gửi mail mới hoặc đổi mặc định Render Free. Tên key cấu hình local trong `.env` (không sao chép giá trị credential vào docs):

```dotenv
VIETROUTE_MAIL_PROVIDER=smtp
VIETROUTE_MAIL_FROM=hieuv526034@gmail.com
VIETROUTE_SMTP_HOST=smtp.gmail.com
VIETROUTE_SMTP_PORT=587
VIETROUTE_SMTP_USER=hieuv526034@gmail.com
VIETROUTE_SMTP_PASSWORD=<Gmail_App_Password>
```

Chủ mailbox bật Xác minh 2 bước, mở [Mật khẩu ứng dụng Google](https://myaccount.google.com/apppasswords), tạo mật khẩu cho VietRoute rồi nhập trực tiếp vào SMTP_PASSWORD ở môi trường riêng. Đây là mật khẩu ứng dụng 16 ký tự, không phải mật khẩu đăng nhập Gmail; xem [hướng dẫn Google](https://support.google.com/mail/answer/185833?hl=vi). Không điền giá trị thật vào `.env.example`, `.run`, source, PR hoặc ảnh/log.

Giữ MAIL_ENABLED=false khi chưa xác thực thành công. Khi có credential hợp lệ và hạ tầng cho phép kết nối SMTP, bật MAIL_ENABLED/WORKER_ENABLED; đồng bộ cờ OS/IDE đang ưu tiên hơn `.env`. Auth email/reset đã bỏ, không bật lại bằng cờ môi trường. Nghiệm thu riêng thư thử tới chính mailbox được phép và đối chiếu inbox. Render Free vẫn chặn SMTP 25/465/587 kể cả có App Password; thay cấu hình Java không gỡ được giới hạn mạng này.

Kết quả chẩn đoán và phần còn thiếu ghi tại [tiến độ MAIL03](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md#mail03--chuyển-smtp-local-đang-chờ-mật-khẩu-ứng-dụng). Một lỗi xác thực không được coi là provider hoạt động, không tự retry credential bị từ chối hoặc bật worker để thử tiếp.

### Email tài khoản đã bỏ trong AUTHDB04

Không còn template account-code, mail composer/job handler auth hoặc API đăng ký/xác minh/reset. MailService/system_jobs/provider Gmail/SMTP chung vẫn phục vụ nghiệp vụ được cấp quyền, không tự khôi phục email lifecycle auth. Nếu cần tính năng này sau này phải chốt thiết kế, credential/challenge và test riêng; có provider không có nghĩa flow auth đã tồn tại.

## Cấu hình và cách học/mở rộng

Tất cả biến có mẫu trong `.env.example`. WAR không tự đọc .env; launcher local chủ động đọc theo [hướng dẫn chạy](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md). Render Blueprint để mail/worker tắt ban đầu; thêm OAuth secrets rồi bật trong Environment. Worker có thể chạy ở cùng web cho đồ án hoặc cùng WAR/image ở service luôn chạy riêng; web không cần sticky session, các instance dùng cùng PostgreSQL/JWT secret; quota memory vẫn riêng từng node và ingress đáng tin. Không tự cấp tài nguyên Render trả phí trong source.

- TrafficSettings: RATE_REQUESTS 1–100000, RATE_LOGINS 1–1000, RATE_MAX_KEYS 100–100000, HTTP_IN_FLIGHT 1–512, LOGIN_IN_FLIGHT 1–16, HTTP_MAX_BODY_BYTES 1024–1048576. Đây là dải kiểm tra cấu hình, không phải giá trị đề nghị tăng tối đa.
- JobSettings: WORKER_THREADS 1–8, POLL_SECONDS 1–300, LEASE_SECONDS 120–900, RETENTION_DAYS 1–90. Khi tắt worker không có polling DB; khi bật, poller sẽ giữ Neon hoạt động hơn và tính chi phí tương ứng.
- MailSettings: bật/tắt, gmail/smtp, sender và credentials; lỗi cấu hình không ghi giá trị secret. SMTP_PORT 1–65535, mặc định 587 STARTTLS.

Học code theo thứ tự TrafficGuardFilter → Memory/RedisRateLimitStore → JwtAuthenticationFilter/UserRepo/EndpointAccess → UserService/RoleService/FunctionService/CatalogService → JobRepository/JobWorker → MailService/MailJobHandler/MailTransport. Comment tiếng Việt giải thích thứ tự khóa, transaction, chỗ commit, ACK và xử lý lỗi; không chỉ diễn giải tên hàm.

Khi thêm module: khai báo endpoint @Operation, gán Function/Role theo DB, DTO @Schema/validate, service kiểm principal/nghiệp vụ và SQL scope dữ liệu, SQL có schema/metadata/index và migration mới; nếu cần mail, enqueue cùng transaction. Kiểm thử quyền thiếu/đủ, cạnh tranh version, rollback/outbox và dữ liệu ngoài phạm vi trước PR. Không gửi email trực tiếp trong controller, không tin audit/role/companyId từ HTTP và không dùng Thread mới cho từng request.

Kiểm chứng tự động: unit/H2 không dùng DB bên ngoài; regression dialect/migration dùng PostgreSQL test riêng; Gmail client giả không gửi mail; CI PostgreSQL tạm kiểm V001–V026/fresh/legacy, quyền có hiệu lực và thu hồi với JWT cũ, optimistic update, hai worker chia việc, lease hết hạn, DEAD/retry, rollback enqueue, pool/baseline. Redis container tạm kiểm quota hai instance, giả XFF và fail closed khi mất Redis. Production WAR/image không chứa test/fixtures/Node. Sau khi cấu hình mailbox thực, cần nghiệm thu riêng một email được chủ tài khoản cho phép; CI không chứng minh tài khoản Google/Render đã cấu hình thành công.
