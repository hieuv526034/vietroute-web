# Nghiệm thu nền tảng và nghiệp vụ

Cập nhật 24/10/2026, đối chiếu main tại `7c53bf6` sau [PR #92](https://github.com/hieuv526034/vietroute-web/pull/92). Phạm vi là source nền tảng và UI quản trị trên Spring MVC/JDBC hiện tại. Đọc cùng [quản trị hệ thống](../04-van-hanh/QUAN_TRI_HE_THONG.md), [tiến độ nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md) và [vận hành](../04-van-hanh/GIAM_SAT_VA_VAN_HANH.md). Kết quả trên nhánh chức năng chỉ trở thành bản bàn giao main sau khi PR qua CI và merge. Các mục bằng chứng phía dưới giữ mốc lịch sử; hợp đồng bootstrap hiện tại theo AUTH17, schema theo AUTH19 trong [chuyên đề phân quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md).

Phần nền tảng bên dưới giữ phạm vi/mốc lịch sử trước BUSINESS04. Đối chiếu nghiệp vụ hiện hành ngày 25/10 ở [ACCEPT01](#đối-chiếu-ca-nghiệp-vụ--accept01); bảng quyền và trạng thái ba account/Render xem tiến độ. Không dùng câu “chưa triển khai nghiệp vụ” tại mốc cũ để suy trạng thái source hiện tại.

## Báo cáo sản phẩm theo mẫu Công nghệ Java

Ngày 31/10/2026, chủ dự án chỉ định mẫu Word `docs/xxx-tai-lieu-thi/Mau_BTL_Cong_nghe_Java.docx` làm format cho tài liệu sản phẩm VietRoute. Giữ bản mẫu nguyên trạng; SHA-256 khi đọc là `bd8fc0707ebadb9373d988f28bc7d4b036542f6bb8a662191228f410ca5089c6`. Mẫu nằm trong thư mục local do chủ dự án cung cấp, chưa theo Git; không dùng một đường dẫn máy cá nhân làm nguồn thay thế khi bàn giao. Đây là báo cáo bài tập lớn học phần Công nghệ Java, khác với hướng dẫn vận hành/API hiện có.

### Cấu trúc giữ từ mẫu

| Phần | Nội dung áp dụng cho VietRoute |
|---|---|
| Bìa ngoài | Cơ quan/trường/khoa/học phần theo thông tin được xác nhận, tên đề tài VietRoute, sinh viên/lớp/khóa và năm |
| Phụ bìa | Số nhóm, danh sách sinh viên/MSSV, ô điểm và cán bộ chấm như mẫu; không tự điền điểm, tên hoặc chữ ký |
| Mục lục | Trang riêng, dùng Heading 1–3 và mục lục Word; cập nhật số trang sau khi bố cục cuối đạt |
| Danh mục viết tắt | Giải thích các từ thực sự dùng như OOP, JDK, MVC, JDBC, DTO, JWT, API và CSRF |
| Danh mục bảng/hình | Trang riêng khi có bảng/sơ đồ; tên và số theo chương, đối chiếu đúng trang sau kết xuất |
| Chương 1 Tổng quan về đề tài | Bài toán quản lý vận tải/đặt vé theo chặng, mục tiêu/phạm vi, tác nhân và phân công có căn cứ |
| Chương 2 Phân tích thiết kế chương trình | Yêu cầu, kiến trúc lớp, sơ đồ lớp/quan hệ, mô hình dữ liệu và lưu đồ các chức năng chính; giải thích transaction, quyền/scope và tồn chỗ |
| Chương 3 Cài đặt | Môi trường/công nghệ; module thực tế, luồng màn hình/API, validation/ngoại lệ, hướng dẫn chạy và ca kiểm/ảnh minh họa |
| Kết luận | Kết quả đã kiểm, hạn chế và hướng phát triển; nêu đúng mức hỗ trợ Oracle và dịch vụ ngoài |
| Tài liệu tham khảo | Đánh số theo thứ tự trích dẫn, dùng [n] trong nội dung; ghi cơ quan/tác giả, năm, tên, nguồn và ngày truy cập với tài liệu web |

### Định dạng và xử lý phần mẫu

Hướng dẫn viết trong mẫu yêu cầu A4 dọc; lề trên 2,5 cm, dưới 2 cm, trái 3 cm, phải 1,5 hoặc 2 cm. Chọn phải 2 cm khi chưa có yêu cầu khác. Font Times New Roman, thân bài 13pt, căn đều, dòng đầu 1,27 cm, Before/After 4pt và giãn dòng Multiple 1,3. Tên chương 14pt, đậm/in hoa/căn giữa; mục cấp một 13pt đậm, cấp hai đậm nghiêng; tối đa bốn mức. Tên bảng 13pt nghiêng đặt phía trên, tên hình đặt phía dưới; nội dung bảng theo mẫu và không thu chữ để ép số trang. Bìa giữ các mức 14/18pt được chú thích ở các textbox.

Thiết lập section hiện lưu trong file khác hướng dẫn chữ: khoảng 20,99 × 29,66 cm, trên 2,50 cm/dưới 3,00 cm/trái 2,50 cm/phải 2,00 cm. Khi tạo bản nộp, áp dụng yêu cầu định dạng ghi trong mẫu ở đoạn trên và ghi nhận thay đổi này; không sao chép nhầm các lề đang lệch. Bản mẫu có sáu section, TOC/PAGEREF/SEQ và PAGE trong footer; cần giữ cơ chế mục lục/đánh số, kiểm lại section bắt đầu số trang sau phần bìa. Không suy số trang thật từ metadata DOCX.

Thay toàn bộ ví dụ quản lý sinh viên/Student/ArrayList và tên Nguyễn Văn A/B/C bằng mô tả VietRoute đúng source. Bỏ các chỉ dẫn “xóa khi in”, bảng quy cách trình bày, textbox hướng dẫn bìa và hình minh họa thuộc đề tài mẫu khỏi bản nộp; giữ cấu trúc bìa, bảng thông tin, heading và số trang có chủ đích. Không gán UI Swing/JavaFX hoặc ORM cho sản phẩm Spring MVC/Thymeleaf/JDBC. File mẫu không chứa tên/MSSV/lớp/khóa/số nhóm thật của bốn thành viên; chỉ điền khi chủ dự án cung cấp, không suy họ tên từ GitHub username.

### Nguồn nội dung và điều kiện chốt

Dùng [đặc tả sản phẩm](DAC_TA_HE_THONG_XE_KHACH.md) cho bài toán/ca sử dụng; [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md), [API](../02-phat-trien/API_BASE.md), [schema/quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) và source tương ứng cho thiết kế/cài đặt; [quy ước UI](../03-giao-dien/QUY_UOC_UI.md) cho màn hình. [Phân công](PHAN_CONG.md) là kế hoạch trách nhiệm; chỉ dùng commit/PR/bằng chứng trong [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) để ghi đóng góp đã hoàn thành. Không ghi một phần nghiệp vụ thành code của người khác nhằm lấp bảng phân công.

Phân biệt bản trên dev và main/Render, ca thử cô lập và hạ tầng thật. Oracle hiện mới DB01/adapter cấu hình, chưa có runtime/migration/SQL nghiệp vụ; Gmail/Maps/Cloudinary/GPS và receiver/backup thực tế phải giữ đúng giới hạn nghiệm thu. Chỉ đưa ảnh được xác minh thuộc phiên bản báo cáo; che token/mật khẩu/PII, ghi rõ ảnh dùng dữ liệu kiểm thử khi cần. Kết quả test có mốc commit/môi trường, không diễn giải CI thành SLA production.

Trước bàn giao Word: đối chiếu nội dung từng chương với source, cập nhật mục lục/danh mục/số trang, kiểm bản mẫu còn nguyên checksum, kết xuất và xem mọi trang để phát hiện vỡ bảng, thiếu dấu, khoảng trống, ảnh/heading bị tách. Lượt đọc mẫu mới kiểm nội dung OOXML/styles/section/textbox/field; công cụ kết xuất đóng gói chưa chạy được vì thiếu LibreOffice trên môi trường Windows. Chưa có bản Word sản phẩm được tạo hoặc nghiệm thu bố cục.

## Các màn và chức năng tại mốc nền tảng

| Phần | Đầu ra dùng được | Giới hạn còn lại |
|---|---|---|
| Đăng nhập/tài khoản | Login/me/đổi mật khẩu/logout, đăng ký CUSTOMER/xác minh email/quên/reset; JWT cookie HttpOnly, CSRF, khóa tạm và lịch sử mật khẩu; riêng bootstrap được cấp sẵn email đã xác minh và không có rào mật khẩu tạm khi init/sync bật, giữ hash cũ | Mọi tài khoản khác kể cả ADMIN được gán phải đổi mật khẩu tạm/xác minh email; cần cấu hình provider + self-service/mail/worker trước khi cấp tài khoản thường, chưa thử inbox thật; SMS/phone OTP chưa triển khai |
| Trang quản trị `/admin` | Lối tắt giám sát/job/quyền/cấu hình; danh mục lọc nhóm, phân trang server với chọn size/nhảy trang, tạo/sửa/xóa mềm, xác nhận và version | Chỉ liệt kê danh mục active; chưa có khôi phục/lịch sử chỉnh sửa trên UI. Không phải CRUD nhà xe/địa điểm |
| Phân quyền | Registry role/quyền tùy chỉnh, ma trận/gán, inventory/policy menu/endpoint, kiểm URL trực tiếp và quyền nền; AccessOperation dùng chung PreAuthorize gọi accessGuard đọc DB; bảo vệ ADMIN/bản thân/admin cuối; nền scope/membership đã có | Guard tăng truy vấn DB, thu hồi ở lần kiểm tiếp theo; DAO nghiệp vụ chưa có và phải lọc ID + scope khi triển khai; không coi role là tenant scope |
| Quản trị tài khoản `/operations/accounts` | ADMIN cấp tài xế/người dùng/nhiều role, bắt đổi mật khẩu tạm, lọc/paging, khóa/mở có version và revoke JWT/challenge, xem menu/hành động thực tế theo policy user đích; ma trận và tạo role trực tiếp ở Phân quyền | Chưa có hồ sơ/membership nhà xe hoặc OPERATOR cấp tài xế trong scope; chưa gửi email mời tự động. Nâng/drain tất cả node trước khi cấp mật khẩu tạm |
| Công việc nền | Lọc trạng thái, phân trang, chi tiết metadata, retry DEAD có quyền và version | At least once; worker tắt mặc định; tác vụ dài cần cơ chế lease riêng |
| Giám sát | Heap/CPU/HTTP/pool/worker, scrape token riêng; tab online/phân trang/bất thường DB và missed writes | Lịch sử JVM vẫn theo node/tab; online DB có retention/capacity/cửa sổ 3 phút, không phải analytics tuyệt đối. Receiver cảnh báo ngoài app chưa xác nhận |
| Cấu hình | Whitelist thông số startup không nhạy cảm, chỉ đọc | Thay env ở IDE/Render rồi restart; không có nút sửa secret hoặc bật provider từ trình duyệt |
| Ảnh công khai | Thư viện, phân trang, upload/xem/sửa metadata/xóa theo quyền/owner/version, chuẩn hóa JPEG/PNG và cleanup | Khi dịch vụ tắt: hiện lý do, khóa file/upload và không hiện xóa; ảnh đã lưu vẫn xem được. Cần credentials Cloudinary và worker để nghiệm thu provider |
| Tài liệu API | Swagger và DTO/schema/mã lỗi vi/en; thử cookie có CSRF | Dữ liệu trả về Swagger vẫn theo quyền thực của tài khoản |
| Nền chạy | Schema cấu hình LOCAL cùng transaction, kiểm PgBouncer thật trong CI; checksum/lock/audit; Docker/CI, launcher IntelliJ; không Boot/ORM/Kafka/Redis mới | IDE dùng launcher đọc `.env` riêng hoặc env OS/IDE; WAR dùng env. Pool/quota/độ trễ cần đo theo hạ tầng thực; CI không chứng minh tải Neon Free/Render |

UI dùng layout xanh chung, menu không gạch chân ở trạng thái thường/hover, nền đánh dấu trang hiện tại và focus bàn phím. Menu đóng bằng Escape hoặc click ngoài. Bảng có vùng cuộn, ma trận quyền giữ header khi cuộn; form/loading/rỗng/lỗi dùng thông báo chung. Trang vận hành thu gọn cỡ tiêu đề, bảng và khoảng cách; không dùng cỡ chữ hero cho công cụ quản trị.

Các trang nhiều nhóm đã chuyển sang tab và form ghi sang modal: xem [quy ước tab/modal](../03-giao-dien/QUY_UOC_UI.md). Danh mục/Tổng quan, các tab phân quyền/registry/policy và tài nguyên/online tách riêng; cấu hình có sáu tab chỉ đọc. Form thêm/sửa danh mục, gán quyền/role, upload và đổi mật khẩu không còn chiếm cố định trên trang chính.

## Luồng danh mục để thành viên học theo

1. Mở `/admin`: lấy quyền hiện hành từ `/auth/me`, tải danh mục active theo nhóm/page/size. UI không suy ra quyền xóa từ `CATALOG_WRITE`.
2. Tạo: tên vi/en, thứ tự và nhóm; mã để trống được bỏ khỏi JSON để server tự sinh. Audit lấy từ principal, không từ form.
3. Sửa: đọc chi tiết quản trị mới nhất, giữ id/version trong RAM; khóa nhóm/mã, chỉ gửi tên/thứ tự/version khi PUT. Server chốt version bằng SQL.
4. Xóa mềm: xác nhận tên/mã trước DELETE có version; không xóa vật lý. Nếu trang cuối hết bản ghi, tải lại trang hợp lệ trước đó.
5. Mỗi mutation lấy CSRF mới, khóa fieldset khi xử lý. 409 giữ nội dung form, hiện hướng dẫn làm mới; không tự retry. Làm mới/hủy sửa bỏ bản nháp và đọc lại dữ liệu. Mất mạng sau mutation chưa chứng minh server đã rollback: kiểm danh sách trước khi thao tác lại.

`catalog.js` chịu trách nhiệm danh mục, `app.js` chỉ giữ HTTP/CSRF/lỗi, auth và menu chung. Không có API/schema mới trong đợt UI này; API CRUD và method security hiện hành vẫn là nơi chốt quyền/dữ liệu.

## Tra cứu mã yêu cầu

`traceId` là mã đối chiếu một HTTP request, có trong JSON và header `X-Request-ID`. UI hiển thị mã lỗi ổn định như `[COM008]` cùng thông báo đã dịch và hướng dẫn gửi mã yêu cầu cho quản trị viên. Nó không phải mã đăng nhập, mật khẩu hay kết luận nguyên nhân lỗi.

- IntelliJ: Console của đúng lần Run → tìm nguyên traceId.
- Trong app: ADMIN có SYSTEM_READ → Hệ thống → [Nhật ký API](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md), lọc traceId → modal Request/Response/Xuất debug. Lịch sử PostgreSQL dùng chung, mặc định 7 ngày; record đã commit còn sau restart, chỉ có snapshot đã lọc từ lúc triển khai V005.
- Render: service → Logs → tìm traceId trong đúng khoảng thời gian/node. Chưa có màn xem log tập trung trong app.
- Trình duyệt: DevTools → Network → request lỗi → Response, xem `code`, `message`, `errors`; không chia sẻ cookie/token hoặc nội dung mật khẩu.

Log HTTP chậm/5xx được lấy mẫu tối đa một dòng/giây/node; không bảo đảm mọi request đều có dòng `http_completed`. HTTP 400 có thể từ validation hoặc thao tác đổi mật khẩu; chỉ `route=auth` không phân biệt login với các API auth khác. Khi báo lỗi cần thêm thời điểm, thao tác và mã lỗi. Không bật log body/header để tìm mật khẩu.

## Bằng chứng kiểm tra và cửa vào nghiệp vụ

Mốc giao diện PR #29/#30 (lịch sử): 91 test Java, 10 test Node, format và tài liệu đạt; ba check CI của PR #30 đã thành công, gồm container Docker và auth/bootstrap trên PostgreSQL tạm. Bảng/modal có phân trang chung, cột thao tác 96px, nút bên phải và validation cạnh field; Home/login chọn đích theo principal. Export cURL có URL thực nhưng giữ token placeholder. Menu con cách nhau 6px, bộ lọc danh mục cùng hàng nút và dấu `*` đỏ hiện ngay khi mở modal. Kiểm vi/en, desktop/mobile 390px, submit trống và reset không nhân đôi dấu bằng HTML thật + API fixture; không dùng Neon để kiểm giao diện. Xem [quy tắc UI và ảnh](../03-giao-dien/QUY_UOC_UI.md).

Các bằng chứng sau giữ nguyên theo mốc nhật ký API trước đó, không phải số test hiện tại:

- Maven verify đợt nhật ký API: 85 test Java đạt, gồm mô hình root/servlet context tách biệt như WAR; không nạp `.env` production. Production package/kiểm WAR đạt và bỏ cả test compile/run; Docker CI phải đạt trước merge.
- 5 test Node đạt (Swagger/CSRF và cURL/Postman). Test nhật ký kiểm permission, TTL/capacity/ghi đồng thời, omission secret/PII và SQLState cả trước controller. File tải từ UI hợp lệ theo schema Postman v2.1; UI 390px không tràn ngang và Escape trả focus đúng. Docker CI bổ sung request thực trên DB tạm. Chi tiết phạm vi trong [hướng dẫn nhật ký](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md).
- HTML Thymeleaf: các trang quản trị/job/quyền/cấu hình/ảnh/giám sát/tài khoản/home/login render vi/en với principal thử, DB không tồn tại và initializer tắt.
- Trình duyệt với HTML thật và API giả trong RAM: phân trang, PUT/DELETE/POST có CSRF/version, 409 giữ form/chỉ gửi một lần, mã trống tự sinh, menu không gạch chân/active và dịch vụ ảnh tắt. Đây là bằng chứng frontend, không thay thế kiểm SQL/auth của Docker CI.
- Mobile 390px: quản trị, job, quyền, cấu hình, ảnh và giám sát không tràn ngang toàn trang. Bảng rộng được cuộn trong vùng riêng. Chưa kiểm mọi thiết bị/trình đọc màn hình.

Sau khi các kiểm tra/PR đạt, nền tảng source đủ để bắt đầu thiết kế và triển khai nghiệp vụ theo từng đợt. Nền SCOPE01 và quản trị tài khoản đã có; bước tiếp là tích hợp scope vào CARRIER01/AUTH03 và DAO nghiệp vụ, chưa được xây CRUD nhà xe bỏ qua quyền dữ liệu. CAT02 đã có UI cơ bản trong đợt nền tảng này, Anh tiếp tục nghiệm thu/tích hợp danh mục với nghiệp vụ thay vì viết bộ CRUD thứ hai. Mốc AUTH17–19 ở đầu tiến độ là bằng chứng hiện hành cho admin sẵn sàng, quyền hiệu lực và kết nối pooler; không có cam kết đã kiểm mọi lỗ hổng hoặc tải cao.

Trước khi mở cho người dùng thật vẫn cần xác nhận Render Live/env, credentials Gmail/Cloudinary khi sử dụng, collector/receiver cảnh báo, backup/restore staging và tải staging. Không gọi các phần đó là đã vận hành chỉ vì source có adapter/file mẫu. Redis/Kafka vẫn chưa bật; quota bộ nhớ là từng node. Lượt này chưa triển khai nhà xe, tuyến, chuyến, booking hoặc tracking.


## Bằng chứng bổ sung FOUNDATION02

Xem [tiến độ và CI của PR #35](TIEN_DO_VA_KE_HOACH_NHOM.md). Source đã qua 100 test Java, 16 test Node, Docker/PostgreSQL tạm và kiểm UI fixture; đủ nền tảng để nhóm bắt đầu module theo phân công. Khi tích hợp provider hoặc deploy thật vẫn phải nghiệm thu riêng. Không có benchmark tải Neon/Render Free, backup/receiver cảnh báo hoặc OTP chỉ vì base đã merge. Test Maven được cách ly env, runtime local vẫn có thể dùng Neon duy nhất qua `.env` riêng và initializer bật rõ ràng.

## Bằng chứng bổ sung FOUNDATION03

[PR #38](https://github.com/hieuv526034/vietroute-web/pull/38) đã merge, 103 test Java/16 test Node và ba CI tại head 599b8a4 đạt; link chi tiết ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md). Media bao gồm controller/page nằm hoàn toàn trong common.media; API dùng ApiResult<T> nhưng giữ envelope/status/header/Swagger. Danh bạ UI/API có V010, sync startup và tab chỉ đọc/phân trang; role là nhóm quyền, public/cứu hộ không nhận policy tùy biến. Policy GET bảo vệ cả HEAD. Metadata không tự cấp authority hay ghi đè mapping, không xóa route node khác có thể còn dùng. Cần bật init để áp dụng V010; sync mặc định theo init nếu không khai báo riêng. Giới hạn provider/vận hành production bên trên vẫn còn.


## Bằng chứng bổ sung FOUNDATION04

[PR #41](https://github.com/hieuv526034/vietroute-web/pull/41) đã merge tại 0237ad3; 109 test Java/16 test Node và ba CI tại head b435d65 đạt, link ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md). PostgreSQL tạm kiểm đủ đăng ký pending/CUSTOMER, kích hoạt/reset/mã một lần/hết hạn, cửa sổ mật khẩu và thu hồi JWT, outbox mã hóa + retry SMTP loopback, actor journal/filter. Production WAR không có test/dev/env; không chạy ghi/migration trên Neon để kiểm.

UI vi/en fixture kiểm tab/required, modal email giữ bản nháp khi lỗi, khóa thao tác trong lúc gửi và cột user/khách trên journal. Đây là dữ liệu UI kiểm thử; chưa nghiệm thu provider/inbox thật, tải production hoặc mọi thiết bị. Local Run có chẩn đoán nguồn DB, JVM property env-file và UTF-8 BOM; kiểm Neon chỉ đọc xác nhận kết nối/admin tại thời điểm chẩn đoán, chưa tái hiện lỗi IDE cụ thể. Runtime áp dụng V011 trước, rồi bật email có chủ đích; admin cũ không cần xác minh lại để đăng nhập. SMS/phone OTP chưa có. Bước kế tiếp là cấu hình/nghiệm thu provider và SCOPE01/hợp đồng cấp tài khoản theo phân công.


## Bằng chứng bổ sung FOUNDATION05

[PR #44](https://github.com/hieuv526034/vietroute-web/pull/44) đã merge; bằng chứng 112 Java/18 Node, ba CI đúng head 6318e38 và phạm vi SQL/UI tại [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md). Annotation mô tả inventory, quyền vẫn từ DB/service; ADMIN bất biến, role khác xóa mềm có chặn liên quan. Hồ sơ/avatar dùng media chung, owner/READY/version và không xóa ảnh đang tham chiếu. Menu tách nhóm, Swagger giữ danh tính cookie, controls gọn, loading/single-flight và heartbeat nền uiSilent. Khung 390px đã sửa dropdown, chưa thay kiểm thiết bị thật.

Runtime áp dụng V012 trên schema đang có và cập nhật tất cả node trước khi dùng avatar; không chạy migration trên Neon để kiểm build. Cloudinary/Gmail/Render Live/tải production vẫn cần nghiệm thu riêng; không có cam kết mọi cấu hình/tải đều không lỗi. Không còn phần code dở của FOUNDATION05, chưa bắt đầu nhà xe/chuyến/booking.

ACCESS05 đã merge tại [PR #56](https://github.com/hieuv526034/vietroute-web/pull/56), head `973c69f`: local 125 Java/25 Node; [Docker/PostgreSQL CI](https://github.com/hieuv526034/vietroute-web/actions/runs/40056779696) đạt toàn bộ cấp tài khoản/mật khẩu tạm/role/policy/khóa/revoke và bộ nền cũ. Bằng chứng và giới hạn đầy đủ ở đầu bảng tiến độ; không coi kết quả CI là đã triển khai trên Render hoặc nghiệm thu provider production.

ACCESS06 đã merge tại [PR #59](https://github.com/hieuv526034/vietroute-web/pull/59), head `6db2de4`: local 129 Java/25 Node và ba CI đúng head đạt; [Docker/PostgreSQL](https://github.com/hieuv526034/vietroute-web/actions/runs/40061898876) gọi accessGuard production với Authentication cũ và kiểm 8 trạng thái DB thật. Guard root/service và servlet/controller đọc DB khi method security kiểm; thu hồi, role/quyền/account tắt, temp password/tokenVersion và DB lỗi không chạy nghiệp vụ đã có regression. Không cache quyền, không đổi schema/JWT key, không ghi Neon. Cần nâng các node; chưa benchmark chi phí truy vấn hoặc nghiệm thu production/provider. Hợp đồng và giới hạn đồng thời tại [ACCESS06](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#hàm-kiểm-db-trong-preauthorize--access06).

## Đối chiếu ca nghiệp vụ — ACCEPT01

Bảng sau dẫn tới ca kiểm thực trong `src/test/java/vn/vietroute/BusinessIntegrationTest.java`, dùng hai context WAR và PostgreSQL test loopback; dữ liệu/số ca/CI cuối cùng ở [tiến độ BUSINESS04](TIEN_DO_VA_KE_HOACH_NHOM.md#business04--đang-làm-hoàn-thiện-luồng-sử-dụng-từ-tuyến-đến-đặt-vé). Không thay ca test bằng nạp/mutate Neon. Các latch test tạo hai tác nhân/yêu cầu đồng thời trong cùng DB cô lập; các browser screenshot chỉ chứng minh giao diện, không chứng minh tải production hoặc GPS vật lý.

| Ca đặc tả | Bằng chứng source/kiểm thử | Giới hạn cần hiểu |
|---|---|---|
| T01/T11 | `membershipFiltersCountRowsIdsAndPickerWithoutLeakingOtherCarrier`, `routesFilterScopeAtCountIdPickerAndRespectIndependentView`, `tripScopeFiltersCountIdsPickerAndIndependentViewWithoutOtherCrudRights`, `bookingStaffScopeFiltersCountIdsMutationsAndCustomerReadRevokesImmediately` | Fixture hai nhà xe tương đương tình huống đặc tả; kiểm scope/count/ID/owner trước projection, không xác nhận dữ liệu doanh nghiệp thật |
| T02/T03/T09 | `routesValidateStopOrderFareParentsAndAtomicVersion`, `publicSearchUsesPickupLocalDateDirectionAndRemainingSegmentsWithoutPrivateFields`, `tripsSnapshotAcrossDaysValidateAssetsAndRequireVersionAndState` | Đúng hướng, điểm trung gian, ngày tại pickup Việt Nam và giá; đi/về là hai tuyến riêng, không đảo tự động |
| T04/T05/T06 | `confirmationRaceCannotOversellAndSeparateSegmentsReuseSeats` và public search/quote | MIN từng segment, hai đơn không chung đoạn dùng lại chỗ; tranh chỗ cuối chỉ một xác nhận |
| T07/T08 | `tripCancellationAndBookingHistoryFailOrCommitTogetherWithCapacity`, `websiteBookingsKeepPriceAreOwnerBoundIdempotentAndOnlyOneMayRemainActive`, `manualPaymentsAndRefundsAreBoundedIdempotentAndDoNotFollowCancellationAutomatically` | Lỗi history rollback cùng chỗ/state, UUID retry không tạo trùng; giao dịch tiền chỉ ghi nhận thủ công thực tế |
| T10 | `concurrentDepartureAndConfirmationCannotConfirmAfterPickupCloses`, `driverDepartureRejectsPendingAndArrivalCompletesConfirmedWithoutReleasingHistoricalSegments` | Khóa tuần tự trip/booking/segment, không xác nhận lọt sau rời pickup |
| T12/T17 | `driverSeesOnlyCurrentAssignedNonDraftTripsAndScopeRevocationAppliesImmediately`, `driverProgressEnforcesStopOrderVersionTimeAndTerminalState`, `driverProgressHistoryFailureRollsBackAndConcurrentStartOnlyAdvancesOnce` | Current assigned/profile/membership/role, không ADMIN impersonation; actual từ server, không đi lùi hoặc sửa chỗ lịch sử |
| T13/T14 | `tripsSnapshotAcrossDaysValidateAssetsAndRequireVersionAndState`, `concurrentTripPublicationSerializesSharedVehicleAndDriverSchedules`, `tripAssignmentProtectsReservedCapacityAndHistoryFailureRollsBackAllWrites` | Snapshot giữ mẫu cũ; publish cạnh tranh chặn lịch chồng xe/tài xế và đổi capacity dưới reserved |
| T15 | `concurrentTripCancellationCannotLeaveConfirmedBookingOrReservedSeats` | Hủy/xác nhận đồng thời kết thúc bằng đơn hủy/chỗ0, không sự kiện hủy nhân đôi |
| T16 | UI lộ trình/điểm dừng và Maps UNCONFIGURED, tab vị trí text tại PR #121 | Chủ dự án hoãn key: chỉ nghiệm thu fallback văn bản; Google map/marker/chia waypoint chưa cấu hình và chưa nghiệm thu |
| T18 | `reportUsesDepartureCohortCountsOnceAndKeepsCancelledMoneyVisible`, `reportReadAndViewAreIndependentAndScopedRightsCannotBeUnionedOrReusedAfterRevocation` | Nhóm chuyến theo ngày khởi hành, tiền hiện tại của đơn; không là cashflow theo ngày thu hoặc doanh thu đã thu từ PENDING |
| T19/T20/T21 | WEB active/idempotence, capacity/race và `driverDepartureRejectsPendingAndArrivalCompletesConfirmedWithoutReleasingHistoricalSegments`; browser WEB/hủy/PHONE và Render owner/scope/capacity | PENDING không giữ chỗ, PHONE không cần account khách; hủy trả đúng chặng trước rời điểm, giữ history |
| Cấp nhân sự AUTH03 | `driverProvisioningCreatesOnlyPendingDriverScopeLinkAndAtomicHistory`, `driverProvisioningCannotCrossScopeEscalateRoleOrUseRevokedAssignment`, `driverProvisioningHistoryFailureRollsBackCredentialMembershipAndProfile`, `concurrentDriverProvisioningHasOneAccountAndAssignedTripBlocksIdentityReplacement` | Không tạo sẵn ngoại lệ cho account mới; Gmail tạm hoãn nên tài khoản mới chưa hoàn tất email |
| Tracking mục17 | Bốn ca `tracking*` ở PR #121 và `src/test/resources/dev/tracking.test.cjs` | Tọa độ trong test là mô phỏng cô lập. Kiểm consent/time/rate/replay/revoke/rollback/retention và lỗi adapter; chưa lấy GPS thiết bị vật lý/khóa màn hình/Google provider |

Thành viên chạy lại theo lệnh chuẩn ở [AI và bàn giao](../06-ai-va-ban-giao/AI_VA_BAN_GIAO.md), test integration cần `-Dbusiness.test.database=jdbc:postgresql://127.0.0.1:<cổng>/vietroute_business_test`; tài khoản `ciadmin`/mật khẩu fixture chỉ dùng test local. Không đổi URL này sang Neon để chứng minh chức năng. Xem API_BASE/SCHEMA/UI trước khi nhận thay đổi, giữ author thực và cập nhật PR theo mẫu CONTRIBUTING.

Nếu cần dựng PostgreSQL test bằng Docker (máy đã có Docker), dùng container riêng, không đặt env Neon cho lệnh test:

```powershell
docker run --rm --name vietroute-test -e POSTGRES_USER=ciadmin -e POSTGRES_PASSWORD=test_only -e POSTGRES_DB=vietroute_business_test -p 127.0.0.1:15439:5432 -d postgres:17
.\mvnw.cmd verify '-Dbusiness.test.database=jdbc:postgresql://127.0.0.1:15439/vietroute_business_test'
node --test src/test/resources/dev/*.test.cjs
docker stop vietroute-test
```

Chờ PostgreSQL sẵn sàng trước Maven; cổng có thể đổi trong cả Docker và URL test. Đây là hướng dẫn tạo môi trường mới cho thành viên, không khẳng định Docker container đó đang tồn tại trên máy. `test_only` chỉ là mật khẩu fixture, không dùng cho runtime.
