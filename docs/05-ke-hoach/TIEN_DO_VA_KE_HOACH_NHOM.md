# Tiến độ và bàn giao

Mốc 02/11/2026, người thực hiện hieuv526034. Phân công bảo trì ở [PHAN_CONG](PHAN_CONG.md), lịch sử chi tiết tra Git/PR; không coi trách nhiệm được giao là tác giả source đã có.

## Mốc đầu vào trước DUYLONG01

| Mốc | Trạng thái và bằng chứng |
|---|---|
| dev | 8143627; [PR151](https://github.com/hieuv526034/vietroute-web/pull/151) và [PR153](https://github.com/hieuv526034/vietroute-web/pull/153) đã merge |
| main | bbeec57, [PR152](https://github.com/hieuv526034/vietroute-web/pull/152) đã merge; Production Docker chạy thành công sau sửa vòng đời hai node CI |
| UI02 | [PR154](https://github.com/hieuv526034/vietroute-web/pull/154), head0f4ceca, còn mở: Swagger chỉ admin theo grant, khoảng cách menu, select tìm chuyến phụ thuộc và một nút tìm. CI đạt, chưa có trên dev/main/Render |
| DB02 | [PR155](https://github.com/hieuv526034/vietroute-web/pull/155), head5974ce7, còn mở; CI đạt. Source đổi cấu hình DB, bỏ theo dõi .env và chuẩn hóa timezone local |
| Render | Image main bbeec57; Environment đã chuyển sang DB mới độc lập với việc merge PR155. [Deployment dep-dbk5gdqjnfac739l8flg](https://dashboard.render.com/web/srv-dbcve0q9lscs73f81reg/deploys/dep-dbk5gdqjnfac739l8flg) Live; [ứng dụng](https://vietroute-web.onrender.com) |
| DOCS03 và cấu hình bổ sung | Nhánh hieuv526034/docs03-tai-lieu-demo từ dev, nhận DB02 bằng fast-forward; đã kiểm local, chưa tích hợp dev/main. Đã nhận source PR154 bằng patch trong UI03, chưa triển khai production |

Trạng thái PR trên được đối chiếu khi bắt đầu DOCS03; trước demo/phát hành cần đọc lại head/check/merge thực tế.

## Những phần hiện hành

- Auth 40 file/8 bảng; business 121/14 theo catalog/carrier/trip/booking/report/bootstrap; common 61. Entity JPA, query tại repo, JDBC batch/query/khóa phức tạp dùng chung transaction.
- Baseline8 SQL tạo 24 bảng gồm journal/history; runtime kiểm checksum/history, không reset tự động. Lần rebaseline Neon trước DB02 đã backup/kiểm phục hồi, chỉ đổi history và giữ dữ liệu/sequence; môi trường này đã nghỉ, không fallback.
- Đặt theo chặng, snapshot tuyến/chuyến, tồn chỗ từng segment, xác nhận/thu–hoàn thủ công, tài xế xác nhận tiến độ và báo cáo theo ngày khởi hành. Không GPS/mobile/payment gateway/mail/upload/job/metrics.

## DB02 — kết quả đã kiểm

DB hiện hành PostgreSQL16.14, database hieuboy/schema vietroute, do chủ dự án cấp. Máy chủ chưa hỗ trợ TLS; sslmode=disable được xác nhận riêng. .env chỉ local, credential Render qua Environment, không ghi vào Git/docs/classpath; lịch sử Git cũ chưa viết lại.

Trước init, schema chưa có và không có bảng người dùng; đã backup mã hóa DPAPI CurrentUser, phục hồi vào PostgreSQL loopback riêng và đối chiếu trạng thái trống. Startup có chủ đích tạo 24 bảng/8 history/43 account sử dụng,6 nhà xe,24 xe/24 tài xế,24 tuyến,494 chuyến/2952 đơn. Không đổi tám SQL. Dữ liệu do dự án giả lập.

Local8080 đạt 43/43 login bằng username, đọc dữ liệu theo scope/owner và logout; bốn dashboard200, khách vào account API403. JVM Windows ban đầu bị từ chối alias Asia/Saigon; Run config đã dùng Asia/Ho_Chi_Minh như Docker. Không đổi timezone DB.

Render lần lưu Environment đầu bị ba trường secret tải bất đồng bộ ghi đè giá trị nhập. Đã chờ tải, sửa, mở lại và so sánh URL/user/password bằng boolean; lượt HTTP trước đó không được tính là bằng chứng DB mới. Deployment trên Live sau3m04s; lượt cuối 43/43 account đạt,0 lỗi, public6 nhà xe. tokenVersion của43 account tăng ngay trong DB mới ở lượt kiểm này, xác nhận Render thực sự dùng hieuboy. Local và Render là hai lượt riêng; không xóa Neon, load test, đổi plan/JWT hoặc merge main trong DB02.

## Kiểm thử theo mốc

| Phạm vi | Kết quả thực tế | Giới hạn |
|---|---|---|
| Nền trước UI02/DB02 | 198 Java, gồm 68 PostgreSQL cô lập;63 Node; Maven verify/WAR và formatter/rules/docs đạt | Không suy thành capacity/SLA production |
| UI02/PR154 | 199 Java, gồm 68 PostgreSQL;66 Node, browser desktop/mobile và CI đạt | Còn PR, chưa main/Render |
| DB02/PR155 | 27 test cấu hình/registry/rules/security/render, không lỗi/skip; npm ci/format/check/rules/docs và CI đạt | Không chạy lại full nghiệp vụ/Docker chỉ vì đổi config |
| DOCS03/cấu hình bổ sung | 29 Java cấu hình/locale/security/smoke/navigation, 0 lỗi/skip; 63 Node; npm ci/format/check/rules/docs/diff đạt | Không chạy lại full PostgreSQL/Docker; chưa restart app thật hoặc deploy |

## DOCS03 — thay đổi đang nghiệm thu

Gộp 22 tài liệu Markdown thành 12: tổng quan/local/format, backend/schema, UI, vận hành, phân công/tiến độ/demo và bàn giao AI. Mỗi thành viên có luồng, bảng/API/source, ca lỗi và đầu ra bàn giao. Bộ 45 hỏi–đáp kèm kịch bản 15 phút, nêu rõ hạn chế Oracle/TLS/payment và PR chưa phát hành. Bỏ 12 hướng dẫn đã gộp, 19 ảnh UI phiên cũ; giữ nguyên checksum mẫu Word và chuyển vào05-ke-hoach/mau. Chưa soạn báo cáo Word.

Bổ sung chú thích toàn bộ 302 field ở 23 entity và BaseEntity, gồm khóa ghép; kiểm bỏ comment cho thấy mapping/code entity không đổi, tám SQL giữ nguyên. postgresql.properties dùng URL/user/schema hiện hành, password trống và env ưu tiên. Theo xác nhận ENVEX01 của chủ dự án, .env.example giống hệt .env gồm password/key khác, duy nhất VIETROUTE_DB_PASSWORD=XXX; thay thế cách che mọi secret trước đó. .env vẫn ignore, giữ nguyên trên máy; không in giá trị vào docs/log/chat. Locale mặc định vi, kể cả browser Accept-Language tiếng Anh; en chỉ khi chọn lang/X-Locale rõ ràng.

Kiểm riêng 12 tài liệu/45 câu hỏi/bốn phần thành viên, 163 link/anchor nội bộ và checksum Word. ENVEX01 kiểm mọi giá trị ngoài DB password bằng so sánh trong bộ nhớ, .env không đổi; quét file Git bảo đảm không có mật khẩu DB và các secret khác chỉ nằm trong .env.example được cho phép. npm ci/format/check/rules/docs/diff đạt, không chạy lại Java/Node vì lượt bổ sung chỉ sửa env mẫu và hướng dẫn. Maven Wrapper trên Windows PowerShell dừng trước build do Target[0] null; không sửa wrapper để né. Đã dùng đúng Maven3.9.11 cache và chỉ rõ kho dependency local để chạy 29 test ở lượt trước, không đọc .env hoặc dùng DB thật.

## UI03 — menu và các bộ chọn nghiệp vụ

Tiếp tục trên hieuv526034/docs03-tai-lieu-demo để giữ các thay đổi đang nghiệm thu. Nhận source/test của UI02 commit0f4ceca bằng patch, không phục hồi tài liệu/ảnh cũ và không merge dev/main. Swagger private giữ grant DB, regression hai context đã chạy; PR154 vẫn chưa merge.

Gom menu theo Danh mục/Nhà xe/Tuyến–chuyến/Đặt chỗ/Báo cáo/Quản trị sau khi DB lọc quyền. Nhóm chỉ trình bày các link đã có; không cấp grant, thêm role/function cố định hoặc sửa dữ liệu DB. Link cách6px, giữ trạng thái hiện hành và breadcrumb.

Tìm chuyến dùng tỉnh → địa điểm, nhà xe/loại xe và một nút tìm. Bỏ nút tìm, bộ đếm/phân trang riêng ở select lập chuyến, đặt chỗ, tuyến, xe/tài xế, tỉnh và nhà xe báo cáo. Danh mục đọc đủ trang100 dòng trong scope, không cắt20 kết quả. Đổi nhà xe xóa tuyến/xe/tài xế cũ trước khi gọi API; allReads chờ các request con kết thúc, lỗi danh sách chặn lưu. Báo cáo đổi select chỉ ẩn snapshot, submit mới lấy số liệu. Pager bảng rỗng bị ẩn, một trang ẩn điều hướng/nhảy trang.

Kiểm thực tế: Maven verify/WAR đạt201 Java, gồm68 PostgreSQL tại127.0.0.1:15461/vietroute_business_test, không fail/error/skip;67 Node và npm ci/format/check/rules/docs/diff đạt. Full test phát hiện hợp đồng key môi trường cũ bắt LOCAL_ENV_FILE có trong mẫu; đã sửa test theo launcher chọn file từ OS/IDE trước khi đọc file, giữ kiểm đủ các key ứng dụng. Không sửa checker/formatter để né lỗi.

Đã render14 template vi/en, browser kiểm một nút tìm/không pager trong bộ chọn, tỉnh đúng địa điểm, đổi nhà xe xóa ID cũ và tải danh mục mới, báo cáo select, desktop và320/390/768px không tràn ngang, menu gap6px. Preview18145 chỉ template thật + API fixture trong RAM, có banner rõ; không là app nối DB hoặc bằng chứng deploy. PostgreSQL test được chủ dự án xác nhận riêng và đã dừng sau kiểm; không đọc root .env, không ghi DB hieuboy/Neon, không bật seed app preview. Nhánh chưa commit/merge/deploy; app Java cũ cần khởi động lại sau khi chọn đúng source.

## DUYLONG01 — dữ liệu và phát hành toàn bộ

Lệnh phát hành mới02/11/2026 cho phép gom toàn bộ DOCS03/ENVEX01/UI03/cấu hình/entity và bổ sung Duy Long qua nhánh riêng → dev → main → Render. Nhánh hieuv526034/duylong01-du-lieu-va-giao-dien tiếp nối source từ dev, không direct commit dev/main và không gán tác giả khác.

Thêm nhà xe DUYLONG, hai điểm Hải Phòng/Hà Nội, xe16 chỗ DLG16, Phạm Thành Lương/DLGLUONG, bốn mẫu giờ05:00–09:00/09:30–11:30/12:00–15:00/15:30–17:30 và ba account riêng. Lượt về sáng/biển số/liên hệ/tọa độ/giá là giả định được ghi rõ tại [local](../01-bat-dau/CHAY_LOCAL.md#nhà-xe-duy-long). Lịch theo giờ Việt Nam và ngày tạo nhà xe, không chồng xe/tài xế, giữ snapshot/mật khẩu/quyền đã thay đổi khi restart. Không thêm bảng/class hoặc sửa tám SQL baseline.

DB mới kiểm được46 account,7 nhà xe,25 xe/tài xế,12 tỉnh/14 địa điểm,28 mẫu tuyến,646 chuyến và3866 đơn. Bốn ca tập trung lịch/scope/đặt–xác nhận–thu tiền/idempotence đạt; toàn bộ Maven verify/WAR đạt203 Java, gồm70 PostgreSQL, không fail/error/skip. Các mốc201/68 ở UI03 là kết quả trước bổ sung Duy Long.

Trước thay đổi dữ liệu thật: đã backup nhất quán hieuboy.vietroute, mã hóa DPAPI CurrentUser, phục hồi vào PostgreSQL test loopback15461/vietroute_business_test. Đối chiếu đủ24 bảng, số dòng và digest đạt,0 ghi remote. Restore đầu thiếu pg_trgm trong namespace vietroute đã rollback; sau chuẩn bị đúng extension, restore đạt. Digest Linux/Windows ban đầu khác vì collation mặc định; cùng UTC/COLLATE C cho kết quả khớp, không sửa dữ liệu để vượt kiểm. Backup gắn tài khoản Windows, không coi là PITR/backup di động.

Tiếp theo: review/commit nhánh, tích hợp các PR154/155 liên quan, PR vào dev; chỉ sau check và Production CI mới merge main và xác nhận Render/HTTP/DB. Link PR, commit thực và kết quả Live sẽ ghi trong bàn giao PR phát hành; không coi kế hoạch này là đã deploy. Các PR Dependabot/nhánh cũ không thuộc phạm vi.

## Còn lại và bước tiếp

Nghiệm thu DOCS03/cấu hình qua nhánh riêng; PR154/155 cần review/tích hợp dev trước quyết định phát hành mới. Chưa có Oracle runtime/regression, kiểm tải/SLA hoặc backup di động/PITR/RPO/RTO; DPAPI local phụ thuộc tài khoản Windows. AccountChallengeRepo còn SQL phụ cũ chưa được luồng hiện hành gọi, cần dọn riêng khi sửa auth, không trình bày như chức năng đang hoạt động. Kiểm thêm cách chạy Maven Wrapper trên máy trước khi dùng hướng dẫn local.

Theo [quy trình Git](../../CONTRIBUTING.md): nhánh thành viên → PR/check/review → dev; main/deploy chỉ khi có lệnh mới. Routine test dùng DB cô lập, không root .env hoặc DB thật. Dữ liệu seed neo ngày lần đầu, muốn demo ngày mới thì tạo chuyến hợp lệ trên DB demo đã được phép, không reset DB thật.
