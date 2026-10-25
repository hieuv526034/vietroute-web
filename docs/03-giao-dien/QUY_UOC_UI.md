# Quy ước UI: tab, modal, bảng và validation

Đọc sau [luồng request](../02-phat-trien/LUONG_XU_LY_BASE.md) khi làm màn business. Tái sử dụng layout/Bootstrap/Axios trong WAR; không thêm CDN, jQuery hoặc thư viện chỉ để tạo pager/modal. API vẫn phân trang PostgreSQL; quyền backend không thay bằng ẩn menu/nút.

## 1. Chọn tab và modal

| Trang | Nhóm trên trang | Modal |
|---|---|---|
| `/admin` | Tổng quan | Không |
| `/catalogs/vehicle-types`, `/catalogs/booking-sources` | Từng danh mục riêng | Thêm/sửa/ngừng sử dụng |
| `/operations/access` | Quyền role / Vai trò tài khoản (ADMIN) | Chỉnh quyền role thường, gán role user |
| `/operations/configuration` | Ứng dụng / Pool / Bảo vệ request / Worker / Tính năng / Giám sát | Chỉ đọc, không chỉnh env/secret |
| `/account`, `/media` | Một nhóm, không thêm tab thừa | Đổi mật khẩu, upload ảnh; ảnh tắt thì khóa nút mở |
| `/operations/jobs` | Danh sách có lọc trạng thái | Chi tiết/retry theo quyền và version |

Role ADMIN chỉ xem; gán role bản thân bị khóa. Modal có tên/đối tượng rõ ràng; kiểm CSRF, version, scope, quyền và thu hồi token vẫn ở API/service.

`workspace.js` nạp trước `app.js`, cung cấp `VietRouteUI`; dùng `workspace-tabs`, `workspace-dialog`, `dialog-heading`, `dialog-controls`, `dialog-actions`. Form danh mục hai cột desktop, một cột mobile.

- Nhóm tab dùng `data-tabs`, button id duy nhất và `data-tab-target`; `role=tablist` có tên vi/en. Helper quản lý tab/tabpanel, ARIA, roving tabindex và hidden; Left/Right/Home/End chuyển tab. Refresh giữ tab hiện tại, không lưu tab/token vào storage.
- Tab động gọi `initializeTabs(container)`; `tabs(group).show(panelId, visible)` đổi hiển thị theo quyền, không tự gỡ hidden làm lộ nhiều panel.
- Dialog native có `aria-labelledby`; gọi `modal(id, {busy, onClose})`, hủy/đóng dùng `data-dialog-close`. Native giữ focus bên trong; helper trả focus về nút mở. Nếu mở sau await, giữ trigger rồi `open(trigger)` để không mất focus.
- Input/button nằm trong fieldset `dialog-controls`; khóa lúc gửi. `busy()` chặn Escape/đóng, khóa cuộn nền; không đóng do click backdrop hoặc lỗi request.

## 2. Bảng và phân trang

`fragments/pagination.html` nhận prefix riêng; `table.js` cung cấp `VietRouteTable`. Dùng cho catalog/users/jobs/journal/media:

```html
<div th:replace="~{fragments/pagination :: pager('catalog', 10)}"></div>
```

```javascript
const pager = window.VietRouteTable.pager('catalog', {
    busy: () => busy,
    onChange: (query) => perform(() => load(query)),
});
// load đọc page/size, dựng bảng rồi pager.update(result) khi thành công.
// perform khóa controls, catch lỗi và mở lại trong finally.
```

Pager có size, khoảng bản ghi/tổng, đầu/trước/tối đa 5 số trang/sau/cuối và ô đến trang; không tạo option cho mọi trang. Danh sách size 10/20/50/100, media 12/24/48/96; UI đếm từ 1, API từ 0, size không quá 100, page không quá giới hạn backend.

Chỉ update pager sau khi tải thành công; lỗi giữ trang/size đã xác nhận. `readPage(fetcher, query)` chỉ đọc lại một lần nếu trang cuối biến mất, dựa vào totalPages của DB. Đổi size/submit lọc về trang đầu, refresh giữ trang/size. Không nối sort/tên cột client vào SQL.

| Thành phần | Quy tắc |
|---|---|
| Cột thao tác | `table-actions` ở th/td, 96px, căn giữa/sticky bên phải khi cuộn ngang; nhiều nút dùng `row-actions` |
| Nút icon | `actionButton(label, icon, handler, danger)`: SVG nội bộ 16px trong nút 32px; edit/delete/view/assign; title/aria-label dịch và có đối tượng |
| Bảng nhỏ/rộng | Cuộn trong `table-responsive` có tabindex/role/aria-label, không làm tràn toàn trang hoặc ép mã xuống từng ký tự |
| Toolbar | `table-filter`/`toolbar-actions`, nút bên phải; ít trường lọc đặt cùng hàng nút như `catalog-toolbar`, chỉ xuống dòng khi thiếu chỗ |
| Footer modal | `dialog-actions`, Hủy trước/Lưu sau, cùng hàng bên phải; mobile được wrap |
| Menu Hệ thống | Các mục cách nhau 6px; vùng hover/focus/mục đang chọn không chạm nhau |

## 3. Validation và xử lý request

`forms.js` cung cấp `VietRouteForms`. Control có id/name/label và required/min/max/minlength/maxlength/pattern đúng hợp đồng; pattern riêng có `data-pattern-message` vi/en. Helper tắt bubble native, hiện lỗi cạnh field, aria-invalid/describedby, giữ hint và focus lỗi đầu tiên.

Dấu `*` đỏ tự lấy từ required ngay khi tải trang, kể cả dialog chưa mở. Nhóm `data-required-selection` đánh dấu legend, không đánh dấu từng checkbox; role có `data-validation-key="roles"` và cần ít nhất một lựa chọn. Dấu trang trí aria-hidden, control giữ required native; reset giữ đúng một dấu. Mã danh mục tự sinh là tùy chọn, không thêm dấu thủ công vào label/bản dịch.

Gọi `VietRouteForms.validate(form)` và lấy FormData **trước khi disable fieldset**. Input lỗi không gửi request; khi sửa, lỗi được tính lại; reset xóa lỗi. `VietRoute.failure` ánh xạ field tường minh và hiện mã lỗi/traceId; không đưa rejected value/HTML/mật khẩu từ server vào DOM. Validation UI không thay kiểm DTO/nghiệp vụ/backend.

- 401: có link đăng nhập lại, giữ bản nháp; không tự redirect/refresh token/tăng hạn JWT.
- 403/409/timeout: feedback có role alert ở modal; giữ nội dung/selection/version, không tự đóng, replay mutation hoặc lấy version mới rồi ghi đè. Timeout chưa chứng minh rollback; xem dữ liệu trước khi gửi lại.
- Hủy/đóng bỏ bản nháp; mở lại đọc bản hiện hành. Mật khẩu reset khi đóng hoặc thành công, không nạp sẵn/ghi URL/log/storage.
- Server xác nhận thành công mới đóng và tải lại danh sách; caller được đóng trong luồng thành công dù busy chưa hạ, người dùng vẫn bị khóa đóng tới khi request kết thúc.
- Lưu quyền trong modal có đối tượng/version không thêm confirm trình duyệt; xóa mềm/retry job giữ xác nhận hiện hành.

Home “Vào hệ thống” và trang login chọn đích từ principal: khách → login, ADMIN → admin, role khác → account; giữ locale/context WAR. Đây là điều hướng, kiểm quyền trực tiếp ở backend vẫn bắt buộc.

Sau POST đổi mật khẩu thành công, client xóa input và tải document `/account` bằng cookie mới, giữ context/locale để server dựng lại toàn bộ menu từ quyền DB. Chỉ gọi lại `/me` sẽ để header mắc ở menu của phiên mật khẩu tạm. Query `passwordChanged=true` chỉ giữ thông báo thành công qua điều hướng; UI chỉ hiện sau `/me` xác nhận tài khoản hết cờ mật khẩu tạm, không dùng query để mở quyền hoặc bỏ AUTH011. Lỗi 401/409/503 vẫn giữ modal/bản nháp và không điều hướng, không tự gửi lại. Chủ tài khoản tự nhập/lưu mật khẩu mới; kiểm thử dùng dữ liệu cách ly, không tự tắt cờ hoặc sửa mật khẩu trên Neon để kiểm UI.

## 4. Bằng chứng và giới hạn

Các đợt UI đã có tại [PR #29](https://github.com/hieuv526034/vietroute-web/pull/29)/[#30](https://github.com/hieuv526034/vietroute-web/pull/30); trạng thái/test theo [nghiệm thu](../05-ke-hoach/NGHIEM_THU_NEN_TANG.md). Java kiểm Home/login với root/servlet tách biệt, khách/ADMIN/CUSTOMER, vi/en và context ROOT/`/vietroute`; Docker CI dùng cookie JWT/PostgreSQL tạm. Node kiểm pager lớn, giới hạn, trang biến mất và lỗi không tự retry.

Browser dùng HTML Thymeleaf thật + API fixture riêng: tab bàn phím, modal/focus, paging/filter, CSRF/version, 409/401 giữ draft, request chậm chặn Escape/đóng, reset mật khẩu khi hủy, dấu bắt buộc trước submit và không nhân đôi sau mở lại. Mobile 390px không tràn ngang toàn trang; bảng quyền cuộn trong dialog. Không thử đổi mật khẩu/gán quyền, gửi Cloudinary hoặc ghi Neon thật để kiểm UI; fixture không thay kiểm SQL/provider, không chứng nhận mọi thiết bị/trình đọc màn hình.

Ảnh dùng dữ liệu kiểm thử, mở khi cần: [menu và toolbar](../images/catalog-toolbar-menu.png), [modal bắt buộc](../images/modal-required.png), [bảng/phân trang](../images/table-pagination.png), [validation](../images/modal-validation.png).


## Điều hướng và trạng thái request — FOUNDATION05

Header tách Hệ thống (danh mục/ảnh/journal/giám sát/job/cấu hình), Phân quyền (role/quyền/user/policy/danh bạ), API (Swagger/OpenAPI) và menu tên/avatar cá nhân. Các mục theo quyền DB/policy, không chỉ theo role mặc định; quản trị phân quyền/journal vẫn cần ADMIN. Khách có đăng nhập/đăng ký; Home thêm quên mật khẩu/xác minh email. Người đã xác thực vào login/account-access được chuyển về workspace/account; header không hiện nút login. Logout dùng POST+CSRF rồi về Home. Menu hỗ trợ một menu mở, Escape/click ngoài/focus; không gộp logout với link public.

UIRESP01: từ 1200px header hiển thị menu ngang; dưới 1200px thanh đầu cao 64px giữ logo, avatar và nút điều hướng 44px, không wrap. Menu chính mở trong panel có chiều cao giới hạn theo viewport và cuộn riêng; submenu thành accordion trong panel, avatar mở menu riêng. `navigation.js` quản lý `hidden`/`aria-expanded`, nhãn vi/en, trả focus khi Escape/resize; Tab ra ngoài hoặc click ngoài đóng menu, không khóa focus/scroll như modal. Không có JavaScript thì các liên kết vẫn hiện để sử dụng. Quyền hiển thị tiếp tục do server lọc; đổi cách trình bày không thêm quyền.

Trang tài khoản dùng `account-layout` cách các card 24px: hồ sơ và khôi phục bootstrap cạnh nhau khi đủ rộng, về một cột dưới 901px. Thông tin trong `dl` chia hai cột, một cột trên điện thoại; nút hồ sơ có footer riêng, khôi phục có tiêu đề/icon và vùng thao tác riêng. Không lặp `id`, không gộp form/modal hoặc thay điều kiện chỉ bootstrap mới thấy cứu hộ.

Hồ sơ có modal sửa tên/ảnh, đổi mật khẩu và gắn email riêng. Ảnh chọn JPEG/PNG gửi qua media dùng chung, không giữ URL/token ở storage. Lỗi giữ bản nháp/ảnh đã upload; 409 cần làm mới dữ liệu có chủ đích. Mọi ảnh là public, không dùng làm giấy tờ xác minh.

request-state.js gắn interceptor Axios để hiển thị thanh tải trang, spinner nút và overlay bảng, đếm đúng nhiều request đồng thời và dọn trên cả success/error. Trạng thái disabled gốc được giữ; các workspace vẫn khóa controls/tabs/modal trong mutation. Single-flight theo method/path chỉ hợp nhất mutation đang chạy, không cache payload hay tự gửi lại sau lỗi; đây không phải idempotency server. Registry/search/refresh chặn bấm lặp; UI không chứng minh server rollback khi mất mạng. Input/select được thu gọn, cột thao tác giữ 96px/icon và toolbar bên phải, reduced-motion tắt animation. Kiểm bằng response chậm/lỗi có kiểm soát; không gửi dữ liệu UI fixture tới provider/Neon.

Heartbeat dùng uiSilent để không nháy loading/mờ bảng khi đọc; CSRF/quota vẫn áp dụng. Popup trên khung hẹp neo theo header để nằm trong viewport. Bằng chứng fixture có dữ liệu giả, không phải ảnh production:

![Menu Hệ thống, Phân quyền, API và tài khoản tách riêng](../images/foundation05-menu.png)

![Nút refresh, bảng và thanh tiến trình khi request chậm](../images/foundation05-loading.png)

## Cây quyền, menu và capabilities — ACCESS03

Header dựng từ AccessFeatures sau khi lọc VIEW bằng cùng guard/policy với API capabilities. Nút dữ liệu dùng actions và target cụ thể, không chỉ kiểm tên role. VIEW-only mở được trang nhưng không gọi API READ; API-only có thể có hành động mà không có menu. Các giới hạn journal/online ADMIN vẫn giữ theo hợp đồng dữ liệu nhạy cảm.

Component access-tree.js giữ một Set cho các bản hiển thị của quyền dùng chung; tìm kiếm không làm mất lựa chọn ngoài kết quả. Nhóm ba trạng thái, chọn trong kết quả hiển thị, gợi VIEW đúng nhánh, preview mã thêm/bỏ/số user snapshot, endpoint ANY/ALL và mã inactive để ADMIN loại bỏ. Dùng details/summary, checkbox/label và search native cho bàn phím; modal giữ focus, vùng cây cuộn riêng và footer bên phải. Lỗi 403/409/timeout không đóng/replay và không làm mất draft. Nguồn quyền của user được tải theo yêu cầu, không lưu lịch sử/quyền/token vào storage.

Ảnh kiểm bằng HTML thật và API fixture cô lập: [cây quyền/preview](../images/access03-tree.png). Không dùng dữ liệu/token production để chụp.

## Lọc resource và loading icon — ACCESS04

Tab Tài nguyên UI/API có select loại + từ khóa + nút Tra cứu/Làm mới trên cùng hàng khi đủ chỗ, co xuống hàng trên khung hẹp và giữ nút bên phải. Tìm mới về trang đầu, phân trang giữ query đã áp dụng. Form/filter dùng validation/loading chung, tìm nguyên văn; backend thực hiện count/filter/paging, không lọc riêng trang dữ liệu tại browser.

Nút icon thao tác 32×32: trong lúc request, spinner tuyệt đối đặt giữa, SVG chỉ ẩn bằng visibility để giữ kích thước. Không chèn spinner cạnh icon làm tràn cột thao tác. API chi tiết về mới mở form; lỗi không mở dữ liệu giả hoặc tự retry. Bộ cây có mẫu chọn quyền hệ thống, xem [hợp đồng/phạm vi](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#bộ-quyền-hệ-thống-và-quyền-db--access04).

Ảnh từ HTML thật/API fixture cô lập, không phải dữ liệu production: [lọc tài nguyên](../images/access04-resources.png), [nút Sửa khi API chậm](../images/access04-loading.png).

## Khu tài khoản và ma trận gán — ACCESS05

`/operations/accounts` tách quản trị danh tính khỏi registry/policy kỹ thuật. Toolbar bên phải, bộ lọc username/role/trạng thái cùng hàng khi đủ chỗ, count đúng bộ lọc và pager server. Bảng hai icon xem/gán trong cột 96px; trạng thái bật/tắt/chờ email/cần đổi mật khẩu phân biệt. Tạo và gán dùng một modal có hai chế độ; role card checkbox nhiều lựa chọn, nút chọn nhanh DRIVER/CUSTOMER, số quyền cấu hình và tóm tắt đã chọn. Dấu bắt buộc có từ khi mở; mật khẩu reset khi đóng/success, không nạp lại từ API hoặc storage. Xem quyền và xác nhận khóa/mở dùng modal riêng, giữ draft khi 403/409/timeout; không tự lấy version mới để ghi đè.

Màn phân quyền có nút Tạo vai trò trực tiếp và đường sang Tài khoản. Ma trận gán giữ một Set như ACCESS03, hàng menu/nhóm và cột hành động dịch vi/en, checkbox một phần, header sticky, chi tiết endpoint khi cần. Toàn bộ menu hiện có xuất hiện trước tìm kiếm; không tạo checkbox giả cho nghiệp vụ chưa có. Giữ VIEW độc lập, quyền dùng chung/WRITE cũ/ngoài registry và preview thay đổi. Vùng ma trận cuộn riêng trên mobile, footer nằm bên phải; tìm nhóm phải bao gồm con, không mất draft khi lọc.

Preview của tài khoản lấy capabilities từ server, không suy từ tên role hoặc raw permission ở trình duyệt. Phiên mật khẩu tạm không hiện chức năng được bảo vệ. HTML kiểm thử lấy từ Thymeleaf thật, API fixture chỉ dữ liệu giả; SQL/bảo mật có bộ kiểm riêng trên PostgreSQL Docker CI.

Ảnh ACCESS05 từ fixture cô lập: [ma trận menu/hành động](../images/access05-matrix.png), [gán nhiều role và giữ bản nháp khi 409](../images/access05-assignment.png), [xem quyền tài khoản](../images/access05-account.png), [danh sách và trạng thái sau khóa](../images/access05-accounts.png).

Ma trận còn hiện từng mã quyền ngoài chức năng hoặc đã tắt đang nằm trong role, để ADMIN có thể bỏ mapping cũ; không giữ quyền vô hình khiến mọi lần Lưu đều bị từ chối. Những dòng này không tự tạo endpoint hoặc VIEW.

## Lịch sử phân quyền — ACCESS09

Tab Lịch sử phân quyền chỉ hiện với ADMIN. Mở tab mới tải API, giữ bộ lọc loại/tiền tố mã/actor khi phân trang hoặc lỗi; bảng dùng pager server và icon 32×32/cột 96px. Modal chỉ đọc actor/trace/time và hai khung trước–sau; delta có giải thích mã bị bỏ/thêm, không hiển thị như snapshot toàn bộ quyền. Tab/phím/focus theo workspace chung. Loading khóa controls, 401/403 xóa dòng cũ khỏi DOM; lỗi không tự replay thao tác ghi. Mặc định 7 ngày/mục tiêu 1000 sự kiện là cấu hình, không hứa audit bất biến.

Kiểm UI bằng `src/test/resources/dev/RenderSystemUi.java` với hai context thật và ConfigurationEnvironment fixture riêng (DB loopback cổng 1, init/sync/worker/provider/journal tắt), xuất HTML vào target/ui-preview; Node `src/test/resources/dev/ui-fixture.cjs` chỉ nghe 127.0.0.1:18119, dữ liệu giả. Fixture không đọc .env/OS credentials hoặc gọi Neon/provider. Đây là kiểm layout/luồng browser, không thay bộ SQL/bảo mật CI. Renderer cũng xuất public webjar assets từ dependency đã có, không tải hay đóng gói tooling vào WAR.

Ảnh dữ liệu giả: [modal lịch sử vi/en và delta](../images/access09-history-vi.jpg). Cách biên dịch renderer: chạy Maven `test-compile dependency:build-classpath -Dmdep.outputFile=target/ui-classpath.txt -DincludeScope=test`, javac với classpath target/test-classes, target/classes và file vừa xuất, rồi chạy RenderSystemUi bằng cùng classpath. Các file xuất/bytecode nằm target hoặc .tools được ignore; source fixture được Git quản lý để máy khác chạy lại.

## Tìm role và registry — ACCESS10

Role ở ma trận/gán tài khoản hiển thị tên theo locale cùng mã, có ô tìm không dấu; role đang chọn và checkbox ngoài kết quả không bị mất. Component role-picker.js dùng chung ở khu Phân quyền/Tài khoản, giới hạn chiều cao danh sách và giữ khả năng bàn phím. Bộ lọc registry loại/từ khóa/trạng thái/nút cùng toolbar khi đủ chỗ, bảng phân trang server và quota riêng không đổi theo bộ lọc. Lỗi giữ bản nháp/version; tìm không cấp quyền và UI không thay guard DB.

Renderer hai context/locale phải không có key dịch thiếu; Node kiểm tìm tên không dấu và không làm mất selection. Browser fixture của lượt ACCESS10 bị URL policy chặn mở lại tab, nên chưa có bằng chứng thao tác trực quan mới cho picker/registry; ảnh ACCESS09 trước đó không được tính là kiểm giao diện mới. Kiểm PostgreSQL phân trang/quota không thay nghiệm thu UX với người dùng thật.

## Quản trị phạm vi — SCOPE01

Tab Phạm vi dữ liệu trong Phân quyền chỉ dành ADMIN. Danh sách tìm/trạng thái/phân trang server; chọn icon thành viên để xem đúng scope, thêm/sửa metadata bằng modal. Modal gán tìm prefix username (tối đa 20 kết quả, thu hẹp từ khóa nếu cần), chọn tài khoản đã có role toàn cục rồi tick các role riêng tại scope. Tên role theo locale kèm mã, tìm giữ draft, trường bắt buộc có dấu đỏ, cột icon 96px/footer bên phải. Không có checkbox giả cho endpoint business chưa triển khai.

Tắt membership/role có version, lỗi giữ modal/draft, không tự nạp version mới hoặc replay. Loading khóa fieldset và chặn đóng modal. Đây là UI admin nền; chưa là màn nhân viên chọn nhà xe hay hành khách đặt/theo dõi chuyến. UI business sau này phải giao quyền scoped với capabilities/policy chung, không lấy quyền A để hiện nút ở B. Browser URL policy còn chặn phiên kiểm; renderer vi/en/validation/SQL không được coi là nghiệm thu trực quan đầy đủ của tab/modal scope.

### Cứu hộ quyền từ trang cá nhân — ACCESS15

Nút Khôi phục quản trị gốc nằm tại /account, chỉ render cho chính username bootstrap từ server, không cần quyền mở trang quản trị. Modal xác nhận mật khẩu, dấu required từ forms.js, chặn submit lặp/đóng khi đang gửi và báo lỗi ngay tại modal. Thành công xóa password khỏi form, tắt submit và hiện link đăng nhập lại vì JWT cũ đã thu hồi. ADMIN khác không có nút; backend vẫn kiểm chính chủ độc lập UI. Không lưu credential vào storage. Xem [hợp đồng cứu hộ](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#cứu-hộ-tài-khoản-bootstrap--access15); kiểm hai context/renderer không thay nghiệm thu trực quan desktop/mobile.

## Menu hẹp, chuyển trang đăng nhập và luồng phân quyền — UI21

Menu dưới 1200px dùng grid một cột `minmax(0, 1fr)` và căn stretch tường minh, không kế thừa căn phải của flex desktop. Link/accordion chiếm chiều rộng panel; menu con nằm trong luồng cuộn của panel. Giữ avatar riêng, Escape/click ngoài/Tab và fallback không JavaScript như UIRESP01.

Đăng nhập phân biệt đang xác thực và đã thành công/đang mở trang. Khi đã nhận cookie thành công, xóa dữ liệu form, giữ nút khóa/spinner và thông báo cho tới khi document chuyển; không mở lại nút trong khoảng chờ trang đích. Lỗi cho sửa/gửi lại có chủ đích, không tự replay. Back/forward cache xóa dữ liệu và giải phóng trạng thái chờ khi khôi phục trang. Thông báo này không bảo đảm thời gian phản hồi DB hoặc bỏ các bước xác minh/mật khẩu tạm.

Phân quyền mặc định hiện Vai trò và quyền, Gán tài khoản, Phạm vi dữ liệu, Lịch sử theo quyền hiện có. Hướng dẫn ba bước và ví dụ dùng danh mục giúp quản trị biết thứ tự tạo vai trò → chọn quyền → gán người dùng. Danh bạ mã quyền, Điều kiện truy cập và Tài nguyên UI/API mở bằng nút Nâng cao; thu gọn tab nâng cao đang chọn thì trở về Vai trò và quyền. Tái sử dụng tab/ARIA/bàn phím của workspace, không lưu trạng thái/quyền vào storage.

Card vai trò đưa hành động chỉnh/xem cùng số quyền và tài khoản lên trước; chi tiết quyền nhóm theo chức năng nằm trong disclosure đóng mặc định. ADMIN/role chỉ đọc dùng nhãn Xem quyền, không làm người dùng tưởng có thể lưu. Modal vẫn giữ ma trận/preview/mã cũ và kiểm tập quyền gán; phần nâng cao chỉ thay cách trình bày, không thay mapping hay guard.

Danh sách gán tài khoản và ba tab nâng cao chỉ gọi API khi được mở; không tải trước vào trang vai trò. Lỗi không đánh dấu đã tải, không tự thử lại; mở lại tab hoặc Làm mới để chủ động tải. Khi refresh, hồ sơ và capabilities được đọc song song từ server, không thêm cache authority hoặc fallback quyền cũ. Provider/Neon và tốc độ production cần nghiệm thu riêng; bằng chứng ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

Ảnh từ HTML thật/API fixture, chỉ dữ liệu giả: [menu điện thoại](../images/ui21-menu-mobile.jpg), [màn vai trò và quyền](../images/ui21-access-desktop.jpg).
## Quản lý địa điểm — LOCATION01

Trang `/operations/locations` có hai tab Địa điểm/Tỉnh thành, dùng workspace.js cho bàn phím/ARIA và modal thêm/sửa. Danh sách tìm/trạng thái/phân trang server qua table.js; cột thao tác 96px/icon aria-label. Modal dùng grid chung, tự về một cột trên mobile, mã/tỉnh của địa điểm đã có bị khóa. Tìm tỉnh tạo địa điểm dùng public lookup tối đa 20 kết quả, có thể tìm tiếp mà giữ lựa chọn ngoài trang; tỉnh tắt không được dùng tạo địa điểm mới. Bật/ngừng sử dụng ngay trong form có version; không có nút xóa dữ liệu/tham chiếu.

Nút và API đọc dựa trên đúng capabilities/target, không suy từ ADMIN hoặc VIEW. Lỗi giữ modal/draft/version, không tự replay; busy khóa form/tab và chặn đóng modal. Thiếu quyền/401 xóa bảng riêng tư, 401 vẫn có link đăng nhập lại và giữ bản nháp. Ghi chú tỉnh/địa điểm tách riêng; trạng thái tỉnh tắt được hiển thị cùng địa điểm quản trị.

Ảnh từ fixture dữ liệu giả, không dùng đặt chuyến: [desktop](../images/location01-desktop.jpg), [modal mobile tiếng Anh](../images/location01-mobile.jpg). Fixture/renderer dùng hai context và env cách ly, không .env/DB/provider; không thay bằng chứng SQL/bảo mật của CI. Trạng thái kiểm và PR nằm ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).


## Menu danh mục và nghiệp vụ — BUSINESS02

Menu Danh mục có Loại xe/Nguồn đặt chỗ/Tỉnh thành/Địa điểm với URL và capabilities riêng. Các màn một nhóm không thêm tab thừa; URL địa điểm cũ giữ hai tab tương thích. Header thu gọn tại 1399px (CSS/navigation.js cùng breakpoint) để chứa đủ các nhóm mới; panel mobile giữ trái/phải 12px, submenu nằm trong panel cuộn. Nút nghiệp vụ dùng `.button`, outline và danger chung, không dùng btn-primary xanh Bootstrap lẫn với xanh VietRoute.

Các màn `/operations/carriers`, `/operations/vehicles`, `/operations/drivers` dùng modal, form hai cột desktop/một cột mobile, server paging và icon/cột thao tác 96px. Bảng phương tiện có min-width 900px và cuộn ngang trong vùng có tên; không bó chữ từng ký tự. Picker nhà xe và loại xe tìm tại server tối đa 20 kết quả, giữ lựa chọn ngoài kết quả. Lỗi/409 giữ draft + version; busy chặn đóng/mutation lặp, focus đặt lại sau khi mở fieldset. `/operations/demo` chỉ seed theo nút tường minh của ADMIN đủ quyền, hiện số tạo mới/giữ nguyên và số lượng từng loại.

Ảnh browser từ app thật/PostgreSQL local cô lập, dữ liệu giả: [menu Danh mục trên điện thoại](../images/business02-menu-mobile.jpg), [bảng phương tiện](../images/business02-vehicles.jpg). Không dùng ảnh để suy đã triển khai hoặc đã seed Neon; bằng chứng vận hành ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

## Tuyến và thứ tự điểm — ROUTE01

`/operations/routes` dùng VIEW độc lập READ, phân trang server và modal hai tab Thông tin/Điểm dừng. Mã/nhà xe giữ bất biến khi sửa. Điểm có select tìm qua lookup, giờ lệch phút, giá cộng dồn, quyền đón/trả và nút lên/xuống/bỏ; nhóm điểm có nhãn ARIA theo thứ tự. READ mở chi tiết chỉ đọc, UPDATE scoped mới có sửa. Lỗi nghiệp vụ/version/401 giữ modal và draft; không tự gửi lại. Footer bên phải, busy chặn đóng, focus giữ trong dialog. Hai ngôn ngữ đã kiểm từ app thật; [tab điểm desktop](../images/route01-stops.jpg), [modal 390px](../images/route01-mobile.jpg) dùng dữ liệu PostgreSQL preview cô lập.
