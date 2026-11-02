# Bộ tài liệu VietRoute — 5 bước đọc

Mỗi nội dung có một nơi chính. Thành viên không cần đọc hết docs trước khi bắt đầu; đi theo 5 bước, rồi mở chuyên đề khi sửa đúng phần việc. Source/PR là căn cứ, không lấy bảng phân công làm bằng chứng đóng góp.

| Bước | Mở tài liệu | Kết quả cần đạt |
|---|---|---|
| 1 | [Tổng quan](01-bat-dau/TONG_QUAN_HE_THONG.md) | Hiểu bài toán, tác nhân, công nghệ, khối/bảng và luồng chung |
| 2 | [Chạy local và account](01-bat-dau/CHAY_LOCAL.md), [formatter](01-bat-dau/FORMAT_INTELLIJ.md) | Chạy đúng DB demo, có tài khoản, biết quy tắc format |
| 3 | [Phần của từng thành viên](05-ke-hoach/PHAN_CONG.md) | Đi từ màn/API → service → repo/bảng, biết ca lỗi và đầu ra bàn giao |
| 4 | [Demo và 45 hỏi–đáp](05-ke-hoach/DEMO_VA_HOI_DAP.md) | Tập bài trình bày15 phút, ví dụ số, câu trả lời và source chứng minh |
| 5 | [Tiến độ](05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md) | Chốt phần đã main/Render, phần PR chưa tích hợp và giới hạn trước demo |

## Vào thẳng phần của mình

| Thành viên | Đường học và trình bày |
|---|---|
| hieuv526034 | [Auth/common/DB/deploy](05-ke-hoach/PHAN_CONG.md#hieuv526034) |
| anhhoang104 | [Danh mục/địa điểm/nhà xe](05-ke-hoach/PHAN_CONG.md#anhhoang104) |
| BangPhamIT | [Tuyến/chuyến/tìm chuyến/đặt chỗ/tiến độ](05-ke-hoach/PHAN_CONG.md#bangphamit) |
| hiencoi250404-design | [Báo cáo/dashboard](05-ke-hoach/PHAN_CONG.md#hiencoi250404-design) |

## Tra cứu khi phát triển hoặc vận hành

| Cụm | Tài liệu chính |
|---|---|
| 01 — Bắt đầu | Tổng quan/local/format ở bước1–2 |
| 02 — Backend/DB | [Quy ước, request/API/transaction/audit](02-phat-trien/QUY_UOC_PHAT_TRIEN.md); [schema/quyền/migration](02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) |
| 03 — Giao diện | [UI: bảng, tab, modal, draft và paging](03-giao-dien/QUY_UOC_UI.md) |
| 04 — Vận hành | [Quản trị, journal/cURL/Postman, backup và Render](04-van-hanh/VAN_HANH.md) |
| 05 — Làm nhóm/demo | Phân công, demo–hỏi đáp và tiến độ ở bước3–5; [yêu cầu báo cáo Word](05-ke-hoach/DEMO_VA_HOI_DAP.md#báo-cáo-sản-phẩm-theo-mẫu-công-nghệ-java) |
| 06 — AI/bàn giao | [Cách tiếp tục khi đổi máy/người](06-ai-va-ban-giao/AI_VA_BAN_GIAO.md), [AGENTS](../AGENTS.md), [Git/PR](../CONTRIBUTING.md) |

Bộ gọn có 12 file Markdown trong sáu cụm, không giữ hướng dẫn cũ hoặc redirect rỗng. Version thư viện tra pom.xml, payload tra controller/Swagger đúng bản, account/key theo CHAY_LOCAL và ngoại lệ .env.example; bằng chứng chỉ ở tiến độ/PR. Mẫu Word gốc nằm trong05-ke-hoach/mau, chưa phải báo cáo sản phẩm đã soạn. Khi chuyển/gộp/xóa phải sửa link/prompt/điểm đọc AI và chạy check:docs sau khi stage file mới.
