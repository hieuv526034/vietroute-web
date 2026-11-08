# Bộ tài liệu VietRoute — 5 bước đọc

Mỗi nội dung có một nơi chính. Thành viên không cần đọc hết docs trước khi bắt đầu; đi theo 5 bước, rồi mở chuyên đề khi sửa đúng phần việc. Source/PR là căn cứ, không lấy bảng phân công làm bằng chứng đóng góp.

| Bước | Mở tài liệu | Kết quả cần đạt |
|---|---|---|
| 1 | [Tổng quan](01-bat-dau/TONG_QUAN_HE_THONG.md) | Hiểu bài toán, tác nhân, công nghệ, khối/bảng và luồng chung |
| 2 | [Hướng dẫn sử dụng A–Z](01-bat-dau/HUONG_DAN_SU_DUNG.md), [chạy local và account](01-bat-dau/CHAY_LOCAL.md), [formatter](01-bat-dau/FORMAT_INTELLIJ.md) | Đăng nhập, thao tác theo vai trò, thực hành trọn luồng và xử lý lỗi |
| 3 | [Phần của từng thành viên](05-ke-hoach/PHAN_CONG.md) | Đi từ màn/API → service → repo/bảng, biết ca lỗi và đầu ra bàn giao |
| 4 | [Lời thoại demo và 55 hỏi–đáp](05-ke-hoach/DEMO_VA_HOI_DAP.md) | Hai dev có lời thoại theo màn, bản rút gọn, ví dụ số và câu trả lời có source |
| 5 | [Tiến độ](05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md) | Chốt phần đã main/Render, phần PR chưa tích hợp và giới hạn trước demo |

## Vào thẳng phần của mình

| Thành viên | Đường học và trình bày |
|---|---|
| hieuv526034 | [Dev nền, DB/deploy và báo cáo/dashboard](05-ke-hoach/PHAN_CONG.md#hieuv526034) |
| BangPhamIT | [Dev catalog/carrier/trip/booking và UI](05-ke-hoach/PHAN_CONG.md#bangphamit) |
| anhhoang104 | [Báo cáo Word, sơ đồ và tổng hợp bản nộp](05-ke-hoach/PHAN_CONG.md#anhhoang104) |
| hiencoi250404-design | [Test thủ công, ảnh chức năng và báo cáo Word](05-ke-hoach/PHAN_CONG.md#hiencoi250404-design) |

## Tra cứu khi phát triển hoặc vận hành

| Cụm | Tài liệu chính |
|---|---|
| 01 — Bắt đầu | Tổng quan/local/format ở bước1–2 |
| 02 — Backend/DB | [Quy ước, request/API/transaction/audit](02-phat-trien/QUY_UOC_PHAT_TRIEN.md); [schema/quyền/migration](02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) |
| 03 — Giao diện | [UI: bảng, tab, modal, draft và paging](03-giao-dien/QUY_UOC_UI.md) |
| 04 — Vận hành | [Quản trị, journal/cURL/Postman, backup và Render](04-van-hanh/VAN_HANH.md) |
| 05 — Làm nhóm/demo | Phân công, demo–hỏi đáp, tiến độ, [yêu cầu báo cáo Word](05-ke-hoach/DEMO_VA_HOI_DAP.md#báo-cáo-sản-phẩm-theo-mẫu-công-nghệ-java) và [test case tài khoản, tổng quan, lịch chuyến](05-ke-hoach/TEST_CASE_TAI_KHOAN_TONG_QUAN_LICH_CHUYEN.md) |
| 06 — AI/bàn giao | [Cách tiếp tục khi đổi máy/người](06-ai-va-ban-giao/AI_VA_BAN_GIAO.md), [AGENTS](../AGENTS.md), [Git/PR](../CONTRIBUTING.md) |

Bản nộp: [Báo cáo Word VietRoute Nhóm10](05-ke-hoach/bao-cao/Bao_cao_VietRoute_Nhom_10.docx), kèm [sơ đồ SVG và ảnh gốc](05-ke-hoach/bao-cao/Tu_lieu_VietRoute_Nhom_10.zip). Điền các ô [[GIANG_VIEN]], [[LOP]], [[KHOA_HOC]], [[HO_TEN_1]]… và [[MSSV_1]]… trên hai trang bìa; cập nhật field khi sửa nội dung. Phạm vi sản phẩm, kết quả kiểm và trạng thái phát hành xem tiến độ.

Bản hướng dẫn có thể in tại [Hướng dẫn sử dụng VietRoute](01-bat-dau/Huong_dan_su_dung_VietRoute.docx). Bản Markdown cập nhật theo source hiện tại. [Từ điển bảng và cột](02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#từ-điển-bảng-và-cột) mô tả 22 bảng và 340 cột. Bản Word nghiệm thu trước đợt lịch hằng ngày ghi cấu trúc 338 cột.

[PowerPoint bảo vệ VietRoute](05-ke-hoach/bao-cao/Thuyet_trinh_VietRoute.pptx) gồm 28 slide về bài toán, tác nhân, chức năng, ảnh ứng dụng, kiến trúc Spring MVC, dữ liệu, kiểm thử, triển khai và demo. Thông tin trường, sinh viên, mã số và giảng viên để trống. Mở Notes để xem lời nói và nguồn đối chiếu, tập theo [kịch bản demo](05-ke-hoach/DEMO_VA_HOI_DAP.md). Slide phân biệt chức năng hiện có, dữ liệu giả lập và hướng phát triển.

Bộ tài liệu Markdown nằm trong sáu cụm. Tra version thư viện ở `pom.xml`, payload ở controller/Swagger và account/key theo hướng dẫn chạy local. Bằng chứng kiểm tra nằm ở tiến độ/PR. Mẫu Word gốc nằm trong `05-ke-hoach/mau` và báo cáo sản phẩm ở thư mục `bao-cao`. Khi chuyển, gộp hoặc xóa file, cập nhật liên kết và chạy `check:docs` sau khi stage file mới.
