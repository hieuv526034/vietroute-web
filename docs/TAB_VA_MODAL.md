# Tab và modal cho giao diện quản trị

Theo yêu cầu 23/10/2026: trang có nhiều nhóm chức năng tách tab; form thêm/sửa/gán mở trong modal để trang chính tập trung vào danh sách và tra cứu. Các module business sau dùng lại mẫu này, không tạo cơ chế tab/CSRF/form riêng.

## Những màn đã áp dụng

| Trang | Tab | Modal |
|---|---|---|
| `/admin` | Danh mục / Tổng quan công cụ hệ thống | Thêm/sửa danh mục |
| `/operations/access` | Quyền vai trò / Vai trò tài khoản; tab tài khoản chỉ hiện cho ADMIN theo quyền hiện hành | Chỉnh quyền của role thường; gán role cho user |
| `/operations/configuration` | Ứng dụng / Pool / Bảo vệ request / Worker / Tính năng / Giám sát | Chỉ đọc, không thêm modal chỉnh Environment |
| `/account` | Một nhóm thông tin tài khoản nên không thêm tab thừa | Đổi mật khẩu |
| `/media` | Một nhóm thư viện nên không thêm tab thừa | Upload ảnh; dịch vụ tắt thì nút mở bị khóa |
| `/operations/jobs` | Một danh sách có lọc trạng thái | Chi tiết/retry hiện hành, đồng bộ kiểu dialog |

Ma trận quyền ở modal chỉnh sửa; phía tab hiện danh sách quyền đã được cấp. Role ADMIN chỉ xem và không có nút sửa khả dụng. Gán role bản thân vẫn bị khóa. Các modal có tiêu đề/đối tượng rõ ràng và nút đóng; CSRF, version, phạm vi và thu hồi token giữ nguyên ở API/service.

## Dùng component chung

Danh sách và form dùng thêm [bảng/phân trang/validation chung](BANG_VA_VALIDATION_UI.md). Nút tìm kiếm/thêm đặt bên phải, cột thao tác rộng cố định với icon có tên truy cập; footer Hủy/Lưu dùng `dialog-actions` cùng hàng bên phải. Không tự tạo pager hoặc validation bằng bubble riêng cho mỗi module.

`assets/js/workspace.js` được layout nạp trước `app.js`, cung cấp `window.VietRouteUI`. Không cần Bootstrap JS hoặc thư viện modal mới. CSS dùng `workspace-tabs`, `workspace-dialog`, `workspace-dialog-wide`, `dialog-heading`, `dialog-controls`; form danh mục dùng grid hai cột desktop, một cột mobile.

- Bọc nhóm bằng `data-tabs`. Mỗi button có id duy nhất, `data-tab-target` trỏ id panel trong nhóm; đặt trong vùng `role="tablist"` có tên dịch vi/en. Không dùng tab để cấp quyền.
- Helper đặt `role=tab/tabpanel`, `aria-selected`, `aria-controls`, `aria-labelledby`, roving tabindex và hidden. Left/Right/Home/End chuyển tab; click vẫn dùng bình thường. Tab cấu hình được giữ khi bấm Làm mới; không lưu tab trong localStorage hoặc giả định giữ qua reload trình duyệt.
- Khi sinh tab động, tạo nhóm DOM mới rồi gọi `initializeTabs(container)`. Dùng `tabs(group).show(panelId, visible)` khi quyền thay đổi; không tự gỡ hidden của panel vì có thể làm lộ hai tab cùng lúc.
- Dialog dùng phần tử `<dialog>` native với `aria-labelledby`, gọi `modal(id, {busy, onClose})`. Nút hủy/đóng dùng `data-dialog-close`. Native giữ focus bên trong; component trả focus về nút mở còn tồn tại sau khi đóng. Nếu mở sau await/khóa fieldset, truyền nút mở đã giữ từ sự kiện click vào `open(trigger)`.
- Đặt input/button trong fieldset `dialog-controls`; caller khóa fieldset khi gửi. `busy()` chặn Escape và nút đóng trong thời gian request chưa hoàn tất. Trang nền bị khóa cuộn khi có dialog mở. Không đóng khi click backdrop để tránh mất bản nháp do bấm nhầm.

## Gửi form và xử lý lỗi

Lấy dữ liệu **trước khi disable fieldset** nếu dùng FormData, vì control disabled bị loại khỏi FormData. Với mật khẩu, chỉ giữ payload trong lần submit; form bị reset khi hủy/đóng hoặc thành công, không ghi vào URL/log/storage. Không nạp sẵn mật khẩu vào HTML.

Lỗi validation/401/403/409/timeout xuất hiện trong vùng feedback có role alert của modal đang mở. Mã lỗi/traceId dùng chung `app.failure`; không đưa message vào innerHTML. Lỗi 409 giữ nguyên nội dung/selection, không tự retry và không tự lấy version mới rồi ghi đè. Đóng/hủy bỏ bản nháp; mở lại để lấy bản hiện hành. Khi server xác nhận thành công mới đóng dialog, thông báo kết quả tại trang và tải lại dữ liệu. Timeout sau mutation chưa chứng minh server đã rollback: kiểm dữ liệu trước khi gửi lại.

Không gọi `close()` tự động khi lỗi. Sau khi thành công, caller có thể đóng dialog ngay trong luồng xử lý dù cờ busy chưa hạ; nút đóng của người dùng vẫn bị khóa đến khi request kết thúc. Không bắt người dùng bấm lại xác nhận trình duyệt cho nút lưu quyền trong modal có đối tượng/version rõ ràng; xóa mềm và retry job vẫn có bước xác nhận hiện hành.

## Kiểm tra đã thực hiện

Render Thymeleaf vi/en với principal thử, DB không tồn tại và initializer tắt. Kiểm trình duyệt bằng API fixture trong RAM: chuyển tab bằng bàn phím, một panel hiện, modal thêm/sửa, CSRF/version, modal quyền/gán role, chặn tự gán role, 409 giữ dữ liệu, request chậm không cho Escape đóng, trả focus về nút sửa, giữ tab cấu hình khi refresh và form mật khẩu được xóa khi hủy. Không gửi đổi mật khẩu thật hoặc gọi Cloudinary thật trong kiểm giao diện. Upload chỉ kiểm mở/đóng theo cờ enabled giả; transport/pixel/provider giữ bộ kiểm tra hiện hành.

Mobile 390px: dialog danh mục/phân quyền và sáu tab cấu hình không tràn ngang toàn trang. Bảng quyền cuộn bên trong dialog; không coi đây là chứng nhận mọi thiết bị/trình đọc màn hình. Maven/production/Docker CI và kết quả merge theo PR đi kèm; kiểm fixture không thay thế SQL/auth integration.
