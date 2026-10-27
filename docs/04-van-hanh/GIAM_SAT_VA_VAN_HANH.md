# Giám sát và vận hành VietRoute

Bổ sung UI quản lý job/retry, quyền và snapshot cấu hình không nhạy cảm tại [Quản trị hệ thống](QUAN_TRI_HE_THONG.md). Cấu hình chỉ đọc; thông tin runtime/collector/receiver bên dưới vẫn là các trách nhiệm riêng.

Cập nhật 22/10/2026. Không cần Kafka/Redis. Source cung cấp instrument, trang xem, dashboard và alert mẫu; việc nhận cảnh báo thật cần collector/receiver bên ngoài được cấu hình và thử nghiệm. Không tự tạo tài khoản, mua dịch vụ hoặc gửi thông báo thật trong build.

## Endpoint và quyền

| Đường dẫn | Dùng để làm gì | Phụ thuộc và bảo vệ |
|---|---|---|
| `/health` | Web availability, giữ hợp đồng text | Công khai, không DB; không xác nhận đăng nhập/DB/provider hoạt động |
| `/api/v1/admin/system/status` | Kiểm DB + queue/pool theo yêu cầu | JWT/cookie và SYSTEM_READ; query DB thật |
| `/api/v1/admin/system/runtime` | JVM/HTTP/pool/worker và mẫu DB cache | SYSTEM_READ; method không query DB, nhưng JWT vẫn kiểm tài khoản qua DB |
| `/monitoring` | Trang xem và 30 mẫu gần nhất trong tab | SYSTEM_READ, poll 5s, dừng khi tab ẩn; không thay kho lịch sử |
| `/operations/api-journal` và `/api/v1/admin/system/api-journal` | [Nhật ký API, trace/controller, snapshot đã lọc, cURL/Postman](NHAT_KY_API_VA_DEBUG.md) | ADMIN + SYSTEM_READ; lưu PostgreSQL 7 ngày, mục tiêu 10000 dòng toàn DB; pool riêng 1 connection/node, không cache/queue lịch sử RAM |
| `/internal/metrics` | Prometheus text cho collector | Chain chính xác riêng; Bearer token vận hành 32–256 ASCII; không cookie/JWT/DB |

Khi token env rỗng, scrape trả 404; sai/thiếu token trả 401; token đúng mà khác GET trả 405. Token không cấp quyền API nào khác. Một scrape đồng thời/node; không log token, header hoặc query. Endpoint không nhận token qua URL. Luôn dùng HTTPS ngoài local, token đặt secret file/env của collector. Khi đổi token phải cập nhật cả collector và app, thử scrape rồi thu hồi token cũ. UI không nhìn thấy token vận hành.

Blueprint tạo `VIETROUTE_METRICS_TOKEN` khi tạo mới. Với service đang tồn tại cần thêm key trực tiếp ở Environment; không suy diễn sync đã áp dụng. Các key:

```text
VIETROUTE_METRICS_TOKEN=<random secret, không commit>
VIETROUTE_MONITORING_DB_ENABLED=false
VIETROUTE_MONITORING_POLL_SECONDS=30
```

DB probe tắt mặc định: không giữ Neon thức. Bật true có chủ đích mới poll DB/queue bằng một thread fixed delay; timeout query 3s, thời gian mượn pool/connect vẫn theo DatabaseSettings. Scrape/UI chỉ đọc mẫu, không kích query DB. Lỗi probe xóa mẫu queue cũ khỏi dữ liệu hiện hành và giữ thời điểm thành công cuối; không hiển thị số 0 giả khỏe. NaN/null nghĩa chưa có mẫu hoặc không hỗ trợ, khác số 0. Không bật worker chỉ để xem metrics; không bật mail để gửi cảnh báo app chết.

## Chỉ số có sẵn

| Nhóm | Metrics chính |
|---|---|
| HTTP | `vietroute_http_duration_seconds_{count,sum,bucket}`, `vietroute_http_active`, `vietroute_http_responses_total{route,method,status}`, `vietroute_http_{errors,limited,unavailable}_total` |
| JVM | `jvm_memory_*`, `jvm_gc_*`, `jvm_threads_*`, `process_cpu_usage`, `process_uptime_seconds` |
| Pool | `vietroute_pool_{active,waiting,maximum}`; Hikari `hikaricp_*` bắt đầu khi pool runtime được tạo |
| Worker | `vietroute_worker_{enabled,active,claim_age,completed_total,failed_total}`; claim_age=-1 trước claim thành công đầu |
| DB/queue opt-in | `vietroute_database_{probe_enabled,available,probe_age}`, `vietroute_jobs_{queued,dead,oldest_due}` |

HTTP đo cả bị chặn trước controller. Route dùng tập nhóm hữu hạn auth/catalog/media/admin/other_api/page, method whitelist, không gắn path raw/userId/IP/traceId vào nhãn. Histogram SLO buckets cố định 50ms đến 30s để tính p95/p99 bên collector; không tạo histogram riêng mỗi URL. Static/health/scrape không tính vào lưu lượng nghiệp vụ; API poll dashboard có tính. RPS không phải số người online/khách duy nhất. Mean trên UI tính từ startup, không phải p95.

Heap không phải toàn bộ RAM container: metaspace/code cache, thread stack, direct buffer và native cũng chiếm RAM. Dùng Render Metrics cho RAM/CPU tổng, ghép với JVM/GC để chẩn đoán. CPU không hỗ trợ trả null/NaN. Log HTTP chậm >=1s hoặc 5xx theo dạng event/route/method/status/durationMs/traceId, tối đa một dòng/giây/node tránh log storm; metrics vẫn ghi đủ. Request ID hiện là correlation ID, chưa phải distributed tracing xuyên Gmail/Cloudinary. Log lỗi chưa biết vẫn do ApiExceptionMapper; không bật body/header debug.

## Dashboard và collector

1. Nếu chỉ cần xem nhanh, đăng nhập tài khoản có SYSTEM_READ rồi mở `/monitoring`. Trên nhiều node, trang hiển thị instance ID và reset mẫu khi load balancer chuyển node; không cộng trừ counter khác instance.
2. Render Metrics cung cấp CPU/RAM; bật Notifications cho deploy thất bại/service unhealthy, kiểm tra địa chỉ nhận. Một số biểu đồ HTTP/latency phụ thuộc workspace plan. Không mặc định tài khoản đã bật thông báo.
3. Với hệ thống giám sát ngoài app, dùng `ops/monitoring/prometheus.yml` làm mẫu; thay hostname public và tạo secret file trên collector. Không deploy Prometheus/Grafana chung container 512MB với Java.
4. Import `ops/monitoring/dashboard.json` vào Grafana, chọn Prometheus datasource. Self-hosted hoặc collector quản lý sẵn đều dùng được; không có service bên ngoài nào được app tự đăng ký. Rule file `alerts.yml` dùng với Prometheus/Alertmanager; Grafana Cloud cần tạo managed alert/contact point tương ứng bằng biểu thức trong file, không tự bật chỉ vì import dashboard.
5. Collector phải có persistent storage/retention và giám sát chính nó. Mỗi target là một node thực với nhãn instance ổn định. Không scrape URL load balancer chia ngẫu nhiên vì counter reset xen kẽ làm sai RPS/p95. Với toàn cụm: sum rate counters/buckets; queue chung dùng max. Kiểm tra TLS/token và ingress limit cho endpoint scrape.

Render Free ngủ sau 15 phút không traffic. Poll uptime/metrics liên tục là traffic có thể làm service thức và dùng tài nguyên/quota. Chọn lịch giám sát phù hợp bản demo, không ping để lách giới hạn. Nếu cần uptime/worker liên tục và nghiệm thu cảnh báo chính xác, dùng compute luôn chạy; chưa có bảo đảm ấy trong plan Free hiện tại.

## Luật cảnh báo mẫu và nghiệm thu

`alerts.yml` có unavailable 2m, DB fail 2m/stale, heap >85% 5m, CPU >85% 10m, pool waiting 2m, 5xx >5% khi đủ mẫu, p95 >2s, nhiều 503, worker không claim, DEAD và queue lag >5m. Đây là ngưỡng bắt đầu, cần tune sau baseline. Alertmanager/contact point nên group, chờ ngắn, repeat có khoảng và gửi resolved; giới hạn người nhận, không đưa token/PII vào alert. Cảnh báo phải đến từ ngoài app để vẫn đến khi JVM/DB/mail chết.

Trước vận hành, tại môi trường staging/DB tạm:

- Token đúng/sai/rỗng; cookie cũ không chặn scrape; token metrics không mở API khác.
- Tắt một instance: collector báo target down, receiver nhận và gửi recovered khi bật lại. Phân biệt token/network lỗi với process chết; đối chiếu Render Events.
- Mất DB: health vẫn UP đúng hợp đồng, probe báo fail, metrics tiếp tục đọc được; phục hồi có mẫu mới. Probe tắt không báo DB/queue giả.
- Chạy tải tăng dần: đo p95/p99, 429/503/5xx, heap/GC/RSS, pool wait; thử ngừng tải để thấy phục hồi. Không load test Neon production.
- Bật worker, tạo job fixture bị retry/DEAD trên DB tạm; xác nhận backlog/heartbeat cảnh báo đúng. Không gửi Gmail/Cloudinary thật trong test.

`promtool check rules` và `promtool test rules` chỉ xác nhận cú pháp/logic mẫu; không thay thử gửi thông báo thật. CI riêng cho cấu hình giám sát không build lại app chỉ vì sửa ops/docs/test.

## Xử lý sự cố và khôi phục

**503/quá tải:** xem tỷ lệ lỗi và pool waiting, log theo traceId, query chậm/transaction dài, quota IP qua trusted proxy. Không tăng thread/pool ngay; tổng connection tính mọi node và worker. Quota memory hiện chỉ từng node, khi scale cần ingress quota toàn cụm; chưa bật Redis.

**RAM cao/OOM:** đối chiếu Render Events và RAM tổng với heap/GC. Kiểm tra upload/concurrency, lượng thread, native/direct memory. Restart là hồi phục tạm, cần tìm nguyên nhân; không bật heap dump chứa dữ liệu nhạy cảm lên ổ public/ephemeral. App bị kill không tự gửi được cảnh báo.

**Job kẹt:** xem worker enabled, claim age, DB probe, lease và DEAD. Retry qua API có JOB_RETRY/version sau khi sửa nguyên nhân, không sửa SQL production tùy tiện. At least once có thể đã thực hiện provider trước lỗi ACK: kiểm tra receipt trước retry tránh gửi lặp. Job dài vượt lease cần thiết kế timeout/gia hạn riêng.

**Deploy lỗi:** CI pass không đồng nghĩa Render Live. Kiểm tra Events/Logs, env mới và health. Rollback image/code tương thích schema; không đảo migration có dữ liệu bằng cách xóa history. Với đổi schema phải dùng expand/contract và kiểm cũ/mới cùng chạy. Spring major chưa nâng trong lượt này.

**Mất dữ liệu:** xác định RPO/RTO với chủ dự án; kiểm chính sách retention/PITR của Neon plan đang dùng. Chưa có backup tự động bên ngoài được cấu hình trong repo. Xuất backup mã hóa bằng tài khoản phù hợp, không Git/image/Render ephemeral; phục hồi vào DB staging tách biệt rồi đối chiếu history/checksum, số dòng/FK, auth/catalog/media. Chỉ chuyển kết nối production sau khi nghiệm thu và có kế hoạch quay lại. Không thử restore vào DB production trong routine CI.

## Vòng đời dependency và phạm vi chưa hoàn tất

Dependabot chuẩn bị PR Maven/npm/Docker/GitHub Actions; không auto-merge major. Spring 6.2.19/Security 6.5.11 giữ theo stack đã chốt và có rủi ro vòng đời OSS; cần đợt nâng Spring/Servlet/Tomcat tương thích riêng. Dependency cập nhật không chứng minh hết lỗ hổng. Không thêm Kafka/Redis hoặc agent lớn chỉ để có nhiều tên công nghệ.

Đã chuẩn bị source/config/dashboard/alerts và kiểm local/CI theo PR. Chưa xác nhận receiver thật, backup/PITR hoặc SLA; chưa tuyên bố tải production, nhiều node không lỗi, hay tracing đầy đủ. Tách trạng thái này khi bàn giao để người mới không nhầm mẫu cấu hình với dịch vụ đã chạy.

Nguồn: [Micrometer Prometheus](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html), [Render Metrics](https://render.com/docs/service-metrics), [Health checks](https://render.com/docs/health-checks), [Notifications](https://render.com/docs/notifications), [Free](https://render.com/docs/free), [Spring 6.2.19](https://spring.io/blog/2026/06/08/spring-framework-7-0-8-and-6-2-19-available-now/).


## Truy cập, người online và tín hiệu bất thường

Trang `/monitoring` chia tab tài nguyên và **Truy cập và online**. Tab truy cập và các API `/api/v1/admin/system/activity`, `/online`, `/anomalies` chỉ ADMIN + SYSTEM_READ. Màn cấu hình an toàn hiển thị cờ activity/ngưỡng request; không hiển thị khóa băm, JWT hoặc URL DB.

Đặt `VIETROUTE_ACTIVITY_ENABLED=true` để ghi; mẫu Render và Run local đã bật, constructor test/WAR không cấu hình mặc định tắt. `VIETROUTE_ACTIVITY_REQUEST_THRESHOLD` mặc định 120 request/phút/chủ thể, trong khoảng 20–100000. V009 tạo bảng presence/bucket và hàm PostgreSQL `record_system_activity`; startup cần initializer bật rõ ràng khi triển khai migration. Heartbeat POST `/api/v1/activity/heartbeat` mỗi 60 giây khi tab nhìn thấy, vẫn có CSRF/quota. Trình duyệt nhận cookie visitor ngẫu nhiên có chữ ký, HttpOnly/SameSite, DB chỉ lưu HMAC. User ID chỉ được đặt sau JWT/password đã xác minh; không tin header/payload tự nhận user.

Online là `last_seen` trong **3 phút**. Nhiều tab cùng cookie tính một trình duyệt, nhiều trình duyệt cùng user tính một user; khách đếm trình duyệt, không khẳng định số con người thật. Cookie bị xóa/đổi JWT key hoặc client không giữ cookie có thể làm thay đổi số lượng. Presence cập nhật tối đa mỗi 30 giây trừ thay user, giữ tối đa 7 ngày; gần 10.000 hàng dọn theo lô 200 trình duyệt đã offline để nhường chỗ. Vì vậy số trình duyệt 24 giờ là số còn được lưu, không phải analytics bất biến.

Request bucket chia theo phút/chủ thể: user ID đã xác minh, hoặc HMAC địa chỉ từ trusted-proxy resolver cho request chưa xác thực. Không lưu IP thô, User-Agent, query, URL tùy ý, header, token hoặc payload. Filter ngoài Security thấy cả 401/403/429/5xx, loại health/assets/metrics, CSRF/docs/OpenAPI và API đọc activity để tránh nhiễu do polling hoặc hạ trạng thái user khi route chủ động bỏ JWT. Có thể có request chưa qua JWT bị tính vào nguồn khách, chẳng hạn quota chặn sớm.

Hai bảng nằm cùng PostgreSQL nên mọi node đọc chung; mỗi node dùng lại pool quan sát tối đa 1 connection, không thêm pool thứ ba hoặc queue lịch sử RAM. Hàm ghi dùng advisory try-lock rồi statement ghi với snapshot mới trong READ COMMITTED, một lần gọi mạng; xem [cơ chế VOLATILE chính thức](https://www.postgresql.org/docs/17/xfunc-volatility.html). Giới hạn mỗi bảng 10.000 hàng; query timeout 1 giây, socket 2 giây, mượn pool 250 ms, writer không chờ; lỗi có backoff 30 giây và `missedWrites` theo node. Cleanup nhỏ mỗi phút, khóa advisory giữa node; bucket giữ 60 phút, lô 500. Không đủ slot/DB lỗi/quota hàng thì có thể mất lượt ghi; UI báo counter, API đọc DB lỗi trả 503 và giữ snapshot cũ, không báo số 0 giả. Cấu hình quan sát không bảo đảm app chịu tải cao trên Neon/Render Free; phải đo tải thực trước khi tăng node/pool.

Tín hiệu xem trong 5 phút gần nhất, tối đa 100 dòng: request/phút vượt ngưỡng cấu hình, hoặc 401/403 >=20, 429 >=10, 5xx >=10. Người quản trị xem kết hợp nhật ký API/traceId và metrics; tín hiệu không tự chứng minh tấn công và chưa tự khóa user/IP. Quota memory chống spam vẫn theo node; không có Redis/Kafka trong cấu hình chạy hiện tại. Cảnh báo bên ngoài khi app chết/backup vẫn cần dịch vụ và bằng chứng triển khai riêng.

## Chẩn đoán đọc dữ liệu — AUTH18

Log mẫu event=http_completed bổ sung diagnostic và sqlState đã lọc từ trạng thái request. Khi journal DB không ghi được, mã trace trên log mẫu vẫn giúp xác định nhóm DB; log tối đa một dòng/giây/node như trước nên không hứa có log cho mọi request. Không ghi SQL/message driver/payload/email/credential hoặc dùng trace làm metric label. Dòng không có journal state ghi UNAVAILABLE; SQLState không có thì để trống, không suy luận DB quá tải chỉ từ 503.

Pool journal vẫn tối đa 1 connection/node, acquire tối đa 250ms. Validation connection dùng 1 giây như pool nghiệp vụ, tách khỏi thời gian chờ slot, tránh loại connection vì RTT mạng vượt 250ms. Socket/connect vẫn 2 giây, query vẫn 1 giây, backoff/missed-write và không queue RAM giữ nguyên. Cold start hoặc DB chậm vẫn có thể mất lượt ghi; không tăng pool/tắt nhật ký để che lỗi và không tự replay mutation.

## Vị trí thiết bị và Google Maps — TRACK01

Chủ dự án chọn Google Maps chưa cấu hình ngày 25/10/2026. Giao diện hiển thị UNCONFIGURED và danh sách lộ trình/tiến độ văn bản; không có key hoặc request provider được bật ngầm. `VIETROUTE_TRACKING_ENABLED` cho phép tính năng chia sẻ thiết bị, không tự bắt đầu chia sẻ hoặc xin quyền vị trí. Quản trị xem cờ và mapStatus trong Cấu hình an toàn.

Thiết bị gửi vị trí trực tiếp qua API riêng tư trong consent tài xế, PostgreSQL giữ snapshot/trace; không queue lịch sử RAM. Đóng chi tiết, trang bị ẩn/offline/pagehide dừng clearWatch ở client và thử gửi stop. Khi request stop không tới server, lease tối đa 120 giây từ mẫu cuối (hoặc lúc bật) giới hạn việc công bố; không cam kết dừng server ngay trong lúc mất mạng. Không tự bật lại khi trở lại online. Trình duyệt có thể không chạy nền/khóa màn hình; dữ liệu nhận là tọa độ thiết bị báo, không bảo đảm GPS vật lý chính xác hoặc chống giả mạo.

Trace query bỏ dữ liệu quá 7 ngày ngay khi đọc, trả tối đa 100 mẫu cùng consent; UI tối đa 20. Cleanup vật lý theo hoạt động nhận mẫu, lô 500/advisory lock, trần 2000 mẫu/trip và mục tiêu 10000 toàn app; khi không có hoạt động mới không có worker độc lập bảo đảm xóa vật lý đúng ngày. Khi dừng/hủy/đổi phân công/hoàn tất, snapshot bị xóa và trace không còn được công bố. Không coi trace giữ lại là audit bất biến hoặc nguồn tính tiền/chỗ/đã đón khách. API/guard và mã lỗi ở [API base](../02-phat-trien/API_BASE.md#chia-sẻ-vị-trí-riêng-tư--track01).

## Phân biệt độ trễ giao diện và hạ tầng

Khi chuyển trang chậm, đo riêng DNS/connect, thời gian nhận byte đầu và tổng tải bằng vài GET public tuần tự (`/health`, `/login`, asset CSS), rồi kiểm layout/client bằng fixture loopback. Không dùng build/test routine để ghi production hoặc bật probe giữ DB thức. `/health` không query DB, CSS không cần dựng giao diện: nếu các request này chưa nhận byte đầu thì không thể quy toàn bộ thời gian chờ cho JavaScript hay SQL nghiệp vụ. TTFB chưa có khi timeout không phải TTFB bằng 0; phân biệt lỗi client timeout với HTTP 5xx.

[Render Free](https://render.com/docs/free#spinning-down-on-idle) có cơ chế ngủ sau 15 phút không nhận traffic và cần thời gian bật lại; [Neon scale-to-zero](https://neon.com/docs/introduction/scale-to-zero) cũng có thời gian khởi động compute sau lúc không hoạt động. Đây là cơ chế có thể góp phần, không là bằng chứng nguyên nhân của một lượt chậm cụ thể. Cần log/metrics runtime và đo DB trên cùng request để tách mạng/Render/app/pool/Neon; không tự bật polling giữ dịch vụ thức, nâng gói hoặc sửa guard/cache authority để làm giao diện có vẻ nhanh. Bằng chứng lượt đo và giới hạn hiện tại ghi ở tiến độ/PR.

## Đo đăng nhập và diễn tập khôi phục — PERF01

Theo yêu cầu 27/10/2026, tiếp tục hoãn tích hợp dịch vụ ngoài. Luồng browser là GET CSRF → POST login → GET document dashboard/account; JS chuyển ngay sau POST thành công, không thêm timer giữ spinner. Đo riêng ba HTTP request và việc dựng trang, không lấy tổng thời gian chờ làm thời gian UI. Sau xác thực thành công, header `Server-Timing: auth;dur=<ms>` đo service auth (chờ pool/schema/SQL, BCrypt, commit, phát JWT), không gồm CSRF, quota/filter trước controller, ghi journal sau controller, JSON/mạng hoặc document đích. Chỉ có tổng thời gian, không tên user, query, password hoặc nhãn động; response lỗi không có header này để tránh thêm tín hiệu dò tài khoản. Giữ no-store và không mở CORS/Timing-Allow-Origin. Xem [Server Timing W3C](https://www.w3.org/TR/server-timing/).

Năm statement nghiệp vụ cũ của login được gộp còn hai bằng projection mảng và [PostgreSQL UPDATE RETURNING](https://www.postgresql.org/docs/17/dml-returning.html). Mỗi lượt vẫn có thêm statement chọn schema LOCAL và commit, không bỏ transaction/lockout/BCrypt hoặc cache authority để giảm số query. Đây là giảm lượt trao đổi DB, không cam kết tỷ lệ tăng tốc tương ứng trên mọi môi trường. Giữ các refresh guard HTTP/service riêng vì phải thấy quyền/tokenVersion bị thu hồi tại check tiếp theo.

Drill [verify-recovery.sh](../../.github/scripts/verify-recovery.sh) chỉ source cuối Docker CI, nhận đúng các container/database fixture cố định và từ chối chạy ngoài GitHub Actions. Nó dừng node test để snapshot/manifest đứng yên, dump custom, restore sang DB mới với exit-on-error; so digest mọi dòng bảng, sequence last_value/is_called và định nghĩa/trạng thái constraint. Sau đó kiểm 22 migration, khởi động app trên DB phục hồi, login với CSRF, GET me/dashboard/registry và đối chiếu password hash. Dump/manifest chứa dữ liệu fixture nên vẫn để trong thư mục/container CI tạm, không upload artifact hoặc in giá trị. Cleanup workflow gồm node recovery khi thành công hoặc lỗi.

Drill này không tạo lịch backup, kho lưu mã hóa, global PostgreSQL roles, PITR hoặc RPO/RTO cho Neon. Các phần đó cùng receiver thật vẫn cần cấu hình vận hành riêng khi chủ dự án triển khai tích hợp ngoài. Kiểm tải có trần hiện có vẫn bốn worker × 20 GET trên hai node CI; thêm năm login service tuần tự và số statement, không suy ra capacity hay SLA production. Kết quả từng lượt, commit và giới hạn ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md#perf01--đang-kiểm-ci-giảm-lượt-sql-đăng-nhập-và-diễn-tập-khôi-phục-cô-lập).
