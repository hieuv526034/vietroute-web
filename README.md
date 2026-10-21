# VietRoute

**Website quản lý nhà xe, tìm kiếm và đặt chỗ xe khách liên tỉnh, theo dõi hành trình tích hợp Google Maps.**

- Repository đề xuất: `vietroute-web`.
- Maven: `vn.vietroute:vietroute-web:0.1.0-SNAPSHOT`.
- Java package: `vn.vietroute`.
- Sản phẩm: website; giao diện và xử lý server trong cùng source.
- Stack: Spring MVC, Thymeleaf, JDBC thuần, PostgreSQL (Neon), Maven WAR và Tomcat 10.1.
- Java 17 trở lên; khung source được kiểm tra bằng JDK 21.
- Không dùng Spring Boot, React, JPA/Hibernate, JdbcTemplate hoặc Spring Data JDBC.

## Trạng thái source

Đây là **khung khởi tạo**, chưa phải ứng dụng nghiệp vụ hoàn chỉnh. Đã có cấu hình Spring MVC/Thymeleaf, trang chủ, static CSS, endpoint `/health`, factory mở Connection JDBC, cấu trúc module và smoke test cấu hình web. Các module tài khoản, tuyến/chuyến, đặt theo chặng và bản đồ sẽ do các thành viên triển khai theo phân công.

`/health` kiểm tra ứng dụng web đang chạy, không xác nhận database hoặc Google Maps đã sẵn sàng. Trang chủ khởi tạo không cần PostgreSQL để render.

## Docker, GitHub CI/CD và Render

[Hướng dẫn triển khai đầy đủ](docs/DEPLOY_RENDER.md). Docker đóng gói WAR thành `ROOT.war`, nên URL khi chạy container là `/` và `/health`, không có tiền tố `/vietroute`.

```powershell
docker build -t vietroute-web .
docker run --rm --env-file .env -p 10000:10000 vietroute-web
```

GitHub Actions build và kiểm tra container với cache Docker/Maven. Render đọc `render.yaml`, dùng gói Free và chỉ tự deploy `main` sau khi CI thành công. Thay đổi chỉ trong test/tài liệu không kích hoạt build. Production dùng `-Pproduction`: không biên dịch/chạy test; `.dockerignore` chỉ cho phép source production vào image. Lệnh `verify` thông thường bên dưới vẫn chạy test khi phát triển local.

## Chạy dự án

1. Cài JDK 17 hoặc 21; đặt `JAVA_HOME` trỏ tới thư mục JDK thật. Kiểm tra `java -version` và `javac -version`.
2. Mở `pom.xml` bằng IDE và import như một Maven project.
3. Build bằng Maven Wrapper:

```powershell
.\mvnw.cmd clean verify
```

Trên Linux/macOS dùng `./mvnw clean verify`.

4. Deploy `target/vietroute.war` lên **Tomcat 10.1**, hoặc thêm artifact WAR vào Tomcat run configuration trong IDE.
5. Mở `http://localhost:8080/vietroute/`; health ở `http://localhost:8080/vietroute/health`. Nếu IDE đặt context path khác thì dùng đúng context đó.
6. Để phát triển phần dữ liệu, xem `database/README.md` và cấu hình các biến môi trường `VIETROUTE_DB_*`.

Không dùng Tomcat 9 cho cấu hình Jakarta Servlet hiện tại. Maven Wrapper cần mạng trong lần đầu tải Maven/dependency. Không có Spring Boot main class; ứng dụng được container Servlet khởi tạo qua `WebApplicationInitializer`.

Version hiện khóa: Spring 6.2.19 + thymeleaf-spring6 3.1.5.RELEASE + Servlet 6.0, dành cho môi trường bài tập Tomcat 10.1. Cả nhóm cần thống nhất trước khi đổi thế hệ Spring/Servlet/Tomcat. Việc nâng cấp production nằm ngoài phạm vi bộ khung này.

## Cấu trúc

```text
src/main/java/vn/vietroute/
  config/           Khởi tạo Spring MVC và Thymeleaf
  common/jdbc/      Factory Connection JDBC dùng chung
  web/              Controller trả view, mở rộng controller theo module ở đây
  identity/         Service/DAO/model tài khoản, nhà xe, xe, tài xế
  network/          Service/DAO/model địa điểm, tuyến, điểm dừng, chuyến
  booking/          Service/DAO/model tìm chuyến, đặt/hủy và chỗ theo chặng
  tracking/         Service/DAO/model tiến độ, vị trí, bản đồ, báo cáo
src/main/webapp/
  WEB-INF/views/    Template Thymeleaf; tạo thư mục con theo module
  assets/           CSS/JavaScript dùng chung hoặc theo module
database/           SQL khởi tạo và thay đổi schema
docs/               Đặc tả, phân công và hướng dẫn làm nhóm
```

Controller đặt dưới `vn.vietroute.web.<module>` để được WebConfig quét. Service/DAO/model đặt dưới các module còn lại để RootConfig quét; không gắn Controller vào root context. Mỗi module có thể tách tiếp `service`, `dao`, `model`, `dto` khi bắt đầu làm.

## Làm nhóm và ghi nhận đóng góp

- Đọc [CONTRIBUTING.md](CONTRIBUTING.md) trước commit đầu tiên của mỗi người.
- Phân công: [docs/PHAN_CONG.md](docs/PHAN_CONG.md).
- Tạo repo, thêm người và push: [docs/KHOI_TAO_GITHUB.md](docs/KHOI_TAO_GITHUB.md).
- Đặc tả và các ca nghiệm thu: [docs/DAC_TA_HE_THONG_XE_KHACH.md](docs/DAC_TA_HE_THONG_XE_KHACH.md).

Mỗi người tự clone, cấu hình email của mình, làm nhánh chức năng, commit và mở pull request. Mời vào repo không tự tạo đóng góp. Khung source chung không được chia thành commit giả mang tên các thành viên chưa thực hiện công việc.

Về tracking: đã xác định website có thể nhận vị trí qua Geolocation khi trang chia sẻ đang hoạt động. Không cam kết theo dõi nền khi khóa màn hình. Trước khi code phần này, thống nhất lựa chọn trong mục cập nhật của đặc tả; bộ khung chưa triển khai lấy vị trí hoặc Google Maps.
