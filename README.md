# VietRoute

Website quản lý nhà xe, tuyến/chuyến, đặt chỗ và theo dõi hành trình liên tỉnh. Một source Java server + giao diện Thymeleaf; Spring MVC 6, Spring Security/JWT, JDBC thuần, PostgreSQL/Neon, Maven WAR và Tomcat 10.1. Java 17+; build/runtime dùng JDK/JRE 21. Không dùng Spring Boot, JPA/Hibernate ORM, JdbcTemplate hoặc Spring Data.

## Phần đã có

- Common: locale vi/en, message key/mã lỗi, BusinessException và API envelope thống nhất, validation, tạo mã, audit base, phân trang và transaction JDBC.
- Auth: JWT HS256, BCrypt, cookie HttpOnly/CSRF hoặc Bearer, login/me/đổi mật khẩu/logout, thu hồi token, khóa đăng nhập sai và kiểm tra role/active từ DB.
- Migration có history/checksum/transaction lock, seed admin và danh mục lặp không trùng; không reset mật khẩu đã đổi.
- Danh mục mẫu: tra cứu vi/en có phân trang; ADMIN tạo mục có audit và kiểm tra mã; các module nghiệp vụ dùng mẫu này để triển khai tiếp.
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

## Chạy dự án

Cài JDK 17/21, đặt JAVA_HOME đúng thư mục JDK. Import pom.xml vào IDE. Java không tự đọc `.env`: đặt biến môi trường trong Tomcat/IDE hoặc dùng Docker `--env-file`.

```powershell
# Kiểm tra phát triển, bao gồm test local không dùng Neon
.\mvnw.cmd verify

# Build production: bỏ qua cả compile và chạy test
.\mvnw.cmd -Pproduction clean package

# Chạy Docker với cấu hình local đã chuẩn bị
# Nếu DB_INIT_ENABLED=true, ứng dụng sẽ chạy migration/seed trên DB đã cấu hình.
docker build -t vietroute-web .
docker run --rm --env-file .env -p 10000:10000 vietroute-web
```

Docker: mở http://localhost:10000; `/login`, `/account`, `/admin`, `/docs`, `/v3/api-docs`. WAR thủ công: deploy `target/vietroute.war` lên Tomcat 10.1 và thêm context `/vietroute` vào các đường dẫn. Linux/macOS dùng `sh ./mvnw` hoặc `./mvnw`.

Cần DB đã migrate để đăng nhập/tra danh mục. Trang chủ, trang đăng nhập, Swagger và `/health` có thể render khi initializer tắt; `/health` chỉ xác nhận web availability, không xác nhận DB sẵn sàng. Bootstrap username mặc định `admin`, mật khẩu lấy từ `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD` trong `.env` local/Environment; không có mật khẩu production trong source.

## Cấu trúc

```text
src/main/java/vn/vietroute/
  common/     base, constant, exception, validation, util, jdbc, config, web
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
