# Chạy Spring MVC bằng IntelliJ

Đọc [tổng quan](TONG_QUAN_HE_THONG.md), thiết lập [formatter](FORMAT_INTELLIJ.md), rồi import Maven bằng JDK 21. Stack Java 17+/Spring MVC/JPA/JDBC/Thymeleaf, WAR/Tomcat 10.1; không Spring Boot.

## Nút Run/Debug trong IntelliJ

1. Mở repo, import pom.xml, chọn Project SDK 21.
2. Maven Profiles: bật local, tắt production, Reload All Maven Projects. src/dev/java là Sources Root và Tomcat nhúng ở scope provided.
3. Chọn VietRoute Local trong cấu hình chia sẻ .run/VietRoute Local.run.xml. Working directory là repo; chọn đúng module và bật dependencies provided nếu IDE yêu cầu.
4. Run/Debug, mở http://localhost:8080/login?lang=vi. Breakpoint tại controller/service; Stop đóng server/context/pool. Có thể chạy main của src/dev/java/vn/vietroute/dev/VietRouteApplication.java nếu cấu hình chưa hiện.

Launcher chỉ bind loopback, VIETROUTE_HTTP_PORT mặc định 8080. Java cần Build/Restart sau sửa, không hứa hot reload. Production WAR loại launcher/tomcat-embed; Docker chỉ copy src/main.

## Cấu hình và database

Launcher chủ động đọc root .env theo working directory; VIETROUTE_LOCAL_ENV_FILE chọn file khác. OS/IDE ưu tiên file, sau cùng properties mặc định. Root .env và .env.* chứa credential chỉ giữ local, không theo dõi Git theo yêu cầu mới02/11/2026. Khi clone máy mới, copy [.env.example](../../.env.example) thành .env rồi điền password/JWT/bootstrap riêng; mẫu có địa chỉ DB nhưng không mật khẩu thật. Không in giá trị hoặc đưa secret vào .run/docs/image. WAR/Tomcat/Render chỉ nhận biến môi trường, không tự đọc file. Launcher dùng chế độ development/HTTP nếu không có OS/IDE override rõ ràng.

VIETROUTE_LOCAL_ENV_FILE phải đặt ở OS/IDE hoặc system property trước khi launcher chọn file; không cần dòng rỗng trong root .env. File gốc chỉ giữ key còn dùng, nhóm DB/môi trường/JWT-bootstrap/init/account/quyền/journal. Các key email/password của DEFAULT_CUSTOMER/DRIVER/OPERATOR vẫn cần vì JSON seed tham chiếu; chỉ dùng khi tạo account còn thiếu. Key retention/capacity lịch sử quyền, policy và đăng ký/self-service đã bỏ không có tác dụng, không thêm lại từ cấu hình cũ.

Maven/test không đọc root .env. Surefire dùng env/DB cô lập; chạy JUnit trực tiếp cũng không gán env production vào cấu hình test. Local UI kết nối DB hieuboy theo cấu hình thật thì CRUD sẽ tác động dữ liệu thật; không dùng nó để kiểm build hoặc tạo fixture.

## Khởi tạo môi trường

### Chọn PostgreSQL hoặc chuẩn bị Oracle 26ai

Đây là cấu hình Spring MVC thuần, không dùng cơ chế application-*.properties của Spring Boot. Chọn hệ DB độc lập với VIETROUTE_ENV=development/production và Maven profile local/production:

```text
src/main/resources/
  database.properties             # db.vendor=postgresql mặc định
  database/postgresql.properties  # URL/user/schema mẫu PostgreSQL
  database/oracle.properties      # URL/user/schema mẫu Oracle 26ai
```

VIETROUTE_DB_VENDOR nhận đúng postgresql hoặc oracle. Nếu bỏ key này, DatabaseSettings suy từ VIETROUTE_DB_URL hiện có; thiếu cả hai mới chọn db.vendor. OS/IDE và file local vẫn ưu tiên theo quy tắc trên, sau đó mới tới giá trị mẫu của profile. URL phải khớp vendor; tên không hợp lệ bị từ chối, không dùng tên profile tùy ý làm đường dẫn. Hai profile khai báo db.password= trống; VIETROUTE_DB_PASSWORD từ OS/IDE/.env ưu tiên, chỉ development mới được dùng fallback properties. Production yêu cầu password môi trường tường minh, không fallback credential classpath. Không điền secret thật vào properties được đóng WAR/image. Root .env giữ kết nối PostgreSQL; không tự chuyển PostgreSQL sang Oracle. Mật khẩu DB độc lập mật khẩu tài khoản admin/khách/tài xế/điều hành.

| Thiết lập | PostgreSQL hiện hành | Oracle 26ai, cấu hình chuẩn bị |
|---|---|---|
| VIETROUTE_DB_VENDOR | postgresql | oracle |
| VIETROUTE_DB_URL mẫu | jdbc:postgresql://localhost:5432/vietroute | jdbc:oracle:thin:@//localhost:1521/FREEPDB1 |
| VIETROUTE_DB_USER mẫu | vietroute_app | VIETROUTE_APP |
| VIETROUTE_DB_SCHEMA mẫu | vietroute | VIETROUTE_APP |
| JDBC driver | org.postgresql.Driver | oracle.jdbc.OracleDriver; ojdbc17 23.26.4.0.0 |
| Hibernate dialect | PostgreSQLDialect | OracleDialect đã khai báo, chưa bật JPA/runtime Oracle |
| Chạy toàn bộ ứng dụng | Có, với baseline PostgreSQL | Chưa: dừng trước schema/repository/initializer |

Oracle URL dùng SERVICE_NAME của PDB do DBA cung cấp; FREEPDB1 chỉ là ví dụ, không suy ra service từ phiên bản 26ai hoặc tự tạo PDB/user. Schema Oracle thường viết hoa, schema PostgreSQL viết thường; ứng dụng không tự đổi chữ. Với môi trường Oracle mới, chuẩn bị credential riêng và giữ DB_INIT/ACCESS_SYNC/DEFAULT_ACCOUNTS/BUSINESS_INIT tắt trong khi chưa có bộ SQL Oracle. Dù tắt cả bốn cờ, runtime vẫn chặn Oracle vì các repo hiện dùng SQL PostgreSQL. Việc thêm driver không chuyển đổi hoặc sao chép dữ liệu giữa hai DB.

Oracle dùng JDBC Thin không yêu cầu cài Oracle Client; ojdbc17 phù hợp JDK 17/21 theo [tài liệu tương thích Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/jjdbc/JDBC-getting-started.html). VIETROUTE_DB_CONNECT_TIMEOUT_SECONDS và SOCKET_TIMEOUT_SECONDS vẫn nhập bằng giây; adapter đổi thành mili giây cho oracle.net.CONNECT_TIMEOUT, OUTBOUND_CONNECT_TIMEOUT và oracle.jdbc.ReadTimeout, không dùng key pgJDBC trên Oracle. Tham khảo [thuộc tính kết nối Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/26/jajdb/oracle/jdbc/OracleConnection.html). Hikari vẫn tạo lười và không mở connection chỉ để nạp cấu hình.

Để bật Oracle thật cần bộ migration Oracle riêng, chuyển các query JSONB/array/ON CONFLICT/RETURNING/advisory lock và cơ chế schema/transaction, sau đó kiểm JDBC/JPA chung rollback, scope/quyền, tồn chỗ và init idempotent trên Oracle 26ai. Chưa có kết nối Oracle thật trong lượt chuẩn bị này. Không dùng H2 hoặc test driver nhận URL làm bằng chứng Oracle đã chạy nghiệp vụ. Trạng thái kiểm ở [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

### Khởi tạo PostgreSQL

Database trắng: điền DB/schema/JWT và VIETROUTE_BOOTSTRAP_ADMIN_* trong env của môi trường; bật DB_INIT có chủ đích để chạy [baseline tám bước](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#baseline-theo-cụm-bảng). Database đã có history khác phải backup/kiểm phục hồi, dừng node và đổi baseline bằng công cụ bảo trì trước; runtime không tự reset. Không bật initializer DB thật chỉ để thử launcher.

ACCESS_SYNC thu thập endpoint sau MVC refresh và đồng bộ bootstrap khi bật; không thay migration. DEFAULT_ACCOUNTS_ENABLED tạo 42 account theo JSON, dùng email/password DEFAULT_CUSTOMER/DRIVER/OPERATOR cho ba nhóm; BUSINESS_INIT_ENABLED tạo bộ vận hành đầy đủ và gán phạm vi/hồ sơ một lần. Bảng đăng nhập và luồng dùng thử nằm ở phần dưới. Các cờ opt-in không đặt lại password hoặc phục hồi ordinary grant/membership đã thu hồi. Chi tiết tại [bootstrap](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#khởi-tạo-và-bảo-vệ-bootstrap).

Nguồn cấu hình DB có log urlSource/userSource/passwordSource, chỉ LOCAL_FILE/OS_IDE/DEFAULT, không có giá trị. Khi đổi source, Stop server cũ → clean build → Reload Maven → Run. Không chạy hai server cùng port. Profile production không chứa dev launcher/test.

## Dùng thử luồng nghiệp vụ

Khởi tạo tài khoản/danh mục/nhà xe → lập tuyến → mở chuyến và phân công → khách tìm theo tuyến/ngày rồi đặt chỗ → điều hành xác nhận/thu hoặc hoàn → tài xế xác nhận khởi hành/đến/rời điểm/hoàn tất → xem báo cáo. Dữ liệu nền do dự án xây dựng theo yêu cầu, cần thay bằng thông tin đơn vị khi vận hành; không giả là lịch chạy đã được xác minh.

Không cần GPS/mobile/provider Maps hoặc credential upload/mail. Tọa độ tĩnh và link địa điểm không theo dõi thiết bị. Nhật ký tại /operations/api-journal cần grant page/API chính xác, xem [debug](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md). /health chỉ liveness; cần login/me và API có DB để nghiệm thu kết nối.


## Tài khoản và dữ liệu khởi tạo

Bộ này giả lập vận hành, có tên/địa chỉ/liên hệ/lịch sử theo nghiệp vụ; không xác nhận đơn vị vận tải hoặc lịch chạy ngoài thực tế. Thông tin khởi tạo không có chữ DEMO trong tên hiển thị. Các số dưới đây là khi tạo trên database mới đủ cấu hình; môi trường đã có dữ liệu giữ nguyên phần người dùng đã sửa và không bị ép về đúng số lượng mẫu.

| Dữ liệu | Số lượng trên DB mới |
|---|---:|
| Tài khoản đăng nhập | 43: admin, 12 khách, 24 tài xế, 6 điều hành |
| Nhà xe | 6 |
| Xe / hồ sơ tài xế có tài khoản | 24 / 24 |
| Tỉnh/thành / điểm đón trả | 12 / 12 |
| Tuyến một chiều | 24 |
| Chuyến | 494 |
| Đơn đặt chỗ | 2.952, gồm 2.928 PHONE và 24 WEB |

Có đủ OPEN/DRAFT/IN_PROGRESS/COMPLETED/CANCELLED, đơn PENDING/CONFIRMED/COMPLETED/CANCELLED/REJECTED, thu đủ/thu một phần/đã hoàn/còn phải hoàn. Tồn chỗ và lịch sử tiền được tạo cùng snapshot, không chỉ đổ số lượng vào bảng. Mỗi xe/tài xế có lịch riêng; tài xế và điều hành chỉ thấy dữ liệu được giao/đúng nhà xe, khách chỉ thấy đơn của mình.

### Bật bộ khởi tạo đầy đủ

Đặt đúng môi trường PostgreSQL dự định dùng và điền các key credential trong .env hoặc OS/IDE. Cần đồng thời:

```dotenv
VIETROUTE_DB_INIT_ENABLED=true
VIETROUTE_ACCESS_SYNC_ENABLED=true
VIETROUTE_DEFAULT_ACCOUNTS_ENABLED=true
VIETROUTE_BUSINESS_INIT_ENABLED=true
```

Khởi tạo theo thứ tự: baseline + bootstrap/42 accounts → danh mục/nhà xe/xe/hồ sơ → scope và liên kết tài xế → tuyến/chuyến/snapshot/chỗ/đơn/sự kiện. Seed nghiệp vụ cùng transaction, theo tập và khóa advisory; không query/save từng bản ghi. Nếu tắt DEFAULT_ACCOUNTS, chỉ có dữ liệu nền/tuyến nền, không tạo chuyến/đơn thiếu account.

Ngày chuyến được tính lúc tạo đầu tiên: 7 ngày trước đến 14 ngày sau; tuyến dài cách bốn ngày để không chồng lịch. Mã ổn định theo profile/tuyến/offset, nên restart không dịch ngày hoặc nhân thêm cửa sổ chuyến. Hết khoảng ngày này, lập chuyến mới bằng màn điều hành; không tự làm mới bằng cách xóa/reset dữ liệu. Chuyến hiện tại của driver01 có mã VRINIT1R01D07, OPEN và giờ xuất phát khoảng 10 phút sau thời điểm init; có thể chọn để tập khởi hành rồi đến/rời điểm.

### Tra mật khẩu đăng nhập

Chỉ dùng username và mật khẩu để đăng nhập (ví dụ admin, custom01, driver01, operator01). Không dùng địa chỉ email thay username; email chỉ là thông tin của tài khoản.

| Nhóm | Username | Key mật khẩu khởi tạo |
|---|---|---|
| Quản trị | admin hoặc username đã cấu hình | VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD |
| Khách | custom01…custom12 | VIETROUTE_DEFAULT_CUSTOMER_PASSWORD |
| Tài xế | driver01…driver24 | VIETROUTE_DEFAULT_DRIVER_PASSWORD |
| Điều hành | operator01…operator06 | VIETROUTE_DEFAULT_OPERATOR_PASSWORD |

Tài khoản mới cùng nhóm dùng giá trị key tương ứng tại lần tạo. Email của custom01/driver01/operator01 lấy ba key DEFAULT_*_EMAIL; email các account bổ sung là định danh thuộc miền dành cho dữ liệu giả lập vietroute.example, không phải inbox để gửi thư. Account đã tồn tại giữ password/trạng thái/tên đã chỉnh; key .env không chứng minh password hiện tại nếu đã đổi trong DB. Seed không mở khóa, bật lại user hoặc khôi phục role/scope/link đã thu hồi.

Theo cấu hình chủ dự án, admin bootstrap dùng key riêng; ba nhóm tài khoản mặc định dùng ba key cùng giá trị đã đặt trong root .env. Mật khẩu seed ordinary chấp nhận tối thiểu 8 ký tự và tối đa 72 byte BCrypt, cho phép định dạng đã được chủ dự án chọn. Ngoại lệ chỉ nằm ở AuthInitializer; admin bootstrap, API quản trị tạo tài khoản và đổi mật khẩu vẫn yêu cầu chữ hoa/chữ thường/chữ số/ký tự đặc biệt. Chỉnh key seed không reset mật khẩu tài khoản đã tồn tại, kể cả restart.

Không cần tìm mật khẩu trong nhiều file. Dùng công cụ sau ở terminal cá nhân; mặc định chỉ liệt kê key, không đọc .env. Chỉ tùy chọn rõ ràng mới đọc credential từ OS/.env, không kết nối DB và không ghi ra file:

```powershell
python -X utf8 scripts/development/list-init-accounts.py
python -X utf8 scripts/development/list-init-accounts.py --show-passwords
```

Không chuyển output --show-passwords vào ảnh/log/PR. Tài liệu giữ key thay giá trị để mật khẩu đang dùng chỉ có một nguồn cấu hình. Đây là mật khẩu khởi tạo, không công cụ lấy hoặc reset password đã đổi.

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
| driver01 | Nguyễn Minh Tuấn | DRIVER | VietRoute Miền Nam / DRVSOUTH01 |
| driver02 | Lê Hoàng Nam | DRIVER | VietRoute Bắc Nam / DRVNORTH01 |
| driver03 | Trần Quốc Huy | DRIVER | VietRoute Miền Nam / DRVSOUTH02 |
| driver04 | Lê Hoàng Nam | DRIVER | VietRoute Miền Nam / VRDRV03 |
| driver05 | Phạm Đức Anh | DRIVER | VietRoute Miền Nam / VRDRV04 |
| driver06 | Phạm Đức Anh | DRIVER | VietRoute Bắc Nam / DRVNORTH02 |
| driver07 | Bùi Trung Kiên | DRIVER | VietRoute Bắc Nam / VRDRV07 |
| driver08 | Võ Minh Khoa | DRIVER | VietRoute Bắc Nam / VRDRV08 |
| driver09 | Lê Quốc Bảo | DRIVER | VietRoute Miền Trung / VRDRV09 |
| driver10 | Nguyễn Hữu Phúc | DRIVER | VietRoute Miền Trung / VRDRV10 |
| driver11 | Trần Minh Hải | DRIVER | VietRoute Miền Trung / VRDRV11 |
| driver12 | Phạm Công Thành | DRIVER | VietRoute Miền Trung / VRDRV12 |
| driver13 | Hoàng Văn Long | DRIVER | VietRoute Tây Nguyên / VRDRV13 |
| driver14 | Đỗ Anh Dũng | DRIVER | VietRoute Tây Nguyên / VRDRV14 |
| driver15 | Ngô Đức Thắng | DRIVER | VietRoute Tây Nguyên / VRDRV15 |
| driver16 | Vũ Tuấn Anh | DRIVER | VietRoute Tây Nguyên / VRDRV16 |
| driver17 | Dương Thành Đạt | DRIVER | VietRoute Miền Tây / VRDRV17 |
| driver18 | Trịnh Minh Quân | DRIVER | VietRoute Miền Tây / VRDRV18 |
| driver19 | Đinh Văn Lâm | DRIVER | VietRoute Miền Tây / VRDRV19 |
| driver20 | Phan Quốc Hưng | DRIVER | VietRoute Miền Tây / VRDRV20 |
| driver21 | Lý Văn Bình | DRIVER | VietRoute Đông Bắc / VRDRV21 |
| driver22 | Cao Minh Triết | DRIVER | VietRoute Đông Bắc / VRDRV22 |
| driver23 | Hồ Thanh Tùng | DRIVER | VietRoute Đông Bắc / VRDRV23 |
| driver24 | Mai Đức Hòa | DRIVER | VietRoute Đông Bắc / VRDRV24 |
| operator01 | Nguyễn Phương Linh | OPERATOR | VietRoute Miền Nam |
| operator02 | Trần Quốc Việt | OPERATOR | VietRoute Bắc Nam |
| operator03 | Lê Ngọc Hân | OPERATOR | VietRoute Miền Trung |
| operator04 | Phạm Minh Thư | OPERATOR | VietRoute Tây Nguyên |
| operator05 | Võ Thanh Bình | OPERATOR | VietRoute Miền Tây |
| operator06 | Đỗ Bảo Ngân | OPERATOR | VietRoute Đông Bắc |

### Đi theo một luồng để hiểu hệ thống

1. Đăng nhập admin: xem sáu nhà xe, tài khoản/role/function/phạm vi và nhật ký; không lấy quyền admin để giả quyền tài xế/khách.
2. Đăng nhập operator01: nhà xe Miền Nam; mở chuyến, lọc hôm nay/tương lai, xem đơn PENDING, xác nhận và thu tiền qua modal. operator02…06 làm cùng việc trong nhà xe riêng, không thấy chuyến nhà xe khác.
3. Đăng nhập driver01: mở Chuyến được giao, chọn VRINIT1R01D07, xem danh sách đón/trả. Xác nhận START → ARRIVE → DEPART ở điểm giữa → ARRIVE/COMPLETE ở điểm cuối. Thời gian server ghi, không có GPS hoặc app mobile; driver02 thuộc nhà xe Bắc Nam để đối chiếu phạm vi.
4. Đăng nhập custom01: xem một đơn WEB đang chờ và một đơn đã hoàn thành trong Đơn của tôi; thử xem chi tiết, hủy yêu cầu hợp lệ rồi tìm chuyến tạo đơn mới. Mỗi khách chỉ được một WEB PENDING/CONFIRMED tại một thời điểm, cần xử lý đơn active trước khi tạo tiếp.
5. Quay lại điều hành: đối chiếu trạng thái, chỗ trên các chặng, tiền phải thu/hoàn và báo cáo theo nhà xe; admin xem toàn phạm vi được cấp. Lỗi/409 giữ bản nháp, không tự gửi lại thao tác.

### Nguồn và phạm vi nút khởi tạo trên web

auth/default-access.json giữ account/role/function/grant và mapping ban đầu; bootstrap/foundation.json giữ bộ nền tối thiểu; bootstrap/operations.json bổ sung nhà xe/địa điểm/tuyến và profile vận hành. BusinessDataRepo giữ toàn bộ SQL theo tập, UserRepo giữ batch tài khoản/scope/link. Không thêm bảng hoặc sửa tám migration cho dữ liệu này.

Nút /operations/demo và POST /api/v1/admin/demo chỉ bổ sung bộ nền 21 bản ghi như contract API, không tạo account/membership hoặc các chuyến/đơn số lượng lớn từ HTTP. Bộ đầy đủ chạy tại startup với bốn cờ trên, vì quyền/credential là cấu hình người vận hành. Không tự bật bộ này trên môi trường khác chỉ để xem giao diện. Trạng thái đã áp dụng ở môi trường nào xem [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

DB hieuboy hiện chưa bật TLS; chủ dự án đã chấp thuận riêng kết nối không mã hóa cho local/Render tới máy chủ này. URL mẫu dùng sslmode=disable, không áp dụng thành mặc định chung cho DB khác. Khi DBA bật TLS phải cấu hình CA/hostname và đổi URL tương ứng; không dùng cờ bỏ xác minh certificate để giả là TLS đã chuẩn. Password chỉ điền trong .env local/Render Environment, không properties/Git/docs.

Run config VietRoute Local dùng VM option -Duser.timezone=Asia/Ho_Chi_Minh giống Docker. Máy chủ DB mới không nhận alias Asia/Saigon của một số JVM Windows; chạy main ngoài Run config cũng phải đặt timezone chuẩn này trước khi pgJDBC mở connection. Không sửa timezone/schema trên máy chủ để che lỗi cấu hình client.
