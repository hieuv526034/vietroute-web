# Cloudinary và chạy Spring MVC bằng IntelliJ

Người mới theo [lộ trình tài liệu](README.md). Thiết lập format/Code Style và nút Run Format tại [Format IntelliJ](FORMAT_INTELLIJ.md); tài liệu này tập trung Run/Debug ứng dụng và tích hợp ảnh.

Phụ trách nền tảng: hieuv526034. Stack giữ Spring MVC 6, JDBC, Thymeleaf và WAR/Tomcat 10.1. Không cần Spring Boot hoặc thêm container cho dịch vụ ảnh.

## Nút Run/Debug trong IntelliJ

1. Mở thư mục dự án, import/reload Maven từ `pom.xml`, chọn JDK 21 (tối thiểu 17).
2. Trong Maven → Profiles, bật `local`, tắt `production`, rồi Reload All Maven Projects. Profile local thêm `src/dev/java` làm Sources Root và Tomcat nhúng ở scope provided.
3. Chọn **VietRoute Local** trên thanh Run, bấm tam giác xanh hoặc Debug. Cấu hình chia sẻ là `.run/VietRoute Local.run.xml`; working directory phải là thư mục gốc. Nếu tên module sau import khác `vietroute-web`, chọn đúng **Use classpath of module**.
4. Mở `http://localhost:8080/login?lang=vi`. Đặt breakpoint trong controller/service như Java thông thường; nút Stop đóng Spring context, pool và worker.

Nếu cấu hình chưa hiện, mở `src/dev/java/vn/vietroute/dev/VietRouteApplication.java`, bấm Run ở `main()`. Bật **Add dependencies with provided scope to classpath** trong cấu hình Application để có Tomcat/Servlet API. Cấu hình chia sẻ đã bật tùy chọn này. Không thấy nút Run thì kiểm tra Maven profile local, Sources Root và Project SDK.

Trong **Run → Edit Configurations → VietRoute Local → Environment variables**, khai báo `VIETROUTE_DB_URL`, `VIETROUTE_DB_USER`, `VIETROUTE_DB_PASSWORD`, `VIETROUTE_DB_SCHEMA` và `VIETROUTE_JWT_SECRET` cho DB development. Không lưu secrets vào file `.run` được commit; dùng cấu hình cá nhân trong workspace IDE. Java không tự đọc `.env`; nhập biến qua giao diện hoặc dùng Environment files nếu phiên bản IDE hỗ trợ.

Cấu hình mặc định tắt migration/worker/mail/ảnh: chạy trang chủ, đăng nhập và Swagger mà chưa chạm DB. Muốn đăng nhập cần DB đã migrate. Chỉ bật `VIETROUTE_DB_INIT_ENABLED=true` khi chủ động khởi tạo môi trường và đã đặt `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD`. Không chạy migration/seed lên Neon production chỉ để thử launcher. `VIETROUTE_HTTP_PORT` đổi port local, mặc định 8080; launcher chỉ bind 127.0.0.1.

Launcher dùng đúng `WebApplicationInitializer`/`SecurityInitializer` của WAR. Java cần Build/Restart sau sửa; không cam kết tự hot reload. Profile production không compile `src/dev`; WAR loại package launcher và không chứa tomcat-embed. Docker vẫn chỉ copy `src/main`.

Nếu IDE có tích hợp **Tomcat Server**, cũng có thể chọn Run → Edit Configurations → Tomcat Server → Local, trỏ Tomcat 10.1, Deployment chọn artifact WAR exploded, Application context `/`; cấu hình JDK và Environment variables tương tự. Không chạy đồng thời hai server cùng port. Xem [hướng dẫn JetBrains](https://www.jetbrains.com/help/idea/run-debug-configuration-tomcat-server.html).

## Bật Cloudinary

Tạo tài khoản [Cloudinary](https://cloudinary.com/pricing), chọn Image & Video API, lấy Cloud name/API key/API secret trong Console. Free có quota, theo dõi usage trong Console. SDK Java cloudinary-http5 khóa phiên bản trong Maven; API secret chỉ ở backend.

Đặt trong Environment của IDE/Tomcat hoặc Render:

```text
VIETROUTE_MEDIA_ENABLED=true
VIETROUTE_CLOUDINARY_CLOUD_NAME=<cloud-name>
VIETROUTE_CLOUDINARY_API_KEY=<api-key>
VIETROUTE_CLOUDINARY_API_SECRET=<api-secret>
VIETROUTE_CLOUDINARY_FOLDER=vietroute
VIETROUTE_WORKER_ENABLED=true
```

Restart rồi đăng nhập tài khoản có quyền ảnh, mở `/media` hoặc thử ở `/docs`. Admin được seed quyền mới khi initializer bật; role thường cần được gán tường minh MEDIA_READ/MEDIA_CREATE/MEDIA_DELETE qua API phân quyền. Render Blueprint mặc định tắt ảnh/worker để chưa có credentials vẫn khởi động được; thêm secret trong Render Environment. Bật ảnh mà thiếu credentials hoặc worker tắt sẽ chặn startup. Test/CI không gọi Cloudinary thật; nghiệm thu tài khoản thật cần credentials và một ảnh công khai được chủ tài khoản cho phép.

## API và luồng dữ liệu

| API | Quyền | Hành vi |
|---|---|---|
| GET `/api/v1/media?page=0&size=12` | MEDIA_READ | Phân trang ảnh của mình; ADMIN xem toàn bộ; mới nhất trước |
| GET `/api/v1/media/{id}` | MEDIA_READ + owner/ADMIN | Metadata/audit; ảnh ngoài phạm vi trả 404 |
| POST `/api/v1/media` | MEDIA_CREATE | multipart/form-data, field `file`; 201/OK004 |
| DELETE `/api/v1/media/{id}` | MEDIA_DELETE + owner/ADMIN | JSON `{"version":1}`; 202/OK005, worker thực hiện xóa |

Cookie mode gửi CSRF bằng header trước mutation, không dùng multipart field. UI/Swagger dùng CSRF chung. File tối đa 5 MiB, multipart tối đa 5 MiB + 64 KiB; Tomcat chốt cả chunked. Chỉ JPEG/PNG thật, tối đa 4096 pixel mỗi chiều và 6 triệu pixel. Kiểm header trước decode rồi mã hóa lại pixel để loại EXIF/metadata/nội dung đính kèm. Không nhận SVG, GIF động, video, PDF hoặc remote URL; không tin extension/MIME client.

Một upload/instance xử lý đồng thời để giới hạn RAM; quota 10 upload/phút/user bên cạnh quota IP chung. Dùng Redis chung khi nhiều instance. Quá lớn COM014/413; file sai MEDIA002/400; tắt dịch vụ MEDIA001/503; lỗi provider MEDIA003/503. Response có locale/traceId, không lộ exception/credentials.

Upload: transaction tạo PENDING và job dọn sau một giờ → commit → signed upload với UUID/publicId server cấp, overwrite=false ngoài transaction → transaction lưu metadata/READY. Lỗi hoặc process chết giữa chừng vẫn có job dọn PENDING, kể cả provider đã nhận file. Lịch lấy thời gian PostgreSQL để tránh lệch đồng hồ.

Xóa: kiểm owner/quyền/version → transaction đổi DELETING và enqueue → worker nhận trạng thái nguyên tử → destroy/invalidate ngoài transaction → DELETED và bỏ URL. READY không bị job dọn upload cũ xóa. Xóa lặp gặp ảnh không còn ở provider vẫn thành công. Job DEAD cần quản trị sửa cấu hình rồi retry. Render Free ngủ thì việc dọn/xóa tiếp tục khi worker chạy lại; invalidation CDN không có hiệu lực tức thì.

DB và Cloudinary không có transaction phân tán. Client mất response nên xem thư viện trước khi upload lại; mỗi POST là upload mới, chưa có idempotency cho upload. Không chỉnh/xóa trực tiếp trong Console rồi kỳ vọng DB tự đồng bộ. DB lưu metadata/audit/trạng thái, không lưu bytes ảnh.

## Tích hợp nghiệp vụ sau này

Đây là thư viện **ảnh công khai** cho avatar/logo/ảnh xe/banner. Quyền owner bảo vệ API quản lý, không làm URL CDN thành riêng tư. CCCD/bằng lái/hồ sơ cần storage private và quyền tải riêng, không dùng module này.

Module nhà xe/xe phải kiểm owner/tenant và READY khi gắn mediaId, bổ sung FK và cấm xóa ảnh đang được tham chiếu hoặc thay trước khi xóa. Chưa có bảng nghiệp vụ thì không dựng companyId giả. Ảnh thay thế upload thành bản mới trước rồi nghiệp vụ đổi quan hệ trong transaction; chỉ enqueue xóa ảnh cũ sau kiểm tham chiếu.

V004 dùng schema cấu hình; không sửa V001–V003. Học theo MediaController → MediaService → ImageValidator → CloudinaryMediaStorage → MediaCleanupHandler/MediaRepository.

Nguồn: [Cloudinary Java](https://cloudinary.com/documentation/java_integration), [Upload API](https://cloudinary.com/documentation/image_upload_api_reference), [Tomcat nhúng](https://tomcat.apache.org/tomcat-10.1-doc/api/org/apache/catalina/startup/Tomcat.html).
