# Triển khai VietRoute lên Render bằng Docker

## Luồng CI/CD

1. Pull request thay đổi source production: GitHub Actions build Docker và kiểm tra `/health`, trang chủ, CSS trong container giới hạn RAM 512 MB. Không cần database hoặc secrets trong GitHub Actions.
2. Push/merge vào `main`: cùng workflow chạy lại; Render đợi CI thành công (`autoDeployTrigger: checksPass`), sau đó build Docker và chuyển sang bản mới khi `/health` thành công.
3. CI dùng PostgreSQL container tạm để kiểm tra auth/bootstrap/migration/CSRF/quyền, không dùng Neon. Nếu CI hoặc health check thất bại, bản mới không được đưa vào phục vụ. Theo dõi cả GitHub Actions và Events/Logs trên Render để xác nhận deploy đã hoàn tất.

GitHub và Render build ở hai môi trường riêng, mỗi nơi có cache riêng. Bước tải Maven/dependency nằm trước bước copy `src/main` nên được tái sử dụng khi chỉ sửa code. GitHub lưu cả các layer trung gian bằng BuildKit cache `gha,mode=max`. Build đầu hoặc thay `pom.xml` sẽ lâu hơn; không bật Clear build cache trong các lần deploy thông thường. Không cần Docker Hub, GHCR, deploy hook hoặc Render API key cho luồng này.

## Tạo service một lần bằng Blueprint

1. Đẩy source và các file cấu hình lên `main` của repository `hieuv526034/vietroute-web`.
2. Đăng nhập Render → **New → Blueprint** → kết nối GitHub và chọn repository này, nhánh `main`, file `render.yaml`.
3. Kiểm tra service `vietroute-web`: runtime **Docker**, plan **Free**, region **Ohio** (gần Neon đang ở AWS us-east-2), health check `/health`.
4. Điền bốn biến bí mật Render yêu cầu từ `.env` trên máy bạn: `VIETROUTE_DB_URL`, `VIETROUTE_DB_USER`, `VIETROUTE_DB_PASSWORD`, `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD`. URL phải bắt đầu bằng `jdbc:postgresql://`; không điền URI có sẵn user/password của lệnh `psql` vào ô URL.
5. Blueprint tự sinh `VIETROUTE_JWT_SECRET` cố định, đặt `VIETROUTE_ENV=production`, `VIETROUTE_COOKIE_SECURE=true`, `VIETROUTE_DB_INIT_ENABLED=true`, username bootstrap `admin`. Startup sẽ chạy migration/seed trên DB đã cấu hình; chỉ tạo admin khi chưa có và không reset mật khẩu đã đổi.
6. Chọn **Deploy Blueprint**. Chờ trạng thái **Live**, mở `/health` → `vietroute-web: UP`, rồi `/login` để đăng nhập admin bằng mật khẩu bootstrap đã nhập. Trang `/account` cho đổi mật khẩu ban đầu; `/admin` có danh mục mẫu; `/docs` là Swagger UI.

`.env` đã bị loại khỏi Git và Docker context. Java không tự đọc file này; Render phải nhận biến môi trường trong Dashboard. `sync: false` chỉ yêu cầu nhập secret lúc tạo Blueprint, không cập nhật lại secret đã tồn tại ở những lần sync sau. Nếu đổi mật khẩu, cập nhật trực tiếp Environment trên Render và `.env` local.

Nếu đã tạo service từ bản source trước khi có auth, thêm `VIETROUTE_BOOTSTRAP_ADMIN_PASSWORD`, JWT secret cố định và các cờ môi trường ở bước 5 vào Environment trước lần deploy base auth. Blueprint cập nhật không tự hỏi lại giá trị `sync: false` mới thêm. Với service tạo thủ công, `render.yaml` không tự cập nhật cấu hình Dashboard. Không xóa/thay JWT secret đang dùng ở mỗi lần deploy vì thao tác đó làm token cũ mất hiệu lực.

`/health` chỉ xác nhận web chạy; không truy vấn Neon. Kiểm tra thêm login, `/api/v1/auth/me` và danh mục để xác nhận DB hoạt động. Khi initializer bật, lỗi migration/checksum/secret sẽ làm ứng dụng không khởi động thành công; xem Logs và không sửa migration đã áp dụng.

V003 bổ sung quyền chức năng và system_jobs, chạy dưới cùng khóa/checksum/transaction startup. Worker và mail mặc định tắt để chưa có secrets vẫn deploy được; bật sau khi cấu hình Gmail OAuth/worker theo [NEN_TANG_MO_RONG.md](../02-phat-trien/NEN_TANG_MO_RONG.md). Render Free chặn SMTP 25/465/587, dùng Gmail API HTTPS. Free có thể ngủ, job không mất vì ở PostgreSQL nhưng không có bảo đảm chạy đúng giờ. Nhiều instance cần Redis chung và cấu hình trusted proxy thực tế; quota memory chỉ bảo vệ một instance.

`VIETROUTE_ENV` chỉ nhận `development` hoặc `production`; gõ sai làm startup dừng. Production bắt buộc khai báo DB URL/user/password và JWT secret thật, không dùng placeholder trong `.env.example`. HikariCP mặc định tối đa 4 connection/instance; chờ mượn connection 20 giây, connect timeout 15 giây, socket timeout 30 giây. Có thể chỉnh bằng các biến trong `.env.example`, xem giới hạn ở `database/README.md`. Pool không giữ connection rảnh hoặc ping định kỳ; Neon có thể ngủ khi không dùng. Đây là timeout I/O/kết nối, không thay thế việc thiết kế query/index hoặc kiểm thử tải.

Schema lấy từ `VIETROUTE_DB_SCHEMA` trong Environment, Blueprint mặc định vietroute; database vẫn neondb. User DB cần quyền tạo schema/bảng/index. Startup tạo schema trên DB mới hoặc nâng cấp legacy public vào schema cấu hình, giữ ID/FK/sequence/index/password. History ở nơi khác/nhiều history làm startup dừng; không tự tạo dữ liệu song song. Với dữ liệu hiện có, dừng mọi instance và rename DB có backup trước khi đổi key. Xem [hướng dẫn schema](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md).

## Nếu tạo Web Service thủ công

- Kết nối repository bằng GitHub integration, nhánh `main`.
- Language/runtime: **Docker**; Dockerfile path `./Dockerfile`; build context `.`; plan **Free**; region **Ohio**.
- Health check path: `/health`; `PORT=10000`; thêm bốn biến bí mật và các cờ cấu hình ở trên. Tự tạo JWT secret ngẫu nhiên tối thiểu 32 byte UTF-8, lưu cố định trên Environment; không thay secret mỗi lần deploy.
- Auto-Deploy: **After CI Checks Pass**.
- Không điền Maven Build Command hoặc Java Start Command cho Docker. Dockerfile và entrypoint đảm nhiệm cả build lẫn chạy Tomcat.
- Nếu không dùng Blueprint, tự đặt Included Paths giống `buildFilter.paths` trong `render.yaml` để tránh deploy khi chỉ sửa tài liệu/test. Tạo bằng Blueprint sẽ tự áp dụng cấu hình này.

## Chạy và kiểm tra local

```powershell
# Production WAR: bỏ qua cả biên dịch và chạy test
.\mvnw.cmd -Pproduction package

# Kiểm tra phát triển, chạy test khi cần
.\mvnw.cmd verify

# Container giống production, đọc database từ .env local
docker build -t vietroute-web .
docker run --rm --env-file .env -p 10000:10000 vietroute-web
```

Mở `http://localhost:10000/` và `http://localhost:10000/health`. Docker dùng `ROOT.war`; deploy WAR thủ công lên Tomcat vẫn có thể dùng context `/vietroute`.

Đặt toàn bộ Java test vào `src/test/java`, fixture vào `src/test/resources`. Chúng không vào Docker context và không vào WAR. Thay đổi chỉ trong `src/test`, `docs`, README hoặc `.env.example` không kích hoạt CI/deploy. Dùng **Actions → Production Docker → Run workflow** nếu cần chạy CI thủ công; dùng **Manual Deploy** trên Render nếu cần deploy lại mà không thay source.

Tomcat chạy bằng user thường, dùng `PORT` do Render cấp. JVM có heap tối đa 256 MB để chừa bộ nhớ native cho gói 512 MB. Có thể điều chỉnh `CATALINA_OPTS` khi ứng dụng lớn hơn sau khi đo bộ nhớ thực tế.

Render Free có thể tạm ngủ khi không có truy cập, nên lần mở lại có thể chậm. Đây là thời gian khởi động service, khác với thời gian build/deploy.

## Giám sát sau deploy

Mở `/monitoring` bằng tài khoản có SYSTEM_READ để xem JVM/HTTP/pool/worker. Cấu hình token metrics riêng, collector ngoài app và Notifications Render theo [giám sát và vận hành](GIAM_SAT_VA_VAN_HANH.md). Blueprint tạo token khi tạo service mới; service có sẵn cần kiểm Environment. DB probe mặc định tắt để tránh giữ Neon thức. Không cần bật Kafka/Redis. Import dashboard hoặc CI pass không tự kích hoạt gửi cảnh báo; phải thử receiver trên staging.

## Tài liệu tham chiếu

- [Render Docker](https://render.com/docs/docker)
- [Render Blueprint: autoDeployTrigger, buildFilter, envVars](https://render.com/docs/blueprint-spec)
- [Render Free](https://render.com/docs/free)
- [Docker GitHub Actions cache](https://docs.docker.com/build/cache/backends/gha/)
- [PostgreSQL JDBC](https://jdbc.postgresql.org/documentation/use/)

## Chuyển nhật ký API sang PostgreSQL

V005 tạo `api_request_journal` trên schema cấu hình khi startup initializer bật. `VIETROUTE_API_JOURNAL_ENABLED=true`, `VIETROUTE_API_JOURNAL_CAPACITY=10000`, `VIETROUTE_API_JOURNAL_MINUTES=10080` là mặc định mới (7 ngày). Service đã có key 300/30 từ bản RAM cần sửa Environment/đồng bộ Blueprint; default Java không ghi đè giá trị env cũ. Không dùng flag để tắt quyền/che dữ liệu.

V006 bổ sung [audit system](../02-phat-trien/AUDIT_VA_SYSTEM.md): tạo actor kỹ thuật ID 0, lấp NULL và đặt NOT NULL cho người tạo/cập nhật. Giữ actor đã có và mật khẩu admin. Nếu username system/ID 0 đã được dùng trước đó, startup rollback và báo xung đột; không tự chiếm tài khoản. Deploy với initializer bật trên đúng schema để áp dụng một lần, không chạy qua Maven build.

Tổng pool mặc định tối đa 4 nghiệp vụ + 1 nhật ký mỗi node. Nhật ký ghi đồng bộ sau request, timeout/circuit breaker ngắn và không có queue/cache RAM; có thể thiếu record nếu DB/slot ghi không sẵn sàng. Record đã commit tồn tại qua restart/node khác; không có đảm bảo ghi khi cùng DB chết hoặc process bị kill trước commit. Theo dõi failed-write counter ở UI và log `api_journal_write_unavailable`; xem [hướng dẫn chi tiết](NHAT_KY_API_VA_DEBUG.md).
