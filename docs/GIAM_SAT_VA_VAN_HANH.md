# Giám sát và vận hành VietRoute

Cập nhật 22/10/2026. Không cần Kafka/Redis. Source cung cấp instrument, trang xem, dashboard và alert mẫu; việc nhận cảnh báo thật cần collector/receiver bên ngoài được cấu hình và thử nghiệm. Không tự tạo tài khoản, mua dịch vụ hoặc gửi thông báo thật trong build.

## Endpoint và quyền

| Đường dẫn | Dùng để làm gì | Phụ thuộc và bảo vệ |
|---|---|---|
| `/health` | Web availability, giữ hợp đồng text | Công khai, không DB; không xác nhận đăng nhập/DB/provider hoạt động |
| `/api/v1/admin/system/status` | Kiểm DB + queue/pool theo yêu cầu | JWT/cookie và SYSTEM_READ; query DB thật |
| `/api/v1/admin/system/runtime` | JVM/HTTP/pool/worker và mẫu DB cache | SYSTEM_READ; method không query DB, nhưng JWT vẫn kiểm tài khoản qua DB |
| `/monitoring` | Trang xem và 30 mẫu gần nhất trong tab | SYSTEM_READ, poll 5s, dừng khi tab ẩn; không thay kho lịch sử |
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
