# Nhật ký API và xuất request để debug

## Sử dụng trên giao diện

Đăng nhập **ADMIN có SYSTEM_READ**, mở **Hệ thống → Nhật ký API** (`/operations/api-journal`). Dán mã yêu cầu `traceId` từ thông báo lỗi hoặc DevTools → Network → Response. Có thể lọc thêm endpoint, method, HTTP status, mã lỗi và thời gian xử lý tối thiểu; đánh dấu **Chỉ xem lỗi** để bỏ request thành công. Tra cứu thủ công, không poll liên tục.

Mở **Chi tiết** để xem controller/method, status, thời gian, mã lỗi/messageKey và nhóm chẩn đoán/SQLState. Modal có ba tab **Request / Response / Xuất debug**, hỗ trợ bàn phím và trả focus về nút mở sau khi đóng. JSON được hiển thị bằng text, không render HTML từ payload.

Ảnh kiểm giao diện với HTML thật và dữ liệu giả, không phải request hoặc lỗi production:

![Danh sách nhật ký API trên desktop](images/api-journal.png)

[Modal trên màn hình 390px](images/api-journal-mobile.png).

`traceId` dùng để đối chiếu request, không phải nguyên nhân lỗi. Một HTTP 503 kéo dài 20 giây chưa đủ kết luận DB quá tải: có thể hết connection pool, lỗi mạng, DB chưa sẵn sàng hoặc admission guard. Đối chiếu nhóm chẩn đoán với pool waiting, heap/CPU, log server và tình trạng DB tại thời điểm đó.

| Nhóm chẩn đoán | SQLState nếu driver cung cấp | Cách hiểu ban đầu |
|---|---|---|
| `DB_CONNECTION` | lớp `08` | Mất/không mở được kết nối; kiểm DB, mạng và pool |
| `DB_AUTHENTICATION` | lớp `28` | DB từ chối xác thực; kiểm Environment, không copy credential vào nhật ký |
| `DB_SCHEMA_OR_QUERY` | lớp `42` | Schema/table/query không hợp lệ; đối chiếu migration và schema cấu hình |
| `DB_CAPACITY` | lớp `53` | DB thiếu tài nguyên/connection; kiểm quota và tải thực |
| `DB_TRANSACTION` | lớp `40` | Transaction bị rollback, ví dụ deadlock/serialization |
| `DB_LOCK_OR_STATE` | lớp `55` | Lock hoặc trạng thái DB chưa đáp ứng thao tác |
| `DB_FAILURE` | khác hoặc không có | Có SQLException nhưng chưa phân loại được; xem log vận hành |
| `APPLICATION_ERROR` / `API_ERROR` | không có | Lỗi app/validation/security; đọc mã lỗi, controller và errors đã lọc |
| `HTTP_STATUS_ONLY` | không có | Không đi qua response factory; chỉ có metadata HTTP |
| `SUCCESS` | không có | Response thành công theo envelope chung |

SQLState chỉ là gợi ý phân nhóm; không có SQL, parameter, stack trace hoặc message driver trên UI. Request bị chặn trước MVC có thể là `UNMAPPED` / `PRE_CONTROLLER`: lúc đó không có controller thực thi, cũng không giữ URL thô để suy diễn lại.

## Copy cURL và Postman

1. Trong **Xuất debug**, bấm **Sao chép cURL** hoặc **Tải Postman JSON**. Nếu trình duyệt không cho clipboard, nội dung được chọn để nhấn Ctrl+C.
2. Với cURL: Postman → Import → Raw text, dán nội dung. Hoặc import file `*.postman_collection.json` theo [schema Postman v2.1 chính thức](https://schema.postman.com/collection/json/v2.1.0/draft-07/docs/index.html).
3. Điền `baseUrl` trong môi trường Postman, ví dụ `http://localhost:8080`; WAR deploy có context thì dùng `http://localhost:8080/vietroute`. Điền `accessToken` bằng token của tài khoản debug được cấp quyền. Collection không lấy token/cookie đang đăng nhập trên trình duyệt.
4. Điền lại `[REDACTED]`, placeholder path và payload còn thiếu theo Swagger. Auth chỉ có placeholder username/password; file/multipart và DTO không được capture cần bổ sung thủ công. Mẫu response trong collection là projection đã lọc, không phải toàn bộ response server.
5. Kiểm tra method, URL và payload trước khi tự bấm Send. Export không gửi lại request. Mutation có thể thay đổi dữ liệu; ưu tiên môi trường development/staging tách biệt.

Collection không có script, secret, cookie hay thông tin môi trường thật. Giữ token/password trong môi trường riêng hoặc Vault của công cụ, không commit collection đã điền secret. cURL dùng cú pháp shell POSIX; Windows có thể import trực tiếp vào Postman, không dán nguyên cú pháp này vào PowerShell.

## Chính sách dữ liệu

Không ghi raw HTTP body hoặc buffer response. Không ghi Authorization/cookie/header, IP, URL thô, query không được cho phép, file, mail, password, JWT hoặc dữ liệu danh tính. Không lưu secret vào tab/storage.

Request chỉ projection DTO có trong danh sách tường minh: tạo/sửa/xóa danh mục, gán quyền role/gán role user, retry job. Chỉ giữ số, boolean và enum; mã/tên tự do bị thay bằng `[REDACTED]`. Field/DTO mới mặc định bị bỏ. JSON không parse được không có snapshot payload. Unknown body chỉ ghi trạng thái đã bỏ.

Response giữ `success`, `code`, `messageKey`, `locale`, field/code/messageKey của tối đa 10 lỗi validation. Không giữ message/args có thể chứa dữ liệu nhập. Data danh mục chỉ giữ projection an toàn, danh sách công khai tối đa 3 item; data auth/access/mail/media/job/runtime và DTO khác bị bỏ. Mỗi snapshot giới hạn 4096 ký tự, quá giới hạn được thay bằng trạng thái đã bỏ. Số byte thực tế phụ thuộc encoding/overhead JVM; đây là giới hạn chuỗi, không phải số đo RAM benchmark.

Route lấy từ mapping MVC; replay chỉ khôi phục path variable số/UUID/enum hợp lệ. Query chỉ nhận `page`, `size`, `lang=vi|en`, `status` job đã cho phép. Path/query còn thiếu phải điền lại; endpoint không mapping không được giả làm endpoint hợp lệ.

## Kiến trúc và hợp đồng

- `ApiJournalFilter` đặt trước Security/traffic để ghi cả 401/403/429/503 và đo duration. Chỉ `/api/**` đồng bộ; bỏ qua chính API nhật ký, static/page/health/metrics/Swagger. Hiện chưa instrument Servlet async/streaming.
- `ApiJournalBodyAdvice` đọc DTO đã parse trước validation, tạo projection rồi trả nguyên DTO cho controller. Không đọc stream thêm lần nữa.
- `ApiResponses` ghi projection response vào trạng thái request; `ApiExceptionMapper` trích SQLState tối đa 8 tầng cause. `BaseRepository` giữ cause nội bộ để chẩn đoán, không trả cause ra client.
- Khi request hoàn tất, filter lưu metadata/projection vào `ApiJournalStore`. Không lưu servlet, exception, principal hay raw object. UUID bản ghi khác traceId vì client có thể gửi lại cùng traceId.
- Store dùng bộ đệm RAM có khóa ngắn, dung lượng/TTL giới hạn; không thêm thread, DB query, bảng hoặc job cho mỗi request. Service và page kiểm **ADMIN + SYSTEM_READ**; API kế thừa `BaseController`, schema trên Swagger.

WAR có hai Spring context: root tạo service/security, servlet tạo controller. `@EnableMethodSecurity` phải hiện diện tại `SecurityConfig` **và** `WebConfig`; advisor root không tự áp dụng cho controller ở servlet context con. Test hồi quy dựng đúng hai context để kiểm HTTP 403 cho trang nhật ký/giám sát/cấu hình/job/quyền khi CUSTOMER nhập URL trực tiếp. Không chỉ kiểm bằng context gộp hoặc dựa vào menu bị ẩn.

| API GET | Hợp đồng |
|---|---|
| `/api/v1/admin/system/api-journal` | Envelope `ApiJournalResponse`: instanceId, enabled, capacity, retentionMinutes, evicted và `records` phân trang |
| `/api/v1/admin/system/api-journal/{id}` | Envelope `ApiJournalDetail`: entry, instanceId, replayPath, query, requestBody, responseBody, contentKind; id UUID |

List nhận `page=0`, `size=20` (tối đa 100), `traceId` chính xác, `endpoint` chứa chuỗi route, `method`, `status=0` bỏ lọc, `code`, `minDurationMs=0`, `errorsOnly=false`. Lọc/paging trên bộ đệm, request mới hoàn tất trước nằm đầu. 400 cho bộ lọc không hợp lệ, 401 chưa xác thực, 403 thiếu role/quyền, 404 bản ghi hết hạn/khác node/không tồn tại. Response dùng locale/traceId/error envelope chung và `Cache-Control: no-store`.

## Cấu hình và nhiều node

| Biến môi trường | Mặc định | Giới hạn |
|---|---|---|
| `VIETROUTE_API_JOURNAL_ENABLED` | `true` | Bật/tắt từ Environment khi restart |
| `VIETROUTE_API_JOURNAL_CAPACITY` | `300` | 20–1000 bản ghi/node |
| `VIETROUTE_API_JOURNAL_MINUTES` | `30` | 1–120 phút, tính từ lúc request bắt đầu |

Lịch sử **mất khi restart/deploy**, bị loại khi đầy/hết TTL; TTL được dọn khi ghi/đọc tiếp, không có scheduler riêng. Không truy hồi được request xảy ra trước khi bật tính năng. UI hiển thị instanceId và cảnh báo khi node đổi; load balancer chuyển node có thể khiến chi tiết trả 404. Không cộng gộp hay đồng bộ log toàn cụm. Nếu cần lịch sử lâu dài/toàn cụm, thiết kế kho log ngoài app, retention/quyền truy cập và cách lọc dữ liệu trước khi gửi; không bật Kafka/Redis trong giai đoạn này.

Nhật ký là công cụ chẩn đoán trong app, **không phải API gateway**, không routing upstream, không audit bất biến. Khi DB hỏng, JWT vẫn cần DB để kiểm tài khoản/quyền nên trang/API nhật ký có thể không mở được; không tạo đường bỏ qua auth. Dùng Logs đúng node trên Render/IntelliJ và metrics token độc lập theo [vận hành](GIAM_SAT_VA_VAN_HANH.md); sau DB hồi phục có thể tra request còn trong bộ đệm.

## Mở rộng và kiểm tra

Khi thêm API/DTO, cập nhật Swagger/locales và kiểm quyền backend như cũ. Chỉ bổ sung projection sau khi rà từng field dữ liệu, không chuyển sang log raw hoặc blacklist theo tên password/token. Giữ giới hạn snapshot/list/TTL và kiểm sentinel secret không lọt vào cả UI/export. Không thêm email/username/address vào whitelist chỉ để tiện replay.

`ApiJournalTest` kiểm filter/security/MVC thật bằng MockMvc, DTO validation, service permission, TTL/capacity/ghi đồng thời, body/path/query omission, SQLState và OpenAPI/HTML vi/en với DB tắt. Node test kiểm cURL/collection không secret/script và quoting. Docker CI bổ sung tra trace/controller/snapshot/auth omission và từ chối CUSTOMER trên PostgreSQL **tạm**; không dùng Neon hoặc provider thật. Kiểm UI bằng dữ liệu giả chỉ xác nhận frontend, không thay bằng chứng auth/SQL từ CI.
