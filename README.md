# VietRoute

Website quản lý nhà xe, tuyến/chuyến, đặt chỗ và theo dõi hành trình liên tỉnh. Một source Java server + giao diện Thymeleaf; Spring MVC 6, Spring Security/JWT, JDBC thuần, PostgreSQL/Neon, Maven WAR và Tomcat 10.1. Java 17+; build/runtime dùng JDK/JRE 21. Không dùng Spring Boot, JPA/Hibernate ORM, JdbcTemplate hoặc Spring Data.

## Phần đã có

- Common: locale vi/en, message key/mã lỗi, BusinessException và API envelope thống nhất, validation, tạo mã, audit base, phân trang và transaction JDBC.
- Auth: JWT HS256, BCrypt, cookie HttpOnly/CSRF hoặc Bearer, login/me/đổi mật khẩu/logout, thu hồi token, khóa đăng nhập sai và kiểm tra role/active từ DB.
- Migration có history/checksum/transaction lock, seed admin và danh mục lặp không trùng; không reset mật khẩu đã đổi.
- Danh mục mẫu: tra cứu vi/en có phân trang; quyền READ/CREATE/UPDATE/DELETE riêng cho quản trị, version/audit và xóa mềm; các module nghiệp vụ dùng mẫu này để triển khai tiếp.
- Ảnh công khai: thư viện Cloudinary, upload/xem/xóa có quyền và owner, chuẩn hóa JPEG/PNG, metadata/audit và worker dọn upload dang dở; bật sau khi cấu hình credentials.
- Giao diện responsive: trang chủ, đăng nhập, tài khoản/đổi mật khẩu, quản trị danh mục và Swagger UI; Bootstrap/Axios/WebJars đóng gói trong WAR.
- Docker có cache Maven/layer, GitHub CI kiểm tra container và auth trên PostgreSQL tạm, Render Blueprint chỉ tự deploy main sau CI thành công.

Đây là **nền tảng đồ án**, chưa hoàn thành nghiệp vụ nhà xe/xe, tuyến/chuyến, đặt chỗ/tồn chỗ, Geolocation/Maps hoặc báo cáo. Phạm vi, thứ tự và người phụ trách nằm trong tài liệu bên dưới.

## Tài liệu phải đọc

1. [Quy ước phát triển và cấu trúc module](docs/QUY_UOC_PHAT_TRIEN.md).
2. [Hợp đồng API/base/auth](docs/API_BASE.md).
3. [Phân công theo độ khó và thứ tự triển khai](docs/PHAN_CONG.md).
4. [Database và migration](database/README.md).
5. [Docker/Render và biến môi trường](docs/DEPLOY_RENDER.md).
6. [Quy trình Git và ghi nhận đóng góp](CONTRIBUTING.md).
7. [Đặc tả nghiệp vụ và bộ ca nghiệm thu](docs/DAC_TA_HE_THONG_XE_KHACH.md).
8. [Luồng xử lý base để thành viên học và mở rộng](docs/LUONG_XU_LY_BASE.md).
9. [Rà soát nền tảng và các điểm cần chốt trước nghiệp vụ](docs/RA_SOAT_BASE.md).
10. [Chống spam, quyền chức năng, worker và Gmail/Workspace](docs/NEN_TANG_MO_RONG.md).
11. [Cấu hình schema một nơi và ma trận quyền theo hành động](docs/SCHEMA_VA_PHAN_QUYEN.md).
12. [Cloudinary và nút Run/Debug trong IntelliJ](docs/CLOUDINARY_VA_INTELLIJ.md).
13. [ApplicationConstants và quy hoạch package web](docs/CONSTANTS_VA_CAU_TRUC_WEB.md).

## Chạy dự án

**IntelliJ:** bật Maven profile `local`, Reload Maven, chọn **VietRoute Local** rồi Run/Debug. Có lớp `VietRouteApplication.main()` dùng Tomcat nhúng cho phát triển; production vẫn WAR. Hướng dẫn môi trường và phương án Tomcat Server nằm trong [Cloudinary và IntelliJ](docs/CLOUDINARY_VA_INTELLIJ.md).

Cài JDK 17/21, đặt JAVA_HOME đúng thư mục JDK. Import pom.xml vào IDE. Java không tự đọc `.env`: đặt biến môi trường trong Tomcat/IDE hoặc dùng Docker `--env-file`.

Khi phát triển, cài Node.js để chạy `npm ci`, `npm run format` và `npm run format:check`. Bộ format Java/HTML/CSS/JS/XML chỉ dùng ở máy phát triển; production Docker không cài Node. Tên lớp dùng `DTO` viết hoa, thời điểm Java dùng `Instant`. Mỗi người làm nhánh theo bảng [phân công](docs/PHAN_CONG.md), mở PR rồi mới merge vào `main`.

```powershell
# Kiểm tra phát triển, bao gồm test local không dùng Neon
.\mvnw.cmd verify

# Kiểm tra CSRF của Swagger bằng Node, chỉ dành cho phát triển
node --test src/test/resources/dev/swagger.test.cjs

# Build production: bỏ qua cả compile và chạy test
.\mvnw.cmd -Pproduction clean package

# Chạy Docker với cấu hình local đã chuẩn bị
# Nếu DB_INIT_ENABLED=true, ứng dụng sẽ chạy migration/seed trên DB đã cấu hình.
docker build -t vietroute-web .
docker run --rm --env-file .env -p 10000:10000 vietroute-web
```

Docker: mở http://localhost:10000; `/login`, `/account`, `/admin`, `/docs`, `/v3/api-docs`. WAR thủ công: deploy `target/vietroute.war` lên Tomcat 10.1 và thêm context `/vietroute` vào các đường dẫn. Linux/macOS dùng `sh ./mvnw` hoặc `./mvnw`.

Cần DB đã migrate để đăng nhập/tra danh mục. Trang chủ, trang đăng nhập, Swagger và `/health` có thể render khi initializer tắt; `/health` chỉ xác nhận web availability, không xác nhận DB sẵn sàng. Bootstrap username mặc định `admin`, mật khẩu lấy từ `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD` trong `.env` local/Environment; không có mật khẩu production trong source.

Schema ứng dụng cấu hình bằng `VIETROUTE_DB_SCHEMA` (mặc định `vietroute`) trong database `neondb`. Query và metadata tự dùng schema cấu hình; DB mới tạo bảng/index/seed tại đó, legacy public được nâng cấp giữ dữ liệu/hash. Đổi tên schema đang có dữ liệu cần bảo trì/rename DB trước khi đổi key; startup chặn history ở nơi khác để tránh seed nhầm. Xem hướng dẫn schema ở trên.

## Cấu trúc

```text
src/main/java/vn/vietroute/
  common/     base, constant, exception, validation, util, jdbc, config, web, monitoring, job, mail, media, traffic
  auth/       config, security, controller, dto/request, dto/response, entity, repository, service
  business/
    catalog/  controller, dto/request, dto/response, entity, repository, service
src/main/resources/
  i18n/              messages_vi.properties, messages_en.properties
  db/migration/      Migration SQL có phiên bản và checksum
src/main/webapp/
  WEB-INF/views/     fragments, auth, docs và các trang Thymeleaf
  assets/            CSS/JavaScript dùng chung
src/test/            Test local, bị loại khỏi build context/image production
```

Controller chỉ nhận request, service giữ nghiệp vụ/transaction/quyền, repository dùng PreparedStatement. Entity không trả thẳng ra client. Comment các luồng bằng tiếng Việt; thêm message key phải có đủ vi/en. Các module tiếp theo đặt dưới business, không đặt lẫn vào auth/common.
