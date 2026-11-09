# Vận hành, quản trị và debug

Đây là điểm tra cứu chung cho chạy thật. Cách dựng local/tài khoản ở [CHAY_LOCAL](../01-bat-dau/CHAY_LOCAL.md); mốc môi trường/PR ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md). Không coi hướng dẫn hoặc /health200 là bằng chứng DB/login đã đúng.

## Quản trị tài khoản và quyền

| Màn | Dùng để làm gì |
|---|---|
| /operations/accounts | Tạo account, sửa tên, khóa/mở/xóa mềm, gán role và xem quyền đích |
| /operations/access | Tab Role/Chức năng/Phạm vi; CRUD và gán theo endpoint được phép |
| /operations/access/resources | Inventory method + route template từ MVC, không tự cấp grant |
| /account, /dashboard | Hồ sơ/đổi mật khẩu của chính mình và tổng quan theo capability |
| /operations/api-journal | Trace đã lọc; cần grant page và API riêng |

Không còn màn job/configuration/monitoring/media hoặc auth recovery/email. Account quản trị tạo được xác minh nhưng bắt đổi password; account seed mới có ngoại lệ active/verified theo cấu hình. Không lấy email thay username khi login.

Cấp nhân viên: tạo Function với menu/API cần dùng → gán Function vào Role → gán Role cho User → gán membership/role trong nhà xe phù hợp. Public/self-service không được DB/client chuyển thành private grant. Người cấp chỉ ủy quyền trong tập endpoint/phạm vi hiện hành của mình, kể cả role/function đang tắt. Mở URL trực tiếp và thử sai scope để nghiệm thu; không dùng admin thay mọi tác nhân.

## Nhật ký API và debug

Page /operations/api-journal, GET /api/v1/admin/system/api-journal và /{id} cần grant chính xác; không ADMIN/SYSTEM_READ bypass. Lọc traceId/userId/endpoint/method/status/code/duration hoặc chỉ lỗi. Danh sách phân trang chỉ metadata, bấm chi tiết mới lấy snapshot. request_user_id chỉ từ JWT/password đã xác minh; username join theo ID, không tin X-User-ID/body hoặc backfill log cũ.

TraceId tương ứng X-Request-ID giúp nối lỗi filter/controller/service. startedAt/recordedAt và durationMs không gồm chính thao tác ghi journal. Có thể ghi401/403/413/503 trước controller; route chưa xác định là UNMAPPED/PRE_CONTROLLER. Journal không tự ghi các truy vấn của chính nó.

Snapshot qua whitelist DTO/field trong ApiJournalService, field mới mặc định bỏ. Không raw header/cookie/body/PII/password/hash/token, stack trace, SQL hoặc parameter. Auth/access và kiểu không hỗ trợ là BODY_OMITTED. Snapshot tối đa 4096 ký tự, tối đa 3 item một trang; lỗi chỉ giữ code/messageKey/locale/field và SQLState/diagnostic đã lọc.

Copy cURL/export Postman dùng origin/context thật và replayPath đã lọc. Tự điền token/body placeholder trên môi trường được phép; UI không đọc cookie auth hoặc gửi lại mutation. Snapshot không phải request nguyên bản để chạy lại ngay; không trình bày export như một cổng replay giao dịch.

Journal ghi trực tiếp PostgreSQL, pool riêng tối đa 1 connection/node; chờ pool250ms, connect/socket2s, query1s, slot ghi100ms. Lỗi tăng failedWrites, backoff30s, không queue RAM hoặc gửi bù. Counter reset khi restart; có thể mất trace lúc DB/slot/process lỗi. Nghiệp vụ vẫn được trả kết quả dù log thất bại.

Retention journal 7 ngày/mục tiêu10000 dòng; cleanup theo traffic thành công tối đa mỗi phút, lô500 hết hạn và 500 vượt mục tiêu, advisory lock. Cùng bảo trì dọn auth history lô200/7 ngày/mục tiêu1000 ID; DB_INIT cũng dọn auth history. Không có worker nền; không traffic thì xóa vật lý đợi lần bảo trì tiếp, API vẫn ẩn record hết hạn.

## Phát hành và Render

Nhánh thành viên → PR/check/review → dev. Chỉ lệnh phát hành mới cho phép dev → main, Production CI và deploy; không direct dev/main hoặc Manual Deploy để bỏ gate. Render bám main, After CI Checks Pass; build filters phải đồng nhất [render.yaml](../../render.yaml)/workflow. Docs/test không tự kích hoạt production image.

Docker dùng -Pproduction và src/main, không dev launcher/test/.env/Node. WAR/Tomcat nhận env, không đọc file .env. Environment cần JDBC URL/user/password/schema/vendor, JWT cố định, bootstrap và cờ init có chủ đích. Cookie production Secure; không xoay JWT hoặc reset password chỉ vì deploy. sync:false không tự cập nhật secret service đã tồn tại.

Service triển khai là vietroute-web-sg, region singapore. URL hiện hành: [VietRoute Singapore](https://vietroute-web-sg.onrender.com/journeys?lang=vi); vietroute-web.onrender.com là service Virginia cũ, không tự chuyển địa chỉ khi đổi vùng. Render không đổi vùng service có sẵn; cần tạo service mới, kiểm rồi mới chọn URL dùng. Giữ service Virginia làm điểm đối chiếu trong đợt chuyển, không tự xóa hoặc nâng gói. Với node mới dùng chung DB đã khởi tạo, đặt DB_INIT_ENABLED/ACCESS_SYNC_ENABLED/DEFAULT_ACCOUNTS_ENABLED/BUSINESS_INIT_ENABLED=false trong Environment; giữ DB/JWT/cookie và grant hiện hành. Blueprint vẫn có cờ init opt-in cho lần dựng DB mới, không dùng nguyên cờ đó để seed lại DB đang chạy. Kiểm tên/region/Free/main/checksPass, /health và build filters thực trong Dashboard; YAML không tự sửa service đã tạo thủ công. Trạng thái Live và số đo hai vùng xem tiến độ, không suy từ cấu hình dự kiến.

DB hiện hành hieuboy/schema vietroute; không fallback Neon. Máy chủ chưa TLS, chủ dự án đã xác nhận riêng sslmode=disable cho đích này. Không đưa lựa chọn đó thành mặc định cho DB khác; bật TLS cần cấu hình CA/hostname đúng. Mật khẩu DB chỉ .env local/Render, không Git/properties/docs/log. Ngoại lệ .env.example giữ các password/key khác theo chủ dự án, riêng DB password là XXX; lịch sử Git cũ chưa viết lại.

Khi sửa Environment có trường secret tải bất đồng bộ: mở/chờ tải → sửa → đọc lại bản đã lưu bằng so sánh boolean, không in giá trị → triển khai → nghiệm thu. Một màn Live hoặc label Environment updated không chứng minh URL đã đổi. Khi kiểm đăng nhập thật cần tính tác động tăng tokenVersion và ghi nhật ký; kiểm thường kỳ dùng DB riêng.

## Database, backup và rollback

Đợt lịch chạy hằng ngày cần áp dụng `trip/002_daily_service.sql` qua `DatabaseInitializer` trên PostgreSQL đã sao lưu và thử phục hồi. Migration chuyển bốn khung giờ giả lập Duy Long hiện hữu nếu tìm đúng mã nguồn đã công bố. Với DB dựng mới, bộ init đầy đủ tạo 26 nguồn cố định dưới 24 giờ, phủ bảy nhà xe. Kiểm tra `/health`, tìm hai chiều Hà Nội–Hải Phòng theo ngày/giờ và báo giá công khai.

Trên DB hiện hữu, giữ cờ seed tài khoản/nghiệp vụ tắt. Với từng lịch cố định chưa bật, điều hành vào **Chuyến**, mở lượt đã công bố có giờ, tuyến, xe và tài xế đúng lịch thực tế, rồi chọn **Bật lịch chạy hằng ngày**. Mỗi lịch chỉ cần một chuyến nguồn. API tương ứng là `PUT /api/v1/admin/trips/{id}/schedule`, cần grant và scope đúng. Xử lý xung đột lịch ở xe hoặc tài xế trước khi bật. Tìm một ngày ngoài cửa sổ seed và đối chiếu giờ khởi hành, báo giá, số chỗ. Hai tuyến mẫu dài 34 giờ cần nhiều ca xe/tài xế để có lượt mỗi ngày.

Không sửa tám SQL/checksum đã áp dụng; bước mới thêm số liên tiếp trong cụm và manifest. Runtime khóa/checksum/transaction, từ chối history lạ. Database hiện hữu muốn rebaseline: ủy quyền đúng đích → backup mã hóa/kiểm phục hồi → dừng mọi node → scripts/development/rebaseline-database.py --check → --apply --confirm-stopped nếu cấu trúc khớp. Chỉ đổi history, không DROP bảng hoặc đổi hash/ID/sequence/audit.

Backup bằng pg_dump phải phục hồi vào DB riêng để kiểm bảng/dữ liệu/sequence, không chỉ kiểm file tồn tại. Với --schema, chuẩn bị extension phụ thuộc như pg_trgm đúng namespace; so digest dữ liệu Linux/Windows phải cùng timezone UTC và thứ tự COLLATE C. Bản DPAPI CurrentUser local gắn với tài khoản Windows, không là backup di động, PITR hoặc kế hoạch RPO/RTO. Trạng thái đã kiểm của từng môi trường nằm ở tiến độ; không hứa khả năng phục hồi chưa thử.

Rollback cần source khớp baseline. Không tùy ý chạy WAR trước đợt thay baseline hoặc đổi sang DB khác để che lỗi startup. Dừng ghi nếu cần kiểm bảo trì; không reset DB thật để làm build xanh.

## Kiểm tra và xử lý lỗi

| Triệu chứng | Kiểm theo thứ tự |
|---|---|
| /health200 nhưng login/API lỗi | Env nguồn OS_IDE/LOCAL_FILE/DEFAULT, kết nối DB, schema/history/grant; health không query DB |
| Local DB báo Asia/Saigon không hợp lệ | Dùng Run config -Duser.timezone=Asia/Ho_Chi_Minh giống Docker |
| 401 | Phiên/issuer/audience/expiry/tokenVersion/trạng thái account; đăng nhập lại, không bỏ JWT |
| 403 | code/messageKey: grant page/API, CSRF, trần scope; ẩn menu không giải quyết backend |
| 404 khi đổi ID | Bản ghi không có hoặc ngoài owner/scope/assignment; không làm lộ bằng API khác |
| 409 | Tải lại version/trạng thái, giữ draft, người dùng quyết định gửi lại; không replay tự động |
| 429/503 hoặc chậm | Request/BCrypt guard, pool, mạng, SQL và CPU; không nới bảo mật/cache grant để che lỗi |
| Tìm không có chuyến | Ngày tại điểm đón, chiều, OPEN/IN_PROGRESS, điểm chưa rời, chỗ còn và nhà xe/xe/tài xế hợp lệ |
| Không thấy log | Grant/lọc/retention, failedWrites/backoff/pool lạnh; không hứa ghi đủ mọi trace |
| Oracle dừng startup | Đang bị chặn có chủ đích; chưa chuyển SQL/migration, không bỏ chặn vì driver nạp được |

Render Free có cold start; đã có lượt mất hơn3 phút. Không quy mọi chậm cho DB hoặc áp mô tả scale-to-zero Neon cũ vào máy chủ mới. Pool nghiệp vụ4 + journal 1/node; đo p50/p95, lỗi, query và CPU trên workload staging trước kết luận capacity. Không giữ dịch vụ thức bằng polling, load test/seed DB thật hoặc tăng gói khi chưa được yêu cầu.

Sau deploy kiểm đúng commit Live, login/me/API có DB, thiếu quyền/scope, dữ liệu, locale và journal. Routine test dùng PostgreSQL/H2 riêng, không .env/DB production. Ghi kết quả thật, lỗi đã giải quyết và giới hạn vào [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

## Chuyển Category

Source giữ tám baseline đã áp dụng, thêm catalog/002_category.sql và catalog/003_location_wards.sql. Không chạy code cũ cùng schema mới. Trước cutover cần backup/kiểm phục hồi, kiểm map xã cho mọi địa điểm, dừng tất cả node, áp dụng hai bước trong transaction và đối chiếu dữ liệu/khóa ngoại. Bản sao đã diễn tập có 22 bảng, history 10; trạng thái DB thật theo tiến độ. Tài khoản, hash, ID giao dịch và snapshot được giữ; ID danh mục cũ được remap theo mã.

Bước003 xóa các target danh mục cũ theo whitelist và bổ sung bảy grant menu/API Category vào function ADMIN_CONSOLE nếu function này đã tồn tại. Không tạo role/user mapping hoặc bật lại function/role/mapping đã ngừng; không chuyển quyền đọc riêng loại xe thành quyền đọc toàn bộ danh mục. Function có tập tài nguyên thay đổi được tăng version/audit system0 và ghi CATEGORY_RESOURCES_MIGRATED trong cùng transaction. Quyền riêng ngoài bộ quản trị cần chủ động gán sau khi review.

Leaflet dùng WebJar cục bộ; tile từ [OpenStreetMap](https://operations.osmfoundation.org/policies/tiles/) theo thao tác mở bản đồ, attribution luôn hiển thị. Giữ cache/Referer mặc định của browser, không prefetch/bulk download. Tile là dịch vụ ngoài; lỗi tile không được thay tọa độ đã lưu hoặc gửi lại mutation.
