# Quy ước UI và đường demo

Mở [phần thành viên](../05-ke-hoach/PHAN_CONG.md) để biết page/API thuộc ai; tập trình bày theo [demo–hỏi đáp](../05-ke-hoach/DEMO_VA_HOI_DAP.md). Tài liệu này giữ quy tắc dùng chung; hành vi nghiệp vụ do service/DB quyết định.

## Thành phần dùng chung

| Thành phần | Trách nhiệm |
|---|---|
| fragments/layout.html và navigation.js | Header/menu, locale, breadcrumb, details/summary và bàn phím |
| workspace.js | Tab/modal và các thao tác CRUD theo capability |
| table.js và fragment pagination | Bảng và paging server; count/size/page từ response |
| forms.js | Validation, field error và trạng thái form |
| request-state.js | Single-flight cho thao tác đang chạy; đợi nhóm đọc kết thúc trước mở khóa |
| app.js (VietRouteAccess) | Capabilities hiện hành, không thay backend authorization |

Tên và file thực tế ở [assets/js](../../src/main/webapp/assets/js) và [views](../../src/main/webapp/WEB-INF/views). Không thêm thư viện hoặc lớp wrapper chỉ để đổi cách gọi.

## Page, bảng, tab và modal

Page có heading/hướng dẫn ngắn, vùng feedback, bộ lọc, bảng trong vùng cuộn và pager. Toolbar/footer bên phải; cột icon thao tác một hàng/tự giãn, có aria-label/title. Giữ nhãn vi/en, focus-visible và trạng thái aria-busy. Mobile320/390px không làm toàn trang tràn ngang; bảng cuộn trong vùng riêng.

CRUD/gán dùng modal; nhiều nhóm nội dung dùng tab. Mở modal xóa dữ liệu đối tượng cũ, khóa controls đến khi các snapshot cần thiết tải xong. Lỗi tải/validation/409 giữ modal và draft, chỉ bỏ dữ liệu không còn hợp lệ do đổi cha. Không tự đóng hoặc gửi lại mutation; không cho đóng khi đang gửi. Đóng trả focus về nút mở; Enter/Tab/Escape có hành vi rõ ràng.

Đổi trang/lọc không làm mất lựa chọn đã giữ ở picker nếu nghiệp vụ còn hợp lệ. Đổi nhà xe/tuyến/tỉnh phải xóa các lựa chọn phụ thuộc không còn đúng. Không tự tính lại quyền, giá, chỗ hoặc báo cáo trên JavaScript. Giao diện ẩn nút giúp người dùng, backend vẫn chặn URL/API trực tiếp.

## Các luồng cần nhận diện

| Màn | Cách sử dụng và ranh giới |
|---|---|
| /operations/accounts, /operations/access | CRUD/gán trong modal; page grant và API grant riêng, preview quyền của user đích |
| Catalog/carrier | CRUD danh mục/địa điểm/nhà xe/xe/tài xế; field cha và scope phải hợp lệ |
| /operations/routes, /operations/trips | Tuyến có điểm thứ tự; chuyến lưu snapshot, chỉ mở khi đủ điều kiện |
| /journeys | Chọn hai đầu hành trình, ngày đón và số chỗ; tìm xong mới áp bộ lọc vào bảng; xem giá/chỗ từ server |
| /my-bookings, /operations/bookings | WEB của owner hoặc PHONE/staff trong scope; PENDING chưa giữ chỗ; lỗi giữ requestKey/draft |
| /driver/trips | Chuyến được phân công, danh sách đón/trả và mốc thực tế; không GPS |
| /operations/reports | Ngày/nhà xe, hai tab số liệu; chỉ nút xem báo cáo mới áp tiêu chí, picker nhà xe không gọi tổng hợp |
| /operations/api-journal | Lọc metadata, mở chi tiết mới lấy snapshot; copy cURL/Postman đã che dữ liệu |

Source gồm UI02/PR154 và UI03; trạng thái tích hợp/phát hành xem tiến độ/PR. Menu gom theo Danh mục/Nhà xe/Tuyến–chuyến/Đặt chỗ/Báo cáo/Quản trị từ chính các link DB đã cấp, không sinh quyền mới; khoảng cách link6px. Select đọc đủ danh mục qua API100 dòng/trang, không có nút tìm/pager riêng: tỉnh → địa điểm, nhà xe → tuyến/xe/tài xế. Đổi cha xóa lựa chọn con trước khi tải lại; lỗi không cho lưu khi thiếu danh sách. Báo cáo chọn nhà xe trực tiếp, chỉ Xem báo cáo mới áp ID mới.

Phân trang chỉ ở bảng kết quả: ẩn khi rỗng, một trang ẩn điều hướng/nhảy trang, nhiều trang giữ paging server. Bản đang chạy cần đúng source và khởi động lại Java mới có nhóm menu. Tra [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), không coi preview/API giả là bằng chứng production.

## Lỗi, credential và khả năng truy cập

401 có đường đăng nhập; Home/login chọn đích theo principal. Không lưu password/JWT vào localStorage/sessionStorage. Không lấy cookie auth để export request hoặc tự replay thao tác tài chính. Guard JSON64KiB/BCrypt đồng thời/CSRF phải giữ, không nới để làm UI có vẻ nhanh.

Validation quan hệ như minPrice≤maxPrice gắn lỗi đúng field; sửa một cận bỏ lỗi quan hệ cũ. Unknown enum/code từ DB không làm client tự cấp grant. Bảng rỗng có thông báo, pager không hiện0/0; không suy tổng từ số option client đang giữ.

Kiểm desktop và 320/390/768px; Tab/Shift+Tab, Escape, focus modal, locale vi/en, trường hợp không quyền/401/409/lỗi tải, lựa chọn phụ thuộc và không gửi mutation trùng. Không cần viết test chỉ lặp lại CSS; dùng browser để kiểm bố cục và regression có ý nghĩa khi sửa logic.
