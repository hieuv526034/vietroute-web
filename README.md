# VietRoute

Website quản lý nhà xe, tuyến/chuyến, đặt chỗ và theo dõi hành trình liên tỉnh. Java 17+, Spring MVC 6/Security, Thymeleaf, PostgreSQL/Neon, Spring Data JPA/Hibernate và JDBC; đóng WAR cho Tomcat 10.1, không Spring Boot.

## Source hiện hành

- Auth: 40 file Java, tám bảng; User → Role → Function → menu/endpoint trong DB, JWT/BCrypt/CSRF, scope/owner/version và lịch sử mutation nguyên tử.
- Business: danh mục, địa điểm, nhà xe/xe/tài xế, tuyến/chuyến, đặt chỗ/tồn chỗ theo chặng, báo cáo và tiến độ chuyến theo mốc tài xế.
- Common: 61 file Java, giữ nền MVC/JPA/JDBC, envelope/lỗi/locale/Swagger và nhật ký API; gồm cấu hình JDBC Oracle 26ai đang chuẩn bị, chưa bật runtime Oracle. Đã bỏ job/mail/media/monitoring/traffic cùng provider và giao diện phụ.
- Nhật ký /operations/api-journal: user/request/thời gian, snapshot payload/response đã lọc, copy cURL/Postman. Cần grant page/API tương ứng; không ghi token/mật khẩu/PII hoặc tự replay mutation.
- Database: tám SQL theo auth/catalog/carrier/trip/booking/common/foundation, tạo 23 bảng ứng dụng + history. Database có history khác cần bảo trì rebaseline có đối chiếu cấu trúc trước.
- Bộ khởi tạo đầy đủ: 43 tài khoản, 6 nhà xe, 24 xe/tài xế, 24 tuyến, 494 chuyến và 2.952 đơn giả lập. [Tài khoản, key mật khẩu và luồng dùng thử](docs/01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo); init opt-in, giữ dữ liệu/password đã có.

Trạng thái merge/deploy và bằng chứng nghiệm thu nằm trong [tiến độ](docs/05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md); source cục bộ chưa đồng nghĩa main/Render đã được cập nhật. Dữ liệu nền do dự án tạo theo yêu cầu, không xác nhận là hoạt động thật của một đơn vị vận tải.

## Tài liệu

Bắt đầu tại [mục lục 5 bước](docs/README.md), [tổng quan](docs/01-bat-dau/TONG_QUAN_HE_THONG.md), [chạy IntelliJ](docs/01-bat-dau/CHAY_LOCAL.md), [cấu trúc common](docs/02-phat-trien/CONSTANTS_VA_CAU_TRUC_WEB.md) và [schema/quyền](docs/02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md). Dùng AI đọc [AGENTS.md](AGENTS.md) và [bàn giao](docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md); nhánh thành viên → dev, chỉ phát hành main khi chủ dự án có lệnh mới.

## Chạy và kiểm tra

IntelliJ: Maven profile local → Reload Maven → VietRoute Local → Run/Debug, mặc định http://localhost:8080/login?lang=vi. .env gốc được theo dõi Git theo ngoại lệ chủ dự án, không sao chép secret vào docs/log/image. OS/IDE có ưu tiên cao hơn file; WAR/Render nhận biến môi trường, test/build không tự đọc .env.

~~~powershell
npm ci
npm run format
npm run format:check
npm run check:rules
npm run check:docs
.\mvnw.cmd verify
node --test src/test/resources/dev/*.test.cjs
~~~

PostgreSQL regression dùng database riêng theo [API base](docs/02-phat-trien/API_BASE.md); mặc định unit/H2/MVC không cần Neon. Production đóng gói bằng Maven profile production, không compile/chạy test hoặc đưa launcher nhúng vào WAR. Không chạy init/test/cleanup production để kiểm build.

Chỉ chủ động bật DB_INIT/ACCESS_SYNC/default seed khi cần khởi tạo/nâng cấp môi trường đã sao lưu. Startup giữ hash tài khoản hiện có; .env không đặt lại mật khẩu đã đổi trong DB. /health chỉ kiểm web availability; xem [nhật ký và vận hành](docs/04-van-hanh/NHAT_KY_API_VA_DEBUG.md) để chẩn đoán DB/request.

## Cấu trúc

~~~text
src/main/java/vn/vietroute/
  auth/                  config, security, controller/api, dto/request, dto/search,
                         entity, repo, service, initializer
  business/<module>/     controller/api, dto/request, dto/response, dto/search,
                         entity, repo, service, initializer
  common/                base, config, initializer, jdbc, api_journal, web,
                         constant, exception, validation
src/main/resources/
  db/migration/postgresql/          auth, catalog, carrier, trip, booking, common, foundation
  i18n/                            Thông báo vi/en
src/main/webapp/                    Thymeleaf, CSS/JavaScript
src/test/                          Regression/fixture, không đưa vào image production
~~~
