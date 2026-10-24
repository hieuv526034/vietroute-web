# Chạy Spring MVC bằng IntelliJ và cấu hình Cloudinary

Người mới đọc phần Run/Debug dưới đây, thiết lập [Format IntelliJ](FORMAT_INTELLIJ.md) rồi quay lại [lộ trình](../README.md). Các phần từ Bật Cloudinary trở đi chỉ đọc khi tích hợp ảnh.

Phụ trách nền tảng: hieuv526034. Stack giữ Spring MVC 6, JDBC, Thymeleaf và WAR/Tomcat 10.1. Không cần Spring Boot hoặc thêm container cho dịch vụ ảnh.

## Nút Run/Debug trong IntelliJ

1. Mở thư mục dự án, import/reload Maven từ `pom.xml`, chọn JDK 21 (tối thiểu 17).
2. Trong Maven → Profiles, bật `local`, tắt `production`, rồi Reload All Maven Projects. Profile local thêm `src/dev/java` làm Sources Root và Tomcat nhúng ở scope provided.
3. Chọn **VietRoute Local** trên thanh Run, bấm tam giác xanh hoặc Debug. Cấu hình chia sẻ là `.run/VietRoute Local.run.xml`; working directory phải là thư mục gốc. Nếu tên module sau import khác `vietroute-web`, chọn đúng **Use classpath of module**.
4. Mở `http://localhost:8080/login?lang=vi`. Đặt breakpoint trong controller/service như Java thông thường; nút Stop đóng Spring context, pool và worker.

Nếu cấu hình chưa hiện, mở `src/dev/java/vn/vietroute/dev/VietRouteApplication.java`, bấm Run ở `main()`. Bật **Add dependencies with provided scope to classpath** trong cấu hình Application để có Tomcat/Servlet API. Cấu hình chia sẻ đã bật tùy chọn này. Không thấy nút Run thì kiểm tra Maven profile local, Sources Root và Project SDK.

Launcher `VietRouteApplication` của profile `local` chủ động đọc `.env` tại working directory dự án nếu file tồn tại. Đây là tiện ích riêng của launcher; WAR/Tomcat ngoài IDE và Render vẫn chỉ nhận biến môi trường. Tạo file riêng theo [.env.example](../../.env.example), hoặc dùng file riêng đã được chủ dự án cấp; không commit secret. Có thể đặt `VIETROUTE_LOCAL_ENV_FILE` trong Environment variables để chọn file khác. Biến OS/IDE ưu tiên hơn file. Giá trị `VIETROUTE_ENV` và cookie trong file được chuyển về development/HTTP khi chạy launcher; lựa chọn OS/IDE rõ ràng vẫn ưu tiên.

Chủ dự án hiện chỉ có **một Neon `neondb`**: giữ URL/user/password/schema trong `.env` riêng để local dùng đúng DB đó. Chạy giao diện local sẽ tác động dữ liệu thật khi thêm/sửa/xóa. Không tạo DB thứ hai tự động. Maven/test/CI không đọc file này; Surefire ghi đè env của tiến trình test sang loopback không có DB và tắt initializer/worker/provider, tránh vô tình kế thừa env production từ shell. CI dựng PostgreSQL tạm để kiểm migration/seed/SQL. Khi chạy JUnit trực tiếp ngoài Maven, không gán env production vào cấu hình test cá nhân.

Run lần đầu của bản source có migration mới cần `VIETROUTE_DB_INIT_ENABLED=true` trong file riêng để startup áp dụng V001–V015 và seed thiếu. Initializer có khóa/checksum và không reset mật khẩu admin đã tồn tại. `VIETROUTE_ACCESS_SYNC_ENABLED` không khai báo thì theo DB_INIT; nếu giữ key false từ `.env.example`, đổi thành true khi cần startup đồng bộ danh bạ UI/API. Schema phải có đủ V015 trước khi bật riêng sync của source hiện tại. Khi nâng từ code trước VIEW hoặc membership, dừng/drain toàn bộ node cũ trước rồi chạy initializer/source mới; không gán membership khi còn node cũ theo [hợp đồng SCOPE01](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#nền-phạm-vi-dữ-liệu--scope01). Không bật initializer chỉ để kiểm build. Cấu hình `.run` không ép INIT=false nữa, còn worker/mail mặc định tắt; bật provider và worker có chủ đích khi đã chuẩn bị credentials. Chưa có `.env` vẫn có thể khai báo các biến DB/JWT trực tiếp trong **Run → Edit Configurations → Environment variables**. Không lưu giá trị bí mật vào file `.run` chia sẻ.

Cấu hình mặc định tắt migration/worker/mail/ảnh: chạy trang chủ, đăng nhập và Swagger mà chưa chạm DB. Muốn đăng nhập cần DB đã migrate. Chỉ bật `VIETROUTE_DB_INIT_ENABLED=true` khi chủ động khởi tạo môi trường và đã đặt `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD`. Không chạy migration/seed lên Neon production chỉ để thử launcher. `VIETROUTE_HTTP_PORT` đổi port local, mặc định 8080; launcher chỉ bind 127.0.0.1.

ACCESS15: init/sync được bật sẽ sửa dữ liệu quyền thiếu của username bootstrap (mặc định admin), giữ mật khẩu đã đổi và các trạng thái bảo mật của account đang có. Nếu account mất hẳn, cần mật khẩu bootstrap hợp lệ để startup tạo lại; không nhập mật khẩu cũ vào source. Mất riêng role/quyền có thể đăng nhập rồi mở Thông tin cá nhân → Khôi phục quản trị gốc, xác nhận mật khẩu hiện tại và đăng nhập lại sau thành công. First-login vẫn phải đổi mật khẩu tạm. Xem [hướng dẫn vận hành](../04-van-hanh/QUAN_TRI_HE_THONG.md#khôi-phục-quản-trị-gốc) và [hợp đồng/giới hạn](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#cứu-hộ-tài-khoản-bootstrap--access15).

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


## Tích hợp thư viện ảnh dùng chung

`common.media` giữ `service`, `repository`, `entity`, `dto`, `storage`, `validation`. Controller và page nằm cùng `common.media.controller`, giữ nguyên URL HTTP; chỉ gọi `MediaService`, không chứa bộ upload thứ hai. Module sau tiêm service chung và lưu media ID phía server, kiểm quan hệ owner/tenant riêng của module; không nhận public ID hoặc URL từ client để quản trị Cloudinary.

Upload file mới dùng `MEDIA_CREATE`, xem dùng `MEDIA_READ`, sửa tên/alt text dùng `MEDIA_UPDATE`, yêu cầu xóa dùng `MEDIA_DELETE`. PUT `/api/v1/media/{id}` nhận title, altText, version; chỉ ảnh READY và owner/ADMIN, có kiểm version ngay SQL. Sửa metadata không cần gọi Cloudinary và vẫn dùng được khi upload tắt. Muốn đổi nội dung file: upload ảnh mới rồi thay liên kết theo transaction nghiệp vụ sau; chưa có luồng thay thế giấy tờ riêng tư.

## Chẩn đoán nguồn DB khi Run local

`database.properties` chỉ là mẫu localhost/schema, không chứa credential production. DatabaseSettings nhận ConfigurationEnvironment: **OS/IDE → .env được launcher chọn → mẫu classpath**. Không copy password vào database.properties; file đó nằm trong WAR/Git. Cấu hình VietRoute Local chia sẻ chỉ trỏ đường dẫn `$PROJECT_DIR$/.env`, không chứa giá trị bí mật. Launcher cũng tôn trọng VIETROUTE_LOCAL_ENV_FILE từ JVM property khi không có biến OS/IDE, và parser nhận UTF-8 BOM do công cụ Windows tạo.

Console startup có `event=database_configuration urlSource=... userSource=... passwordSource=...`. LOCAL_FILE là đúng file riêng, OS_IDE nghĩa là biến IDE/Windows đang ghi đè (kiểm và xóa giá trị cũ nếu sai), DEFAULT nghĩa là chưa nhận key và đang dùng mẫu. Chỉ in nguồn, không in URL/user/password. Dừng app cũ → Maven clean/Reload Maven → Rebuild Project → chọn VietRoute Local, working directory gốc → Run. Không chạy cùng lúc server cũ đang chiếm 8080 hoặc dùng lớp compile cũ. Nếu vẫn lỗi, gửi SQLState/traceId và ba source, không gửi credential.

Kiểm chẩn đoán chỉ đọc ngày 24/10 xác nhận `.env` trên máy chủ dự án kết nối được Neon, admin active và hash khớp bootstrap, history đang 10 migration; không chạy initializer/backfill hoặc reset mật khẩu để thử. Đây là bằng chứng tại thời điểm kiểm, không kết luận mọi lần Run/IDE dùng cùng nguồn. Source mới cần runtime chủ động áp dụng V012; không thử migration trên production trong build.
