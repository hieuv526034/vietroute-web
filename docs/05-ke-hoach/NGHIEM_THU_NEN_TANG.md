# Nghiệm thu source hiện hành

Đối chiếu [đặc tả](DAC_TA_HE_THONG_XE_KHACH.md), [API](../02-phat-trien/API_BASE.md), [schema](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) và [phân công](PHAN_CONG.md). Kết quả thực tế/môi trường/commit chỉ ghi ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), không coi danh sách dưới đây là đã chạy.

## Bộ ca theo khối

| Khối | Ca phải nghiệm thu |
|---|---|
| auth | Login/CSRF/JWT/khóa sai/đổi mật khẩu/revoke, CRUD và gán role/function/endpoint; protected/system/self, grant/revoke trực tiếp, scope/all_scopes cùng role, hai context WAR |
| catalog/carrier | Tạo/sửa/ngừng dùng, validation/type/unique plate/parent inactive, scope list/count/ID/picker, cấp account tài xế đúng scope và rollback |
| trip | Tuyến đúng thứ tự, snapshot giữ lịch sử, phân công/chồng lịch, state/version, tồn chỗ, tài xế đúng assignment và mốc tiến độ |
| booking | Owner/scope, đúng giá/chặng, PENDING/CONFIRMED, race giữ/trả chỗ, hủy chuyến theo tập, thu/hoàn/requestKey/history rollback |
| report | Số liệu đối soát đơn/chuyến/tiền thực thu, khoảng ngày múi giờ Việt Nam, bộ lọc/picker và scope |
| common | Envelope/status/locale/traceId/Swagger, giới hạn body, journal whitelist/commit/retention/failed writes, cURL/Postman URL thật và placeholder |
| database | Fresh tám bước/24 bảng, custom schema/pg_trgm namespace, checksum sai/history lạ fail closed, startup lặp, rebaseline check rollback/drift refusal/data preservation |
| UI | vi/en, 320/390/768px và desktop, tab bàn phím/ARIA, modal focus/busy/draft, paging server, 401 link đăng nhập và không tự retry mutation |

## Chạy kiểm tra và giới hạn

npm ci, npm run format, format:check, check:rules, check:docs; Maven verify và Node test theo quy trình bàn giao. PostgreSQL test chỉ loopback/database riêng, schema random được cleanup; H2 không thay kiểm PostgreSQL về array/advisory lock/index/FK. Production -Pproduction không compile hoặc đóng gói test. Routine kiểm không đọc .env hoặc ghi Neon.

Trước production cần CI Docker/PostgreSQL/pooler/recovery và nghiệm thu HTTP/UI trên bản đúng commit. Không gọi green local test là SLA, tải thực hoặc đã deploy. Rebaseline production cần backup phục hồi và dừng node theo quy trình DB; không reset mật khẩu hoặc dữ liệu để làm test xanh.

## Báo cáo sản phẩm theo mẫu Công nghệ Java

Ngày 31/10/2026, chủ dự án chỉ định [mẫu Word Công nghệ Java](../xxx-tai-lieu-thi/Mau_BTL_Cong_nghe_Java.docx) làm format cho tài liệu sản phẩm VietRoute và yêu cầu lưu file mẫu gốc trong Git để dùng chung khi đổi máy. Giữ nguyên byte của bản mẫu; SHA-256 là `bd8fc0707ebadb9373d988f28bc7d4b036542f6bb8a662191228f410ca5089c6`. Mẫu theo repository tại `docs/xxx-tai-lieu-thi/`, không dùng đường dẫn máy cá nhân làm nguồn thay thế khi bàn giao. Đây là báo cáo bài tập lớn học phần Công nghệ Java, khác với hướng dẫn vận hành/API hiện có.

Chủ dự án đã xác nhận: **chỉ đọc mẫu và chốt cấu trúc trước, hoàn thiện ứng dụng rồi mới soạn tài liệu**. Báo cáo thuộc **Nhóm 10**, gồm bốn thành viên đã xác định tại [phân công](PHAN_CONG.md#thông-tin-thành-viên): `anhhoang104`, `BangPhamIT`, `hiencoi250404-design`, `hieuv526034`. **Lớp, khóa và giảng viên để trống**, bổ sung sau theo thông tin chủ dự án cung cấp. Họ tên/MSSV dùng dữ liệu đã được xác nhận khi soạn; không suy từ username hoặc tự tạo danh tính. Không bắt đầu viết báo cáo Word hoàn chỉnh chỉ từ việc ghi nhận thông tin này.

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

Thay toàn bộ ví dụ quản lý sinh viên/Student/ArrayList và tên Nguyễn Văn A/B/C bằng mô tả VietRoute đúng source. Bỏ các chỉ dẫn “xóa khi in”, bảng quy cách trình bày, textbox hướng dẫn bìa và hình minh họa thuộc đề tài mẫu khỏi bản nộp; giữ cấu trúc bìa, bảng thông tin, heading và số trang có chủ đích. Không gán UI Swing/JavaFX cho sản phẩm Spring MVC/Thymeleaf/JPA/JDBC. Khi soạn, dùng số nhóm và quy tắc điền thông tin đã chốt ở đầu mục này; không lấy tên/MSSV minh họa trong mẫu làm dữ liệu thật.

### Nguồn nội dung và điều kiện chốt

Dùng [đặc tả sản phẩm](DAC_TA_HE_THONG_XE_KHACH.md) cho bài toán/ca sử dụng; [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md), [API](../02-phat-trien/API_BASE.md), [schema/quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) và source tương ứng cho thiết kế/cài đặt; [quy ước UI](../03-giao-dien/QUY_UOC_UI.md) cho màn hình. [Phân công](PHAN_CONG.md) là kế hoạch trách nhiệm; chỉ dùng commit/PR/bằng chứng trong [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) để ghi đóng góp đã hoàn thành. Không ghi một phần nghiệp vụ thành code của người khác nhằm lấp bảng phân công.

Phân biệt bản trên dev và main/Render, ca thử cô lập và hạ tầng thật. Oracle 26ai hiện mới ORACLECFG01 cấu hình JDBC Thin/profile/Hibernate dialect, chưa có runtime/migration/SQL nghiệp vụ và chưa kiểm kết nối Oracle thật; các dịch vụ ngoài phạm vi hiện hành không đưa vào báo cáo như chức năng đã làm. Backup/restore phải có bằng chứng riêng. Chỉ đưa ảnh được xác minh thuộc phiên bản báo cáo; che token/mật khẩu/PII, ghi rõ ảnh dùng dữ liệu kiểm thử khi cần. Kết quả test có mốc commit/môi trường, không diễn giải CI thành SLA production.

Trước bàn giao Word: đối chiếu nội dung từng chương với source, cập nhật mục lục/danh mục/số trang, kiểm bản mẫu còn nguyên checksum, kết xuất và xem mọi trang để phát hiện vỡ bảng, thiếu dấu, khoảng trống, ảnh/heading bị tách. Lượt đọc mẫu mới kiểm nội dung OOXML/styles/section/textbox/field; công cụ kết xuất đóng gói chưa chạy được vì thiếu LibreOffice trên môi trường Windows. Chưa có bản Word sản phẩm được tạo hoặc nghiệm thu bố cục.
