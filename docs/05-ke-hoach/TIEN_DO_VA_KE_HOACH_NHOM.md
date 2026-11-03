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

## UI03 — mốc kiểm local trước phát hành

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

Đã commit cec5f41 và mở [PR156 vào dev](https://github.com/hieuv526034/vietroute-web/pull/156). PR154/155 đã qua check và merge dev lần lượt tại5fc418f/e7941e5; tích hợp lại vào nhánh riêng giữ nguyên source đã kiểm, không phục hồi tài liệu/ảnh cũ. Kiểm format/rules/docs/diff sau tích hợp đạt. Render đã lưu và đối chiếu các cờ init/sync cùng ba key seed theo role; chưa chạy bản mới tại mốc ghi nhận này. Không đổi DB password/JWT hoặc mật khẩu account cũ.

Mốc chuẩn bị trên đã được thay bằng kết quả phát hành dưới đây. Các PR Dependabot/nhánh cũ không thuộc phạm vi, chưa có review nghiệm thu của thành viên khác.

### DUYLONG02 — bằng chứng main và Render

[PR156](https://github.com/hieuv526034/vietroute-web/pull/156) đã merge dev tại5ecbbb3 sau Java/format/docs đạt. [PR157 dev → main](https://github.com/hieuv526034/vietroute-web/pull/157) đã merge tại c706d0a sau đủ bốn checks, gồm [Production CI của PR](https://github.com/hieuv526034/vietroute-web/actions/runs/41444594467). [CI main](https://github.com/hieuv526034/vietroute-web/actions/runs/41445789297) cũng đạt:203 Java/70 PostgreSQL, Docker/Tomcat/home/assets, WAR không credential/test, bootstrap/restart, transaction pooler và recovery8 baseline. Không bypass gate hoặc Manual Deploy.

[Render deployment](https://dashboard.render.com/web/srv-dbcve0q9lscs73f81reg/deploys/dep-dbk89fuq1p3s73bhm6j0) báo Deploy succeeded | Live, đúng c706d0a, auto-deploy mất4m33s. Startup bổ sung nhà xe Duy Long và152 chuyến. DB hiện hành có46 account sử dụng,7 nhà xe,25 xe/25 tài xế,28 mẫu tuyến,646 chuyến và3878 đơn. Đây là nâng cấp giữ dữ liệu, không ép về3866 đơn của DB mới: các đơn WEB cũ giữ nguyên, chỉ bổ sung dữ liệu còn thiếu.

Đối chiếu chỉ đọc với bản sao đã phục hồi: đủ14 bảng nghiệp vụ giữ nguyên mọi dòng cũ, hash mật khẩu các account cũ khớp. Browser Live chọn Hải Phòng → Hà Nội/điểm Duy Long/ngày04/11 thấy hai lượt05:00–09:00 và12:00–15:00, giá180.000đ/chỗ và13 chỗ còn sau đơn giả lập. Bộ chọn lấy danh mục thật, chỉ một nút tìm; không có nút chuyển trang trong từng select. Cột sức chứa của chuyến vẫn16, chỗ còn là tồn sau đặt chỗ.

HTTP Live đạt46/46 account: username login → dữ liệu theo scope/owner → logout. Admin đọc /docs và /v3/api-docs200;45 account thường đều403 ở cả hai đích. Tài xế/điều hành Duy Long thấy đúng152 chuyến cùng nhà xe; khách Duy Long có2 đơn của mình. tokenVersion của cả46 account tăng trong hieuboy ở lượt này, xác nhận request Render thực sự dùng DB hiện hành. Hai chiều tìm công khai đều2 kết quả/ngày; kiểm đúng bốn khung giờ,16 chỗ và tên Phạm Thành Lương. Năm asset CSS/JS khớp checksum source đã phát hành.

Không tạo đơn hoặc thu tiền thật để kiểm production. Lượt OpenAPI đầu tiên timeout40s; lượt sau với timeout90s đạt, các lượt kiểm hoàn chỉnh không lỗi. Đã dùng page size1/tối đa hai phiên để kiểm count/quyền mà không tải thừa dữ liệu; không coi đây là kiểm tải/SLA. Dữ liệu/hash cũ đối chiếu trước các phiên login/logout; thay tokenVersion là tác động vòng đời đăng nhập có chủ đích. PostgreSQL test chứa bản phục hồi đã dừng sau nghiệm thu. Kết quả và link triển khai được bàn giao thêm trong PR157 và PR DUYLONG02.

## PERF04 — chẩn đoán độ trễ sau phát hành

Nhánh hieuv526034/perf04-do-tre-render-db, người thực hiện hieuv526034. Đã kiểm chỉ đọc ngày02/11/2026 sau phản ánh lag:10 GET công khai tuần tự/hai lượt, đối chiếu trace journal của chính các request và EXPLAIN ANALYZE SELECT trong transaction READ ONLY. Không đăng nhập mới, tạo đơn, load test, seed, đổi index/env/plan hoặc deploy. GET vẫn đi qua journal của ứng dụng như traffic thông thường.

Dashboard service srv-dbcve0q9lscs73f81reg xác nhận vùng thực **Virginia (US East)**; render.yaml đang ghi ohio, nên không dùng file này suy vùng của service hiện hữu. Cả hai đều ở Mỹ; DB hieuboy do chủ dự án cấp nằm ngoài Render. Hướng dẫn Render khuyên đặt app/datastore gần nhau; [đổi region cần service mới](https://render.com/docs/regions), không chỉ sửa YAML. Chưa chuyển vùng hoặc tạo tài nguyên.

| Mẫu đo | Kết quả |
|---|---|
| /health, không DB | 870–903ms từ máy kiểm |
| JS tĩnh | 389–871ms từ máy kiểm |
| HTML /journeys công khai | 330–888ms, không gồm tải danh mục bằng JS |
| GET /api/v1/provinces?size=100 | 2520–2541ms tải hết response; journal phần xử lý1256/1189ms |
| GET /api/v1/carriers?size=100 | 2501–2950ms tải hết response; journal phần xử lý1189ms |
| Sau header đến nhận hết response của bốn API | Khoảng935–937ms, kể cả response tỉnh chỉ912byte |
| SQL tỉnh, trực tiếp trên PostgreSQL | execution0,307ms; planning0,743ms |
| SQL tìm chuyến Duy Long count/page, hai điểm/ngày | execution1,323/1,139ms; planning11,302/15,895ms; page2 kết quả |
| SELECT1 trên connection tái sử dụng, máy kiểm → DB | 5 mẫu24,185–24,584ms; không phải RTT từ Render |
| pg_stat_activity tại một thời điểm | 3 connection idle/ClientRead, không thấy chờ khóa ở mẫu này |

Source giải thích phần độ trễ: mỗi transaction có LOCAL search_path, SQL count/page và commit; JWT/grant/scope trên route private có thêm các lượt đọc hiện hành. ApiJournalFilter gọi store.add đồng bộ ở finally; append có SELECT schema/INSERT/commit, duration_ms được chụp trước đoạn này nên không thể lấy duration_ms làm toàn bộ HTTP. Khoảng935–937ms sau header phù hợp với đường ghi journal đồng bộ; chưa instrument từng bước trên JVM Render để tách tuyệt đối network/driver/CPU. Metadata entity/index chỉ kiểm lúc startup. Query count/page là số statement cố định, không phải for query từng dòng trong các mẫu đã đọc.

Kết luận trong phạm vi mẫu: SQL server-side chưa giải thích được mức chờ nhiều giây; số lượt trao đổi DB qua mạng xa và journal đồng bộ là hai điểm cần ưu tiên. UI journeys đang chờ danh mục tỉnh xong mới gọi nhóm nhà xe/loại xe, cộng thêm thời gian chờ khi khởi tạo. Chưa đo CPU/GC/pool wait, route private hoặc app Java local; không khẳng định mọi màn có cùng nguyên nhân, không quy toàn bộ cho Render Free/cold start và chưa có SLA/p95/load test.

Bước sửa đề xuất: giảm round trip bằng query theo tập, tải các danh mục độc lập song song, giữ kiểm quyền/scope và transaction LOCAL; chọn hạ tầng app gần DB. Thay cách ghi journal cần giữ hợp đồng lưu bền/giới hạn lỗi, không tự thêm queue/cache RAM hoặc âm thầm bỏ log. PERF04 hiện chỉ chẩn đoán và ghi bàn giao, chưa sửa hành vi/commit/merge/deploy production.

## PERF05 — chuyển node Render sang Singapore

Chủ dự án đã cho phép tạo service Free ở Singapore với URL mới và đo độ mượt, giữ Virginia trong lúc kiểm. Nhánh hieuv526034/perf05-render-singapore, người thực hiện hieuv526034; giữ nguyên báo cáo PERF04 đang dở. render.yaml đổi tên đích thành vietroute-web-sg/region singapore; không đổi code Java/JS, SQL, mật khẩu hoặc DB.

Service mới dự kiến Docker từ main, Auto-Deploy After CI Checks Pass, health /health và build filters khớp source. Cấu hình DB/JWT sao chép trực tiếp trong bộ nhớ giữa hai form Render, không ghi credential vào docs/chat/file tạm. Bốn cờ init/sync/ordinary/business ở node mới tắt vì dùng DB đã có đủ dữ liệu; không migration/seed/reset production để chuyển vùng. Giữ tài khoản/quyền và JWT hiện hành. Chưa ghi nhận service mới Live hoặc kết quả nhanh hơn tại mốc chuẩn bị này; số đo/URL thực bổ sung trong bàn giao PR sau nghiệm thu.

Kiểm source/config qua nhánh riêng → dev → main theo lệnh chuyển vùng hiện hành; Production CI cần đạt trước phát hành thay đổi render.yaml. So sánh cùng tập GET tuần tự, giữ cold start tách biệt với lượt ấm; đọc journal của các trace tự tạo và kiểm đăng nhập/scope/Swagger trên Singapore. Không load test hoặc đặt/thu tiền thật. Chưa có review nghiệm thu từ thành viên khác.

## TEAM02 — phân công hai dev, tài liệu và kiểm thử

Theo yêu cầu mới02/11/2026: hieuv526034 phát triển auth/common/DB/cơ chế init/CI/deploy và báo cáo/dashboard; BangPhamIT phát triển catalog/carrier/trip/booking và UI tương ứng. anhhoang104 chủ trì báo cáo Word/nội dung/sơ đồ; hiencoi250404-design test thủ công, chụp ảnh chức năng và viết bảng kết quả, cùng hoàn thiện Word để nộp. Hai dev chịu code và test tự động. Đây là phân công từ mốc này, không sửa tác giả/lịch sử hoặc biến trách nhiệm mới thành đóng góp đã làm.

Nhánh hieuv526034/team02-hai-dev-tai-lieu-test, người thực hiện hieuv526034. Viết lại PHAN_CONG theo đầu ra/bàn giao/lỗi về hai dev; đồng bộ mục lục/tổng quan, kịch bản15 phút và Q45. Giữ bốn thành viên/username/email/anchor cùng quy trình nhánh riêng → dev, main chỉ khi có lệnh phát hành mới. Giữ ghi chú demo đa nhà xe đang có trong CHAY_LOCAL; Duy Long là một đơn vị. Cập nhật Q41 bằng link nghiệm thu Swagger/Singapore đã có tại PR157/161, không diễn giải thành lần deploy mới.

Theo yêu cầu bổ sung, chuyển trách nhiệm phát triển query/API/service/UI report/dashboard từ BangPhamIT sang hieuv526034; đồng bộ mục lục/tổng quan, bảng chuyển lỗi QA, Q45 và phần demo phút10–12. Đây là phân công tương lai, không sửa author hoặc nhận lại đóng góp cũ.

Làm rõ đầu ra hai thành viên tài liệu: Word/PDF, sơ đồ có nguồn sửa, ảnh chức năng theo role và bảng test thủ công. Bỏ yêu cầu Hien viết script/test tự động hoặc chạy regression DB; phần đó do hai dev phụ trách. Đồng bộ phân công, mục lục/tổng quan và Q45; chưa tạo hoặc tuyên bố đã hoàn thiện file Word.

TEAM02 chỉ thay tài liệu, chưa commit/merge hoặc thay source/API/DB/quyền/Render. npm ci/format/format:check/check:rules/check:docs/diff đạt; docs checker kiểm161 link trong22 tài liệu, giữ đủ bốn anchor thành viên. Không chạy lại Java/PostgreSQL hoặc image production vì không thay code. Không coi bảng phân công là thành viên đã kiểm hoặc nghiệm thu source.

## DATA02 — tên riêng cho nhà xe và tài xế giả lập

Nhánh hieuv526034/team02-hai-dev-tai-lieu-test, người thực hiện hieuv526034; giữ phần TEAM02 đang sửa. Đổi sáu nhãn nhà xe trong foundation.json/operations.json thành Thiên Hành, An Phúc, Thanh Vân, Đại Ngàn, Ngọc Hà và Trường Phong. Đổi24 tài xế sang tên nhân vật kiếm hiệp Kim Dung, đồng bộ displayName theo driverCode trong default-access.json và bảng tài khoản/local, ví dụ scope/demo. Duy Long/Phạm Thành Lương giữ nguyên. Ghi provenance dữ liệu giả lập; không đổi mã, username, email, key mật khẩu, role/scope, lịch, giá hoặc đơn. Không sửa tám SQL baseline hoặc thêm cơ chế startup ghi đè tên đã có.

Kiểm JSON đối chiếu HEAD xác nhận chỉ đổi tên/mô tả/provenance, đủ7 nhà xe/25 tên tài xế riêng và25 liên kết account-driver khớp. npm ci/format/format:check/check:rules/check:docs/diff đạt;161 link trong22 tài liệu. PostgreSQL loopback15461 chạy4 ca BusinessIntegrationTest: khởi tạo đầy đủ, idempotence/giữ thay đổi và scope đã thu hồi, bốn lượt Duy Long/ngày và luồng account/tìm/đặt/xác nhận/thu tiền đúng scope;4 đạt,0 lỗi/skip. Lượt đầu bị guard history chặn vì DB test cũ chứa bản phục hồi; giữ nguyên cụm đó, dùng cụm test mới, không sửa guard để xanh. Cụm mới đã dừng sau kiểm.

Chủ dự án đã xác nhận cho phép đổi nhãn trên hieuboy/vietroute. Ngày02/11/2026 đã sao lưu nhất quán schema bằng snapshot, mã hóa DPAPI CurrentUser và kiểm phục hồi đủ24 bảng/số dòng/digest khớp nguồn. Bản backup local before-data02-20261102223053.pgdump.dpapi, SHA-256 của archive trước mã hóa35f42d39b54ba73be62f81d6db96d8be174a496a2243016de517688a5b5d8cb7. Giữ bản phục hồi cũ riêng; không ghi dump rõ hoặc credential vào Git/docs/log.

Thử công cụ trên bản phục hồi: rollback đạt, áp dụng lần đầu đúng6 nhà xe/24 tài xế/24 tên account/494 snapshot chuyến seed, chạy lại0 thay đổi. Sau đó áp dụng trên DB hiện hành trong một transaction và đọc lại khớp6/24/24/494. Đồng bộ tên/mô tả nhà xe và tên scope vi/en; tăng version/audit system0 ở bản ghi đổi, thêm6 SCOPE_UPDATED và24 ACCOUNT_UPDATED chỉ ghi state whitelist. Đối chiếu24 bảng trong transaction giữ nguyên mọi cột/dòng ngoài nhãn và audit/version được phép; mật khẩu/tokenVersion, quyền/mapping, ID, mã, lịch, giá, đơn và Duy Long giữ nguyên. Không sửa các chuyến ngoài mã seed, không đổi history cũ hoặc reset dữ liệu. Cụm PostgreSQL kiểm phục hồi đã dừng. Source/tài liệu chưa commit/merge, không deploy lượt này.

Kiểm HTTP bổ sung: GET công khai /api/v1/carriers?size=100 trên Singapore bị read timeout trong phiên này, chưa xác nhận bảy tên qua API Live. Không suy timeout là lỗi query hoặc thay đổi dữ liệu; đối chiếu trực tiếp DB sau transaction đã đạt. Không gọi login hoặc tạo giao dịch để kiểm thêm. Cần kiểm lại HTTP/giao diện khi service phản hồi; không ghi bước này là đạt.

## DEMO03 — lời thoại bảo vệ sản phẩm cho hai dev

Người thực hiện hieuv526034, nhánh hieuv526034/team02-hai-dev-tai-lieu-test. Mở rộng DEMO_VA_HOI_DAP ngay trong bộ docs hiện hành: H1–H7 cho Hieu (nền/quyền và report/dashboard), B1–B8 cho Bang (dữ liệu nguồn/chuyến/tìm/đặt/xác nhận/thu–hoàn/tiến độ). Mỗi đoạn có lời nói, thao tác, kết quả mong đợi, câu chuyển và cách rút gọn; chuẩn bị nhiều phiên đúng role, chuyến/owner hợp lệ, nhánh đơn A/B tách biệt và số tiền đối soát theo chênh lệch dữ liệu nền. Bổ sung10 phản biện Q46–Q55, nâng bộ lên55 câu; đồng bộ mục lục/phân công và điểm mở source. Anh/Hien giữ vai trò Word/sơ đồ/ảnh/test thủ công.

Đối chiếu nội dung với BookingService, TripSegmentRepo, DriverTripService, ReportRepo/ReportSummaryResponse, quyền/scope và quy ước UI. npm ci/format/format:check/check:rules/check:docs/diff đạt;177 liên kết trong22 tài liệu. Kiểm đủ55 câu liên tiếp không trùng,7 đoạn Hieu/8 đoạn Bang và ví dụ tính tiền khớp. Nội dung là kịch bản tập trình bày, chưa thực hành sân khấu hoặc tạo dữ liệu chuẩn bị mới, không ghi demo mong đợi thành đã đạt. Không chạy lại Java/DB/Render cho thay đổi tài liệu này; giữ nguyên giới hạn HTTP DATA02 phía trên. Chưa commit/merge/deploy hoặc tạo Word sản phẩm.

## WORD01 — báo cáo Word đầy đủ theo mẫu

Theo lệnh soạn trọn bộ ngày02–03/11/2026, người thực hiện hieuv526034 trên nhánh hieuv526034/team02-hai-dev-tai-lieu-test; giữ TEAM02/DATA02/DEMO03 đang sửa. Đã dựng [Word Nhóm10](bao-cao/Bao_cao_VietRoute_Nhom_10.docx),80 trang,42 hình gồm12 sơ đồ tự vẽ và30 ảnh chức năng local,39 bảng gồm từ điển24 bảng DB. Kèm [tư liệu SVG/PNG và ảnh gốc](bao-cao/Tu_lieu_VietRoute_Nhom_10.zip). Giữ hai bìa, ba chương, mục lục/danh mục tự động, kết luận/tham khảo và phụ lục. Theo yêu cầu chữ trong mẫu: A4, Times New Roman13, lề2,5/2/3/2cm, giãn1,3; sửa các geometry lưu sẵn lệch chỉ dẫn. Mẫu gốc giữ SHA-256 bd8fc0707ebadb9373d988f28bc7d4b036542f6bb8a662191228f410ca5089c6. Chỉ còn11 loại ô [[...]] cho giảng viên/lớp/khóa và4 họ tên/MSSV chưa xác nhận; không tự tạo danh tính.

Chạy Java/Tomcat thật cổng18155 với PostgreSQL demo mới loopback15461, schema vietroute_report01, env riêng không đọc root .env hoặc DB thật. Seed đủ46 account/7 nhà xe/25 xe–tài xế/646 chuyến/3866 đơn. Qua UI: admin xem quản trị/quyền/Swagger; khách customduylong hủy PENDING cũ hợp lệ, tạo VR3867 hai chỗ360.000đ; operatorduylong xác nhận, DB kiểm segment capacity16/reserved5 (3 nền+2 mới); admin ghi thêm10.000đ VR3863, history/paid từ125.000 thành135.000đ. Driver đọc152 chuyến đúng assignment và lịch sử seed; báo cáo Duy Long ngày04/11 đối chiếu đủ các chỉ tiêu tiền. Không gọi lịch sử khởi tạo là chuyến vừa chạy thật. Ảnh và Word không chứa password/JWT/dump, chỉ dữ liệu giả lập.

Trong lúc thao tác phát hiện và sửa hai lỗi UI: journeys.js lấy nhầm access.view của trang công khai nên giấu nút đặt của khách đủ grant; đổi sang kiểm GET /my-bookings + POST /api/v1/my-bookings. api-journal.js dùng target cũ thiếu /system; dùng đúng GET theo route hiện hành. Không đổi quyền backend, policy hoặc tự cấp grant. Regression Node có ca đủ/thiếu quyền đặt, journal đúng endpoint và page/target cũ không tự cho đọc. Đã thực hành lại UI đặt/xác nhận và journal/export sau sửa.

Maven/JDK21 với PostgreSQL test riêng loopback15462:203 test,0 failure/error/skipped, gồm70 BusinessIntegrationTest. Node cuối70/70 đạt. npm ci/format/format:check/check:rules/diff đạt. Native Word đã cập nhật TOC/SEQ/PAGE và xuất PDF local; rà ảnh toàn bộ80 trang, sửa ngắt trang/ảnh/tab báo cáo, kiểm bản cuối không field lỗi hoặc ví dụ Student cũ, sáu section đúng geometry. render_docx.py đã thử/chẩn đoán thiếu LibreOffice trên Windows nên dùng Word có sẵn và Poppler để kiểm in; không bỏ bước render. App và hai cụm PG riêng đã dừng sau kiểm, không ảnh hưởng DB/runtime khác. File mới stage để checker đọc Git; chưa commit/merge/deploy, chưa được thành viên/giảng viên nghiệm thu. Khi sửa bìa/nội dung cần cập nhật field và rà lại bản in.

## RELEASE02 — phát hành tài liệu và sửa quyền UI

Ngày03/11/2026 chủ dự án yêu cầu rà lại, commit toàn bộ phần đang hoàn thiện qua dev → main và deploy Render. Người thực hiện hieuv526034, nhánh hieuv526034/team02-hai-dev-tai-lieu-test. Phạm vi TEAM02/DATA02/DEMO03/WORD01: phân công hai dev, bộ trình bày55 câu, Word80 trang cùng tư liệu, nhãn nhà xe/tài xế giả lập và hai sửa lỗi client journeys/journal. Không đổi schema, credential hoặc seed lại DB thật. Mẫu Word gốc giữ nguyên; các ô danh tính chưa có vẫn cần thành viên điền.

Rà diff và chạy lại npm ci/format/format:check/check:rules/check:docs/70 Node đều đạt; giữ bằng chứng203 Java, gồm70 PostgreSQL cô lập của WORD01 vì source Java không đổi sau lượt đó. Production CI của PR dev → main phải kiểm lại Java/PostgreSQL/Docker/recovery trước merge. Dashboard Singapore vẫn báo bản7ef6025 Live; link cũ là Virginia, không tự trỏ sang service mới. Cập nhật hướng dẫn vận hành URL Singapore để tránh nhầm. Chưa suy nhãn Live cũ thành nghiệm thu HTTP hoặc deployment mới; kết quả PR/check/HTTP và commit phát hành sẽ bổ sung sau thao tác thực tế. Chưa có review nghiệm thu của thành viên khác.

### RELEASE02 — kết quả phát hành và nghiệm thu Live

[PR162](https://github.com/hieuv526034/vietroute-web/pull/162) đã merge vào dev tại3cfd16d sau Java/formatter/docs đạt. [PR163 dev → main](https://github.com/hieuv526034/vietroute-web/pull/163) merge tại2a5f1c0 sau đủ bốn checks; [Production CI của PR](https://github.com/hieuv526034/vietroute-web/actions/runs/41491964639) và [CI main](https://github.com/hieuv526034/vietroute-web/actions/runs/41493543558) đều đạt203 Java, gồm70 PostgreSQL,0 fail/error/skip, Docker/Tomcat/assets, WAR không credential/test, bootstrap/JWT/restart/pooler/recovery. Đã rà đúng16 file, không lẫn .env hoặc SQL baseline;70 Node local đạt. Word/tư liệu và các thay đổi TEAM02/DATA02/DEMO03/WORD01 đã có trên dev/main qua hai PR này.

[Render Singapore deployment](https://dashboard.render.com/web/srv-dbk9elou01pc739ei3ug/deploys/dep-dbkcpbtckfvc73bdp660) xác nhận Deploy succeeded | Live, source2a5f1c0, Auto-Deploy3m47s sau CI main. Không Manual Deploy hoặc bỏ gate. URL dùng: [VietRoute Singapore](https://vietroute-web-sg.onrender.com/journeys?lang=vi). Link vietroute-web.onrender.com vẫn thuộc Virginia cũ, hai lượt health timeout55s/20s; không kết luận service cũ deploy lỗi chỉ từ timeout. Singapore lượt đầu cũng timeout55s khi Tomcat vừa khởi động; sau đó health/API tỉnh trả200, tỉnh khoảng0,36s, trang tìm chuyến tải đủ danh mục và tìm Hải Phòng → Hà Nội ngày04/11 thấy nhiều nhà xe, hai lượt Duy Long05:00–09:00 và12:00–15:00 đúng180.000đ/chỗ. Không đổi địa chỉ, region, env hoặc gói của service cũ trong lượt này.

Sau deployment mới: health200, journeys.js/api-journal.js/app.css200 và checksum khớp source. Public API có đủ7 nhà xe với tên mới. HTTP login/read/logout đạt4/4 account admin/customduylong/driverduylong/operatorduylong: admin đọc46 account và journal200, khách chỉ2 đơn riêng, driver/operator đúng152 chuyến Duy Long. /docs và /v3/api-docs admin200, cả ba role thường403; khách gọi danh sách account quản trị403. Chỉ đăng nhập/đăng xuất và GET, không đặt đơn/thu tiền, reset, migration hoặc seed production; vòng đời phiên và journal có ghi bình thường. Không đổi mật khẩu/JWT/DB config.

Giới hạn: /v3/api-docs admin lần đầu sau restart mất51,3s; health và ba asset sau đó khoảng0,20–0,22s trong một lượt đo, không phải p95/SLA hoặc kiểm tải. Bộ kiểm ban đầu gửi Accept application/json cho health text/plain nên406; sửa đúng Accept rồi kiểm lại đạt, không sửa ứng dụng để né phép kiểm. Free/cold start và OpenAPI đầu còn chậm; không tuyên bố mọi màn mượt hoặc không mất trace journal. Bằng chứng nghiệm thu ghi trên nhánh hieuv526034/release02-nghiem-thu-render qua PR/check trước khi nhập dev/main; phần này chỉ tài liệu, không kích hoạt image/deploy mới. Chưa có review nghiệm thu thành viên khác hoặc giảng viên.

## JWTCHECK01 — đối chiếu bước bootstrap/JWT bị đỏ

Ngày03/11/2026, hieuv526034 trên nhánh hieuv526034/jwtcheck01-doi-chieu-ci rà chỉ đọc15 lần Production Docker gần nhất theo phản ánh GitHub. Lần lỗi tìm được là [run41405035171/job125145026995](https://github.com/hieuv526034/vietroute-web/actions/runs/41405035171/job/125145026995), source8700797 ngày02/11: node thứ nhất kiểm sau restart đạt, node thứ hai báo Connection refused; log Docker xác nhận No such container: vietroute-auth-ci2. Script đã xóa node thứ hai trước khi gọi auth-smoke ở port18081, nên request chưa tới bước xác thực JWT. Không phải lỗi chữ ký/claim JWT được ghi nhận trong lần này.

[Bản sửaff19ec1](https://github.com/hieuv526034/vietroute-web/commit/ff19ec1c6ce2259af52adc255e9a7e73a7373a37) đã giữ cả hai container, restart và đợi health từng port trước smoke; đã nằm trong source Live2a5f1c0. [Run main hiện hành/job125442457916](https://github.com/hieuv526034/vietroute-web/actions/runs/41493543558/job/125442457916) xác nhận riêng bước Verify bootstrap and JWT flows with isolated PostgreSQL completed/success; log có hai lượt Auth restart đạt, pooler và recovery đạt. Source auth-smoke kiểm login/Bearer, token cũ401 sau đổi password, thu hồi menu/API403 và xóa account401. Giữ kết quả kiểm Live bốn role của RELEASE02 phía trên, không giả thành lượt login mới trong JWTCHECK01.

Chỉ đọc GitHub/log/source và bổ sung bàn giao; không đổi code JWT/CI/env/secret/DB, không rerun workflow, commit/merge hoặc deploy. Lỗi lịch sử vẫn hiện đỏ đúng trạng thái của lần chạy cũ; không sửa/xóa lịch sử để che kết quả. Kết luận trong phạm vi15 run đã đọc; nếu phản ánh là run khác cần đối chiếu đúng URL đó.

## MENU04 — bỏ danh mục dẫn về Tổng quan và link Địa điểm trùng

Ngày03/11/2026, hieuv526034 trên nhánh hieuv526034/menu04-bo-danh-muc-thua, từ devd75332c; giữ bàn giao JWTCHECK01 chưa commit. Xác định MENU GET /admin tên Danh mục vẫn mở auth/dashboard nên lặp với /dashboard. PageModelAdvice bỏ mục /admin khi dựng menu đã lưu, bỏ cờ catalogAllowed không dùng; seed default-access.json bỏ đúng hai MENU GET /admin và GET /operations/locations, mọi dữ liệu seed khác giữ nguyên. Không sửa controller/API/quyền backend, không xóa mapping DB hoặc migration.

Kiểm UI còn thấy hai mục Địa điểm từ /catalogs/locations và /operations/locations. Chỉ bỏ link cũ khi tập menu được cấp đã có link hiện hành; không tạo quyền/link mới cho tài khoản chỉ được cấp link cũ. Breadcrumb fallback theo link thực sự được cấp. Cập nhật quy ước UI cùng cụm; không tạo file/class mới.

Maven3.9.11 cache/JDK21 chạy PageBreadcrumbsTest và AuthNavigationTest:6 test,0 fail/error/skip, có root/servlet tách biệt và context WAR/locale. So JSON với HEAD chứng minh chỉ bỏ hai menu nêu trên. Bật lại DB demo loopback15461/schema vietroute_report01 và app18155 bằng env fixture riêng: DB vẫn còn MENU /admin (count1), giao diện header/dashboard không còn link này, Địa điểm không trùng; đủ4 danh mục ở vi/en. Bấm Loại xe mở /catalogs/vehicle-types, đọc3 loại xe, breadcrumb đúng. Không root .env/DB thật, không CRUD nghiệp vụ. Lần gọi mvnw.cmd gặp lỗi wrapper local đã nêu trước; dùng Maven cache với repo.local tường minh, không sửa wrapper/rule để xanh. npm ci/format/format:check/check:rules/check:docs/diff đạt. Không chạy lại toàn bộ203 Java/PostgreSQL vì chỉ thay dựng menu/seed metadata; chưa commit/merge/deploy Render. Dừng app/DB demo riêng sau kiểm; chưa có review nghiệm thu thành viên.

## CATEGORY01 — danh mục chung, cây tỉnh/xã và chọn vị trí

Người thực hiện hieuv526034 trên nhánh `hieuv526034/category01-danh-muc-chung` từ dev d75332c. Source catalog từ25 file/3 bảng còn10 file/1 bảng, gồm CategoryRefType cho năm mã nền. Toàn business106 file/12 bảng; auth40/common61 giữ số file. Đã bỏ các entity/repo/service/DTO, page/API/JS/template danh mục riêng; bộ chọn nghiệp vụ dùng Category. `type` dùng GROUP/VALUE, `ref_type` bắt buộc, có `id`, `parent_id`, `description` vật lý và props JSONB. Số chỗ gợi ý của loại xe không thay sức chứa xe/chuyến.

Commit source `f45da17` đã push lên nhánh thành viên, [PR166 vào dev](https://github.com/hieuv526034/vietroute-web/pull/166) đang draft/chờ CI và review thành viên. Chưa merge dev/main; bản Word/tư liệu trong PR là bản cập nhật Category, không phải bằng chứng production.

Thu thập34 tỉnh/thành và3.321 xã/phường, đối chiếu mã/cha với Cục Thống kê; chuẩn hóa17 khác biệt tên giữa nguồn API/bảng đối chiếu. Theo yêu cầu chốt, giữ JSON trong bootstrap có ngày/URL/checksum, không thêm tác vụ ngầm hoặc đồng bộ định kỳ. Khi bật init, hai INSERT theo tập bổ sung mã thiếu từ JSON; mã đã có được bỏ qua, không gọi API ngoài hoặc ghi đè trạng thái/nội dung đã sửa. Hành chính hai cấp; địa điểm đón/trả là lá nghiệp vụ dưới xã/phường. Search chuyến hỗ trợ fromWardId/toWardId; query tuyến/chuyến vẫn lấy đúng tỉnh cho snapshot qua xã. UI mở từng cha lấy20 con, Xem thêm theo nhánh, không tải cả cây.

Leaflet1.9.4 đóng bằng WebJar, chỉ mở bản đồ mới tải tile OpenStreetMap có attribution. Bấm/kéo ghim hoặc dùng bàn phím chọn tâm tự điền tọa độ sáu số lẻ; không đọc GPS hoặc tự geocoding. Đã kiểm UI local: bảng cây tỉnh→phường→hai điểm Hải Phòng, chỉnh mô tả/chọn tâm/lưu/mở lại đúng giá trị; tuyến HALONGVINH hiện đúng tỉnh/xã/địa điểm; tìm ngày04/11 toàn tỉnh có5 kết quả của3 nhà xe, chọn phường/điểm Duy Long còn đúng2 lượt05:00–09:00 và12:00–15:00. Swagger và inventory chỉ còn nhóm Category, page/endpoint cũ không đăng ký.

Maven3.9.11 cache/JDK21 `clean verify` đạt207 Java, trong đó72 PostgreSQL,0 fail/error/skip; PostgreSQL test loopback15462, không root .env. Node đạt70/70. npm ci/format/format:check/check:rules/check:docs đã chạy; checker sau stage đạt263 file Java/187 liên kết trong22 tài liệu. Tám SQL nền giữ nguyên byte; thêm catalog/002_category.sql và003_location_wards.sql, tổng10 bước/22 bảng. Bản backup mã hóa trước chuyển đã kiểm phục hồi24 bảng/digest khớp; diễn tập cả hai bước trên bản sao v4 đạt rollback, đối chiếu giữ các giá trị cũ ngoài remap FK, history10 và typed FK từ chối GROUP. Không ghi DB thật trong các phép thử. Bản mã hóa DPAPI local vẫn có giới hạn di chuyển máy đã nêu ở vận hành.

[Word](bao-cao/Bao_cao_VietRoute_Nhom_10.docx) được cập nhật nội dung Category, ERD, ảnh danh mục/tuyến/tìm chuyến/Swagger/inventory, thêm ảnh chọn bản đồ và từ điển22 bảng. Native Word đã cập nhật field/TOC và xuất PDF để rà bố cục;81 trang,43 hình nội dung,37 bảng. Giữ11 loại ô bìa chưa xác nhận và byte mẫu gốc. [Tư liệu](bao-cao/Tu_lieu_VietRoute_Nhom_10.zip) thay ảnh/ERD liên quan, chỉ SVG/PNG. Không có credential/dump/config local trong gói.

Diễn tập bản sao v4 kiểm thêm chuyển quyền trong bước003: bỏ target danh mục cũ theo whitelist, bổ sung bảy grant Category vào ADMIN_CONSOLE hiện hữu và ghi history cùng transaction. Không gán role/user hoặc bật lại trạng thái đã thu hồi; không chuyển grant đọc một loại danh mục cũ thành quyền đọc mọi nhóm. Regression riêng kiểm function/role/mapping inactive giữ nguyên, password/tokenVersion không đổi, function version/history tăng đúng.

Chưa áp dụng Category lên hieuboy, chưa merge dev/main hoặc deploy Render. Cutover cần dùng đúng bản mới, dừng mọi node và backup/kiểm phục hồi mới ngay trước đổi DB; không để code cũ chạy trên schema mới. Địa điểm ngoài bộ map giả lập làm bước003 dừng để chốt cha, không đoán theo tọa độ. Grant ngoài bộ quản trị cần review/gán riêng.

Đổi tên service Virginia không đổi hostname, đã trả lại tên cũ. Yêu cầu chuyển `vietroute-web.onrender.com` sang Singapore được Render AI phản hồi hostname cố định theo service; sau yêu cầu nhân viên, chat đã xác nhận escalated to the team và sẽ trả lời tại chat/email. Chưa có xác nhận chuyển hostname; giữ hai service hiện hành trong lúc chờ. Không coi việc gửi yêu cầu là đã chuyển thành công.

## JDBCLEAN01 — bỏ metadata JDBC riêng khỏi entity

Theo yêu cầu03/11/2026, người thực hiện hieuv526034 tiếp tục trên nhánh `hieuv526034/category01-danh-muc-chung`, đối chiếu commit8fbc025/PR166. Bỏ annotation riêng khỏi21 model và xóa ba lớp JdbcTable/JdbcIndex/JdbcSchemaValidator. Giữ annotation JPA chuẩn; bổ sung các index auth_users vào @Table. Common còn58 file, jdbc6; toàn ba khối production204 file, auth40/business106 giữ nguyên. Không đổi bảng, dữ liệu hoặc byte mười SQL/checksum hiện hành.

DatabaseInitializer giữ cờ opt-in, chuẩn bị schema/LOCAL search_path, advisory lock, transaction, history/checksum, auth/init/cleanup và nạp Category từ JSON. Bỏ danh sách entity/lệnh validator trước seed. PK/index chuyển sang hai regression trong BusinessIntegrationTest dùng metadata JPA chuẩn; bốn PK nghiệp vụ ghép auth kiểm tường minh, không nhầm với ID kỹ thuật UNIQUE. Kiểm âm tính bỏ index, sai thứ tự/cột hoặc PK và rollback phát hiện đúng. Không tạo validator/annotation thay thế trong production. Runtime không còn quét PK/index; checksum file không thay thế kiểm DB bị sửa tay.

JDK21/Maven3.9.11 cache `clean verify` đạt209 test, gồm74 PostgreSQL,0 fail/error/skip trên loopback15462/vietroute_business_test; không root .env/DB thật. Có khởi tạo mới, chạy lại giữ credential/dữ liệu, từ chối checksum/history sai và các ca JPA/JDBC/quyền/nghiệp vụ hiện có. Lượt đầu DB test đang tắt, lệnh bật thiếu tham số cổng; đã bật lại đúng cụm/cổng trước kiểm. Ca kiểm sau rollback cần chọn lại LOCAL schema, đã sửa test rồi clean build lại. Không sửa rule/formatter hoặc bỏ ca kiểm để đạt.

npm ci/format/format:check/check:rules/check:docs/diff đạt; checker260 file Java/187 liên kết trong22 tài liệu. Đồng bộ AGENTS, tổng quan, quy ước backend, schema và Word theo cơ chế mới. Native Word xuất PDF vẫn81 trang; kiểm các trang thay đổi không tràn/cắt nội dung. WAR không còn ba class đã xóa. Cụm PostgreSQL test đã dừng sau kiểm. Không sửa ảnh/ERD vì DDL/nghiệp vụ không đổi. Chưa merge dev/main, chưa đổi DB thật hoặc deploy Render; phần formatter IDE có sẵn vẫn giữ ngoài thay đổi này.

## AUTHCHECK01 — kiểm đăng nhập custom01 trên Render

Theo yêu cầu03/11/2026, kiểm riêng custom01 tại https://vietroute-web-sg.onrender.com. Đối chiếu BCrypt chỉ đọc trên DB hiện hành xác nhận khớp cấu hình seed khách; account active/verified, không deleted/pending/khóa và có role toàn cục. Không in hoặc lưu password/hash/token/cookie. Kiểm HTTP phiên riêng: CSRF200, login200/OK001, me200 đúng custom01, API đơn cá nhân200 với3 đơn, trang dashboard và my-bookings đều200. Không đổi mật khẩu/quyền hoặc tạo giao dịch; login có cập nhật phiên/audit theo luồng ứng dụng đã được yêu cầu thử. Chưa tái hiện lỗi trong browser của chủ dự án; cần đúng URL và thông báo lỗi nếu còn xảy ra. Các account khác chưa được kiểm đăng nhập từng tài khoản trong lượt này.

## QUERY01 — native @Query theo schema, giữ số file

Theo hai lựa chọn của chủ dự án ngày03/11/2026: dùng @Query cho phần tra cứu/CRUD phù hợp, giữ JDBC cho batch/khóa phức tạp; chỉ chuyển phần không cần thêm Custom/Impl. Người thực hiện hieuv526034 tiếp tục trên nhánh `hieuv526034/category01-danh-muc-chung`, nền647e723/PR166; giữ phần AUTHCHECK01 có sẵn. Chuyển FunctionRepo, FunctionResourceRepo, RoleFunctionRepo và TripStopRepo thành interface Spring Data thật,13 @Query native +1 countQuery. Không tạo file Java hoặc query executor riêng: auth40/business106/common58, tổng204 file production giữ nguyên. Các Repo còn cần implementation riêng không chuyển toàn bộ sang @Query.

JpaConfig bật @EnableJpaRepositories và chọn EntityManager hiện hữu là @Primary để tránh xung đột shared EM do Spring Data tạo. Query native viết text block đầy đủ, named param có @Param, dùng {h-schema} từ hibernate.default_schema cùng cấu hình schema connection. Query grant/menu giữ điều kiện tài khoản/role/function hiện hành và trần all_scopes kể cả role inactive. @Modifying cập nhật điểm chuyến giữ scope/carrier/CAS, service vẫn khóa trip và kiểm tài xế; batch/history JDBC giữ cùng transaction, không commit riêng. Fixture quyền tách context đổi theo interface; kiểm một-query cho cả tập grant bằng Hibernate statistics trong test thay vì bộ đếm chỉ thấy PreparedStatement thủ công.

Thêm exporter phát triển đọc chính các native @Query trong source, xuất14 SQL gồm count đã thay schema, giữ :thamSo để DB editor bind. Không đọc .env/kết nối/thực thi SQL hoặc lưu credential. Có kiểm UTF-8 qua pipe Windows, bỏ comment/JPQL, ngoặc trong SQL và chặn schema injection. SQL sinh ở target, không giữ bản SQL chép tay thứ hai. JDBC còn lại tiếp tục dùng LOCAL search_path theo schema cấu hình; Oracle vẫn chặn runtime.

JDK21/Maven3.9.11 cache clean verify đạt210 test, gồm75 PostgreSQL,0 fail/error/skip; Node73/73. PostgreSQL test loopback15462, không root .env/DB thật. Mười ca trọng yếu và ca schema mới đã chạy trước regression tổng: schema random vẫn đọc đúng khi search_path chỉ pg_catalog/pg_temp; JPA đọc được mapping JDBC chưa commit và cùng rollback; quyền hai context, thu hồi, grant theo lô, cạnh tranh/rollback tiến độ đạt. Lượt đầu phát hiện hai bean EntityManager cùng type, đã sửa chọn @Primary rồi chạy lại, không bỏ test/rule.

Rà SQL kiểm release phát hiện tên cột/index Category cũ: đồng bộ item_ref thành value_ref và idx_catalog_* thành idx_category_*, giữ nguyên số assertion. Đối chiếu bốn count trên preview PostgreSQL test:5 giá trị nền/34 tỉnh/3321 xã/5 index audit. Chưa chạy production Docker hoặc dispatch release. Mười SQL migration/checksum giữ nguyên byte, không đổi schema/quyền/mật khẩu/dữ liệu thật. Đồng bộ AGENTS, quy ước backend/schema và Word; ảnh/ERD giữ vì UI/DDL không đổi. npm ci/format/format:check/check:rules/check:docs/diff đạt; checker260 Java/189 liên kết trong22 tài liệu. Chưa merge dev/main hoặc deploy; formatter IDE có sẵn giữ ngoài commit.

## SQLSTYLE01 — query dễ copy và quy ước JDBC chỉ ghi batch

Theo yêu cầu03/11/2026, người thực hiện hieuv526034 tiếp tục nhánh `hieuv526034/category01-danh-muc-chung`, nền078bb4b/PR166. Chốt quy ước query nghiệp vụ tra cứu/CRUD/khóa dùng JPA/Hibernate/Spring Data; JDBC nghiệp vụ chỉ ghi batch/bulk. Các class Repo có implementation riêng có thể dùng EntityManager/native SQL mà không thêm Custom/Impl. DDL/schema/transaction và journal pool riêng là hạ tầng JDBC. Ngoài bốn Repo QUERY01, source còn query JDBC cũ; phạm vi chuyển toàn bộ ngay hay theo từng nghiệp vụ đang chờ chủ dự án chốt, chưa ghi nhận đã chuyển hết.

Bổ sung exporter để người mới dùng: bản template14 SQL có schema/named params và phân trang Pageable rõ ràng; --example tạo11 SELECT đã điền giá trị mẫu, BEGIN READ ONLY/ROLLBACK, bỏ mutation và SELECT giữ khóa. Có --user-id/--page-size/--page; giữ literal/comment khi bind, giải thích NULL/% và page từ0. Không đọc .env, kết nối DB hoặc tự thực thi SQL. Không đổi Java production/API/DDL/dữ liệu thật trong lượt này.

Node74/74 đạt, gồm ca xuất SQL mẫu/phân trang/literal và loại mutation/khóa; đã chạy cả11 SQL ví dụ trên preview PostgreSQL loopback15462/vietroute_category_preview_v4, schema vietroute_category01, bằng transaction chỉ đọc và rollback: exit0. Cụm test đã dừng sau kiểm. Bộ Java210/75 PostgreSQL của QUERY01 vẫn là bằng chứng gần nhất, không chạy lại vì không sửa Java. Đồng bộ quy ước/tài liệu và kết quả Node trong Word, render81 trang và rà trang thay đổi. npm ci/format/format:check/rules/docs/diff đạt; checker260 Java/189 liên kết trong22 tài liệu. Không merge dev/main hoặc deploy.

## TRANSACTION01 — SQL viết hoa và ranh giới giao dịch

Theo yêu cầu03/11/2026, hieuv526034 tiếp tục nhánh `hieuv526034/category01-danh-muc-chung`, nền4724404/[PR166](https://github.com/hieuv526034/vietroute-web/pull/166), chưa merge dev/main. Chuẩn hóa111 chuỗi SQL/JPQL trong19 file: viết HOA từ khóa/hàm/kiểu dữ liệu, giữ nguyên literal, identifier có quote và tên tham số. Đối chiếu từng chuỗi trước/sau chỉ khác kiểu chữ; mười SQL migration/checksum giữ nguyên. Không thêm class production, auth40/business106/common58.

JdbcTransactions dùng TransactionTemplate REQUIRED qua manager cả khi đang có giao dịch; callback con lỗi đánh dấu rollback-only dù caller bắt lỗi. Các default method Repo đọc theo tập đặt @Transactional tại hàm ngoài qua proxy, query con không dựa annotation tự gọi. FunctionRepo.lock/TripStopRepo.mark dùng MANDATORY, thiếu transaction service bị chặn trước SQL. Giữ cùng connection JPA/JDBC, thứ tự khóa/CAS và history rollback, không self-injection/AopContext hoặc mở transaction riêng để nhả khóa sớm.

Nhóm tập trung13/13 đạt; clean verify214/214 Java, gồm77 PostgreSQL và4 H2 JPA,0 fail/error/skip, WAR thành công. Ca mới kiểm nested callback thành công không commit sớm; lỗi con đã bắt vẫn rollback JPA/JDBC, cùng transaction ID thực ở PostgreSQL; khóa/tiến độ ngoài transaction bị chặn và default method đọc gọi qua proxy vẫn chạy. Node74/74 đạt; cả11 SELECT ví dụ viết hoa chạy trên preview PostgreSQL loopback15462 bằng READ ONLY/ROLLBACK, exit0. Cập nhật quy ước backend/AGENTS và Word; không đổi DDL/API/UI/quyền/mật khẩu hoặc ghi DB thật. Oracle và chuyển nốt Repo JDBC cũ vẫn là phần chưa nghiệm thu; không merge dev/main hoặc deploy.

## Còn lại và bước tiếp

Source DUYLONG01 đã có trên dev/main và Live theo bằng chứng trên; DUYLONG02 chốt tài liệu nghiệm thu. Chưa có Oracle runtime/regression, kiểm tải/SLA hoặc backup di động/PITR/RPO/RTO; DPAPI local phụ thuộc tài khoản Windows. AccountChallengeRepo còn SQL phụ cũ chưa được luồng hiện hành gọi, cần dọn riêng khi sửa auth, không trình bày như chức năng đang hoạt động. Kiểm thêm cách chạy Maven Wrapper trên máy trước khi dùng hướng dẫn local.

Theo [quy trình Git](../../CONTRIBUTING.md): nhánh thành viên → PR/check/review → dev; main/deploy chỉ khi có lệnh mới. Routine test dùng DB cô lập, không root .env hoặc DB thật. Dữ liệu seed neo ngày lần đầu, muốn demo ngày mới thì tạo chuyến hợp lệ trên DB demo đã được phép, không reset DB thật.
