# Chạy local và tài khoản dùng thử

Đọc [tổng quan](TONG_QUAN_HE_THONG.md), làm theo các bước dưới rồi vào [phần tên mình](../05-ke-hoach/PHAN_CONG.md). Không ghi giá trị mật khẩu vào tài liệu/ảnh; .env.example có password/key được chủ dự án cho phép, riêng mật khẩu DB lấy qua kênh riêng.

## Nút Run/Debug trong IntelliJ

1. Cài Git/JDK21/Node22, import [pom.xml](../../pom.xml); Java target17, Spring MVC/WAR, không Spring Boot. Dùng Maven Wrapper của repo.
2. Copy [.env.example](../../.env.example) thành .env nếu chưa có, điền riêng VIETROUTE_DB_PASSWORD đang là XXX. Các password/key khác giữ đầy đủ theo yêu cầu chủ dự án. Không ghi đè file local đang dùng; .env và các .env.* khác bị ignore Git, lịch sử cũ chưa viết lại.
3. Bật Maven profile local, tắt production, Reload Maven. Chọn VietRoute Local trong [.run](../../.run/VietRoute%20Local.run.xml), working directory là repo, bật dependencies provided nếu IDE yêu cầu.
4. Run/Debug rồi mở http://localhost:8080/login?lang=vi. Launcher bind127.0.0.1; port qua VIETROUTE_HTTP_PORT. Stop đóng context/pool. Java cần Build/Restart, không hứa hot reload.
5. Kiểm /health rồi login và API có DB; health200 chỉ liveness. Thiết lập [formatter](FORMAT_INTELLIJ.md) trước sửa source. Kịch bản trình bày tại [demo–hỏi đáp](../05-ke-hoach/DEMO_VA_HOI_DAP.md).

Run config dùng -Duser.timezone=Asia/Ho_Chi_Minh giống Docker; máy chủ mới không nhận alias Asia/Saigon của một số JVM Windows. Nếu chạy main VietRouteApplication ngoài Run config cũng đặt VM option này. Production không đóng launcher/Tomcat nhúng hoặc đọc file .env.

## Cấu hình môi trường

Launcher chọn VIETROUTE_LOCAL_ENV_FILE từ OS/IDE hoặc system property trước, thiếu thì root .env. Giá trị OS/IDE ưu tiên file, cuối cùng properties mẫu. Không cần khai báo LOCAL_ENV_FILE rỗng trong file gốc. Log chỉ công bố nguồn OS_IDE/LOCAL_FILE/DEFAULT, không giá trị; sai DB thường do biến IDE cũ ghi đè file.

DB hiện hành hieuboy/schema vietroute; URL/user/schema trong postgresql.properties khớp môi trường hiện hành; env vẫn được ưu tiên. Máy chủ chưa TLS, chủ dự án đã chấp thuận riêng sslmode=disable cho đích local/Render này; không dùng thành mặc định chung. .env local/Render giữ password, không properties/Git/log. Local dùng development/HTTP cookie không Secure theo launcher trừ khi OS/IDE override rõ ràng; Render production Secure.

VIETROUTE_DB_VENDOR nhận postgresql/oracle; thiếu suy từ URL rồi db.vendor. URL/vendor phải khớp. Schema chỉ VIETROUTE_DB_SCHEMA/fallback profile, không hardcode vào entity/SQL. Hai profile [database](../../src/main/resources/database) có password trống; .env.example giống local, duy nhất VIETROUTE_DB_PASSWORD=XXX. Production yêu cầu env password tường minh. Oracle26ai chỉ có driver/profile chuẩn bị, runtime bị chặn dù init tắt; không thử chuyển DB bằng cách chỉ đổi URL.

Root .env cần DB, JWT/bootstrap, cờ init và DEFAULT_CUSTOMER/DRIVER/OPERATOR_EMAIL/PASSWORD mà JSON tham chiếu. Mật khẩu DB khác mật khẩu app. Maven/test không đọc root .env; local kết nối DB thật thì CRUD có tác động thật, không dùng để kiểm build hoặc tạo fixture.

Locale mặc định tiếng Việt, không theo Accept-Language của browser. Chọn tiếng Anh qua nút ngôn ngữ (?lang=en) hoặc X-Locale: en.

## Khởi tạo PostgreSQL

DB trắng đã được phép: điền schema/JWT/bootstrap và cùng bật4 cờ. DB đã có history khác phải theo [backup/rebaseline](../04-van-hanh/VAN_HANH.md#database-backup-và-rollback), không reset để chạy lại.

```dotenv
VIETROUTE_DB_INIT_ENABLED=true
VIETROUTE_ACCESS_SYNC_ENABLED=true
VIETROUTE_DEFAULT_ACCOUNTS_ENABLED=true
VIETROUTE_BUSINESS_INIT_ENABLED=true
```

Thứ tự: baseline/bootstrap/account → danh mục/nhà xe/xe/hồ sơ → scope/account liên kết → tuyến/chuyến/snapshot/segment/đơn/event. Chỉ startup khi bật có chủ đích, không lúc Maven package. Seed giữ hash, trạng thái, mapping/thu hồi và ngày/mã cũ; ordinary init một lần, không tự tạo lại cửa sổ mới mỗi restart.

## Tài khoản và dữ liệu khởi tạo

Đây là dữ liệu giả lập quan hệ/lịch/giá/chỗ/tiền/history, không hoạt động thương mại đã xác minh. Bộ đầy đủ trên DB mới có 46 account sử dụng (+system0 không đăng nhập),7 nhà xe,25 xe/25 tài xế,34 tỉnh/thành,3.321 xã/phường và14 địa điểm đón/trả,28 mẫu tuyến,646 chuyến,3866 đơn (3840 PHONE/26 WEB). Có các trạng thái và tình huống thu/hoàn để học, không chỉ số lượng ngẫu nhiên.

Chỉ login username/password. Account cũ giữ mật khẩu đã đổi, env không là công cụ reset. Seed ordinary tối thiểu8 ký tự/tối đa 72 byte BCrypt theo ngoại lệ đã chốt; admin bootstrap/tạo/đổi password giữ độ phức tạp. Không lưu credential vào slide hoặc chia sẻ terminal.

| Nhóm | Username | Key password lúc tạo mới |
|---|---|---|
| Quản trị | admin hoặc BOOTSTRAP_ADMIN_USERNAME | VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD |
| Khách | custom01…custom12, customduylong | VIETROUTE_DEFAULT_CUSTOMER_PASSWORD |
| Tài xế | driver01…driver24, driverduylong | VIETROUTE_DEFAULT_DRIVER_PASSWORD |
| Điều hành | operator01…operator06, operatorduylong | VIETROUTE_DEFAULT_OPERATOR_PASSWORD |

Email custom01/driver01/operator01 từ DEFAULT_*_EMAIL; các email bổ sung thuộc miền giả lập, không chứng minh có inbox/gửi thư. Tra account/key không đọc DB hoặc .env bằng lệnh mặc định:

```powershell
python -X utf8 scripts/development/list-init-accounts.py
```

Chỉ trên terminal riêng, thêm --show-passwords mới đọc/in giá trị từ OS/.env; không chụp/redirect output vào log/PR. Đó chỉ là password khởi tạo, không lấy được password đã đổi trong DB.

### Danh sách từng tài khoản

| Username | Tên hiển thị lúc tạo mới | Role | Phạm vi / hồ sơ |
|---|---|---|---|
| admin (hoặc BOOTSTRAP_ADMIN_USERNAME đã cấu hình) | Quản trị viên | ADMIN | Toàn hệ thống theo grant |
| custom01 | Nguyễn Thu Hà | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom02 | Trần Minh Ngọc | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom03 | Phạm Hoàng Anh | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom04 | Lê Thanh Trúc | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom05 | Võ Bảo Châu | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom06 | Đỗ Ngọc Mai | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom07 | Bùi Đức Huy | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom08 | Ngô Minh Tâm | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom09 | Hoàng Quỳnh Anh | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom10 | Đặng Văn Phúc | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom11 | Phan Thảo Vy | CUSTOMER | Đơn đặt chỗ của chính mình |
| custom12 | Mai Hồng Nhung | CUSTOMER | Đơn đặt chỗ của chính mình |
| driver01 | Quách Tĩnh | DRIVER | Nhà xe Thiên Hành / DRVSOUTH01 |
| driver02 | Dương Quá | DRIVER | Nhà xe An Phúc / DRVNORTH01 |
| driver03 | Lệnh Hồ Xung | DRIVER | Nhà xe Thiên Hành / DRVSOUTH02 |
| driver04 | Trương Vô Kỵ | DRIVER | Nhà xe Thiên Hành / VRDRV03 |
| driver05 | Tiêu Phong | DRIVER | Nhà xe Thiên Hành / VRDRV04 |
| driver06 | Đoàn Dự | DRIVER | Nhà xe An Phúc / DRVNORTH02 |
| driver07 | Hư Trúc | DRIVER | Nhà xe An Phúc / VRDRV07 |
| driver08 | Thạch Phá Thiên | DRIVER | Nhà xe An Phúc / VRDRV08 |
| driver09 | Hồ Phỉ | DRIVER | Nhà xe Thanh Vân / VRDRV09 |
| driver10 | Vi Tiểu Bảo | DRIVER | Nhà xe Thanh Vân / VRDRV10 |
| driver11 | Trần Gia Lạc | DRIVER | Nhà xe Thanh Vân / VRDRV11 |
| driver12 | Viên Thừa Chí | DRIVER | Nhà xe Thanh Vân / VRDRV12 |
| driver13 | Trương Tam Phong | DRIVER | Nhà xe Đại Ngàn / VRDRV13 |
| driver14 | Hoàng Dược Sư | DRIVER | Nhà xe Đại Ngàn / VRDRV14 |
| driver15 | Hồng Thất Công | DRIVER | Nhà xe Đại Ngàn / VRDRV15 |
| driver16 | Chu Bá Thông | DRIVER | Nhà xe Đại Ngàn / VRDRV16 |
| driver17 | Vương Trùng Dương | DRIVER | Nhà xe Ngọc Hà / VRDRV17 |
| driver18 | Đoàn Trí Hưng | DRIVER | Nhà xe Ngọc Hà / VRDRV18 |
| driver19 | Khưu Xử Cơ | DRIVER | Nhà xe Ngọc Hà / VRDRV19 |
| driver20 | Mã Ngọc | DRIVER | Nhà xe Ngọc Hà / VRDRV20 |
| driver21 | Tống Viễn Kiều | DRIVER | Nhà xe Trường Phong / VRDRV21 |
| driver22 | Du Liên Châu | DRIVER | Nhà xe Trường Phong / VRDRV22 |
| driver23 | Du Đại Nham | DRIVER | Nhà xe Trường Phong / VRDRV23 |
| driver24 | Mạc Thanh Cốc | DRIVER | Nhà xe Trường Phong / VRDRV24 |
| operator01 | Nguyễn Phương Linh | OPERATOR | Nhà xe Thiên Hành |
| operator02 | Trần Quốc Việt | OPERATOR | Nhà xe An Phúc |
| operator03 | Lê Ngọc Hân | OPERATOR | Nhà xe Thanh Vân |
| operator04 | Phạm Minh Thư | OPERATOR | Nhà xe Đại Ngàn |
| operator05 | Võ Thanh Bình | OPERATOR | Nhà xe Ngọc Hà |
| operator06 | Đỗ Bảo Ngân | OPERATOR | Nhà xe Trường Phong |
| driverduylong | Phạm Thành Lương | DRIVER | Nhà xe Duy Long / DLGLUONG |
| operatorduylong | Điều hành Duy Long | OPERATOR | Nhà xe Duy Long |
| customduylong | Khách hàng Duy Long | CUSTOMER | Đơn của chính mình |

## Phạm vi bộ dữ liệu demo

Bảy nhà xe: Thiên Hành, An Phúc, Thanh Vân, Đại Ngàn, Ngọc Hà, Trường Phong và Duy Long. Sáu tên đầu là tên giả định riêng; 24 tài xế dùng tên nhân vật kiếm hiệp Kim Dung, không là hồ sơ người thật. Duy Long/Phạm Thành Lương giữ theo đầu vào chủ dự án. Mã kỹ thuật VRMIENNAM/VRBACNAM… giữ để không đổi liên kết, username hoặc scope; tên hiển thị lấy từ dữ liệu. Bảng account phía trên là tên lúc khởi tạo mới; đổi JSON không tự ghi đè tên trên DB đã có.

Bộ demo phục vụ toàn hệ thống với nhiều nhà xe, địa phương, tuyến, loại xe, tài xế, lịch chạy, khách hàng và trạng thái đơn/thu–hoàn. Duy Long là một nhà xe trong bộ dữ liệu này. Khi bổ sung tiếp, mở rộng đa dạng các đơn vị và tình huống nghiệp vụ, giữ mã ổn định, scope/owner đúng và seed idempotent; không lấy Duy Long làm mặc định chung hoặc giới hạn nghiệp vụ quanh một nhà xe. Số lượng mới bổ sung theo từng đợt được nghiệm thu, không suy rằng các dữ liệu dự kiến đã có trong DB.

## Nhà xe Duy Long

Theo yêu cầu chủ dự án: Nhà xe Duy Long tại Hải Phòng, xe16 chỗ DLG16, tài xế Phạm Thành Lương (DLGLUONG), hai vòng khứ hồi/ngày. Khởi tạo thêm152 chuyến trong38 ngày (7 ngày trước đến30 ngày sau lần tạo nhà xe), bốn lượt mỗi ngày, cùng một xe/tài xế và không chồng lịch.

| Lượt | Chiều | Giờ Việt Nam | Mẫu tuyến |
|---|---|---|---|
| Sáng đi | Hải Phòng → Hà Nội | 05:00–09:00 | DLGHPHNSANG |
| Sáng về | Hà Nội → Hải Phòng | 09:30–11:30 | DLGHNHPSANG |
| Chiều đi | Hải Phòng → Hà Nội | 12:00–15:00 | DLGHPHNCHIEU |
| Chiều về | Hà Nội → Hải Phòng | 15:30–17:30 | DLGHNHPCHIEU |

Lượt về sáng09:30–11:30 là giả định bổ sung, chừa30 phút tại mỗi đầu giữa hai lượt. Hai chiều có bốn mẫu tuyến do thời lượng sáng/chiều khác nhau; mỗi chuyến có hai điểm đầu/cuối, snapshot và một segment riêng. Mã chuyến dạng DLG1R25DyyyyMMdd (slot25…28), neo ngày theo created_at nhà xe ở Asia/Ho_Chi_Minh, không dịch ngày khi restart.

Giá cấu hình180.000đ/chỗ/một lượt;2 chỗ360.000đ. Biển số15B-000.16, điện thoại, điểm đón, tọa độ và giá do dự án tạo để nghiệm thu, không xác nhận liên hệ/địa chỉ/lịch khai thác thương mại. Chỉ tên nhà xe, thành phố, tài xế, sức chứa và các giờ đã nêu là đầu vào của chủ dự án. Dữ liệu nguồn và giả định nằm ở provenance.duyLong trong operations.json.

Đăng nhập operatorduylong để xem chuyến/đơn/thu–hoàn/báo cáo đúng nhà xe; driverduylong chỉ thấy chuyến được phân công; customduylong xem đơn cá nhân, tìm theo cặp DLGHAIPHONG/DLGHANOI. Password dùng key theo role ở bảng trên, không thay tài khoản cũ. Khách đã có một WEB active thì xử lý đơn đó trước khi đặt mới. Tìm Hải Phòng → Hà Nội có hai lượt đi/ngày, đổi chiều có hai lượt về; chọn ngày tương lai nếu hôm nay đã qua17:30. Thử16 chỗ chỉ thành công khi còn đủ chỗ, không mặc định xe16 chỗ luôn còn16 chỗ.

DB mới có tổng646 chuyến/3866 đơn theo snapshot đã kiểm. DB đang có dữ liệu chỉ thêm phần thiếu; tổng đơn có thể cao hơn vì giữ dữ liệu cũ và bổ sung lịch sử WEB cho khách, không reset để ép về số DB mới. Khi hết cửa sổ ngày, điều hành tạo chuyến mới bằng chức năng hiện hành.

### Chọn chuyến đúng ngày

Ngày seed neo lần tạo đầu:7 ngày trước đến14 ngày sau, tuyến dài cách 4 ngày. VRINIT1R01D07 của driver01 được tạo OPEN khoảng 10 phút sau init đầu tiên; đừng mặc định mã đó luôn tương lai khi demo ngày khác. Khi hết cửa sổ cần điều hành tạo/mở chuyến mới bằng luồng hợp lệ, không dịch clock hoặc xóa/reset DB.

Nguồn account ở [default-access.json](../../src/main/resources/auth/default-access.json), nền/profile ở [bootstrap](../../src/main/resources/bootstrap). Nút /operations/demo và POST /api/v1/admin/demo chỉ thêm bộ nền21 bản ghi, không tạo account/membership hoặc hàng nghìn chuyến/đơn qua HTTP. Luồng tập chung và tài khoản từng người nằm trong [phân công](../05-ke-hoach/PHAN_CONG.md) và [kịch bản demo](../05-ke-hoach/DEMO_VA_HOI_DAP.md).
