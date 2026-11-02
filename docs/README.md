# Bắt đầu đọc tài liệu VietRoute

Người mới chỉ cần đi theo **5 bước** dưới đây. Các tài liệu chuyên sâu mở khi làm đúng phần việc, không cần đọc hết docs trước khi chạy app.

## Đọc trước, đọc sau

| Bước | Mở tài liệu | Đầu ra cần đạt |
|---|---|---|
| 1 | [Tổng quan hệ thống](01-bat-dau/TONG_QUAN_HE_THONG.md) | Hiểu stack và phần nền tảng/nghiệp vụ đã có, còn thiếu |
| 2 | [Chạy IntelliJ](01-bat-dau/CHAY_LOCAL.md#nút-rundebug-trong-intellij), [tài khoản/dữ liệu](01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo), [Format](01-bat-dau/FORMAT_INTELLIJ.md) | Chạy app bằng môi trường riêng và dùng format chung |
| 3 | [Luồng request](02-phat-trien/LUONG_XU_LY_BASE.md), [API base](02-phat-trien/API_BASE.md) | Hiểu security → controller → service/transaction → JPA/JDBC → response/lỗi |
| 4 | [Quy ước phát triển](02-phat-trien/QUY_UOC_PHAT_TRIEN.md), [auth tám bảng và luồng quyền](02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) | Nắm package, DTO/Instant, audit, quyền, validation và cách viết code |
| 5 | [Tiến độ](05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), [Phân công](05-ke-hoach/PHAN_CONG.md) | Chọn việc/phụ thuộc và nhánh đúng người trước khi code |

Dùng AI hoặc chuyển máy: đọc thêm **[AI và bàn giao](06-ai-va-ban-giao/AI_VA_BAN_GIAO.md)** cùng [AGENTS.md](../AGENTS.md). Quy trình Git/PR nằm ở [CONTRIBUTING.md](../CONTRIBUTING.md).

## Tra cứu theo thư mục

| Cụm | Khi nào mở | Mở đầu bằng |
|---|---|---|
| [01-bat-dau](01-bat-dau/) | Hiểu dự án, công nghệ, chạy/format trên máy mới | Tổng quan → chạy local → format; [thư viện BE/UI](01-bat-dau/CONG_NGHE_VA_THU_VIEN.md) để tra cứu |
| [02-phat-trien](02-phat-trien/) | Viết API/service/SQL, schema/quyền/audit, constants và common | Luồng request → API → quy ước; chuyên đề mở theo task |
| [03-giao-dien](03-giao-dien/) | Làm bảng, phân trang, tab, modal và validation | [Quy ước UI](03-giao-dien/QUY_UOC_UI.md) |
| [04-van-hanh](04-van-hanh/) | Dùng màn hệ thống, debug traceId, giám sát hoặc deploy | [Quản trị](04-van-hanh/QUAN_TRI_HE_THONG.md); có lỗi đọc [nhật ký API](04-van-hanh/NHAT_KY_API_VA_DEBUG.md) |
| [05-ke-hoach](05-ke-hoach/) | Nhận việc, xem đặc tả, trạng thái và nghiệm thu | Tiến độ → phân công → đặc tả/ca nghiệm thu; [format báo cáo sản phẩm theo mẫu Java](05-ke-hoach/NGHIEM_THU_NEN_TANG.md#báo-cáo-sản-phẩm-theo-mẫu-công-nghệ-java) |
| [06-ai-va-ban-giao](06-ai-va-ban-giao/) | Tiếp tục task bằng AI hoặc đổi máy/người làm | Một hướng dẫn AI và bàn giao dùng chung |

Ảnh minh họa ở `images/`, không phải tài liệu bắt buộc đọc. Render/backup chỉ được coi hoạt động khi có bằng chứng vận hành; có adapter/file mẫu chưa chứng minh đã chạy thật.

## Giữ docs gọn

- Gốc `docs/` chỉ giữ README này. Tài liệu đặt trong cụm phù hợp, không tạo README/mục lục thứ hai cho từng nhóm nhỏ.
- Sửa tài liệu hiện có trước; chỉ thêm file khi chủ đề có trách nhiệm riêng. Nội dung trùng thì gộp, lịch sử đã thay thế tra qua Git, không giữ bản hướng dẫn lỗi thời.
- Quy tắc ở AGENTS/chuyên đề, trạng thái ở tiến độ, việc dở ở PR/Issue. Không sao chép backlog hoặc kết quả kiểm thử sang nhiều file.
- Khi chuyển/gộp file, cập nhật cả link, prompt AI, đường dẫn trong checker và mọi chỗ tham chiếu. Chạy `npm run check:docs` sau khi stage file mới để kiểm link/ảnh, cụm thư mục và đường đọc từ mục lục.
- Lịch sử tên/đường dẫn cũ tra qua Git/PR, không giữ các file chuyển hướng rỗng làm thư mục phình trở lại.
