# Bàn giao, chuyển máy và tiếp tục công việc

Tài liệu dành cho thành viên quay lại dự án, chuyển máy hoặc mở phiên hỗ trợ code mới. Cập nhật ngày 23/10/2026, đối chiếu source tại `d722089` sau [PR #30](https://github.com/hieuv526034/vietroute-web/pull/30). Khi đọc ở commit mới hơn, kiểm lịch sử Git và bảng tiến độ trước khi dùng mốc này.

Quy tắc và ngữ cảnh cần dùng lại được lưu trong repository và đẩy lên GitHub. Không giả định cuộc chat, file chưa push, thiết lập IDE hay dịch vụ đang chạy trên máy cũ sẽ có trên máy mới. Người hỗ trợ cần đọc lại tài liệu và code của đúng commit, không chỉ dựa vào lời kể rằng một phần đã xong.

## 1. Đọc gì trước khi tiếp tục

| Thứ tự | Đọc / kiểm tra | Mục đích |
|---|---|---|
| 1 | [AGENTS.md](../AGENTS.md), `git status --short --branch`, `git log -5 --oneline` | Biết quy tắc, nhánh, thay đổi chưa commit và mốc source thực tế |
| 2 | [Mục lục docs](README.md), [Tổng quan hệ thống](TONG_QUAN_HE_THONG.md) | Nhớ kiến trúc MVC/JDBC/WAR và chọn tài liệu theo nhóm |
| 3 | [Tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), [Phân công](PHAN_CONG.md), [Nghiệm thu nền tảng](NGHIEM_THU_NEN_TANG.md) | Phân biệt đã merge, kế hoạch, người thực hiện và việc còn thiếu |
| 4 | Tài liệu chuyên đề liên quan trong mục lục, code và PR của phần sắp sửa | Hiểu hợp đồng và luồng thật trước khi đổi code |
| 5 | [Quy trình Git/PR](../CONTRIBUTING.md), [Format IntelliJ](FORMAT_INTELLIJ.md) | Làm nhánh đúng người, kiểm tra và bàn giao bằng PR tiếng Việt |

Người mới hoàn toàn đi theo lộ trình 1 → 6 trong mục lục. Người quay lại không phải đọc tất cả docs; đọc năm bước trên rồi đi vào đúng phần nhận việc.

## 2. Mỗi thông tin có một nơi quản lý chính

| Cần biết | Nguồn chính |
|---|---|
| Quy tắc bắt buộc khi sửa repo | [AGENTS.md](../AGENTS.md); phần giải thích chi tiết theo link trong file |
| Tài liệu nào đọc trước/sau | [docs/README.md](README.md) |
| Công nghệ, thư viện và chỗ tra phiên bản | [Công nghệ BE → UI](CONG_NGHE_VA_THU_VIEN.md), đối chiếu `pom.xml`, `Dockerfile`, `package-lock.json` |
| Kiến trúc, luồng request và hợp đồng API | [Tổng quan](TONG_QUAN_HE_THONG.md), [luồng base](LUONG_XU_LY_BASE.md), [API base](API_BASE.md) |
| Đã làm/chưa làm và bằng chứng PR | [Tiến độ nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md); đối chiếu Git/PR và code |
| Ai phụ trách, username/email, nhánh | [Phân công](PHAN_CONG.md); không suy ra đóng góp đã hoàn thành từ kế hoạch |
| Luồng nghiệp vụ dự kiến và ca nghiệm thu | [Đặc tả](DAC_TA_HE_THONG_XE_KHACH.md) |
| Thiết lập môi trường | [.env.example](../.env.example), [IntelliJ](CLOUDINARY_VA_INTELLIJ.md), [Render](DEPLOY_RENDER.md); giá trị secret cấp riêng |
| Ca đã kiểm và giới hạn vận hành | [Nghiệm thu](NGHIEM_THU_NEN_TANG.md), [vận hành](GIAM_SAT_VA_VAN_HANH.md), PR tương ứng |

Giữ đường dẫn Markdown hiện có để link từ PR/Issue/code không gãy; nhóm đọc được quản lý tại mục lục. Khi đổi quy tắc, sửa nguồn chính và link đến nó, không tạo bản sao quy tắc trong nhiều file. Tài liệu có mốc cũ là bằng chứng lịch sử, không tự ghi đè số test hoặc kết quả cũ thành kết quả mới.

## 3. Trạng thái cần nhớ tại mốc bàn giao

Nền tảng common/auth/catalog/media và UI hệ thống đã có; nghiệp vụ nhà xe, địa điểm/tuyến/chuyến, booking/tồn chỗ và tracking chưa được triển khai đầy đủ. Trạng thái từng mã việc và phụ thuộc nằm trong bảng tiến độ, không ghi hai bản backlog khác nhau ở đây.

Các thay đổi gần nhất đã merge do `hieuv526034` thực hiện:

| PR | Kết quả cần giữ khi làm tiếp |
|---|---|
| [#26](https://github.com/hieuv526034/vietroute-web/pull/26) | Mục lục theo thứ tự đọc, code style IntelliJ và quy trình format |
| [#27](https://github.com/hieuv526034/vietroute-web/pull/27) | Nhật ký API ghi PostgreSQL, mặc định 7 ngày/10.000 dòng; không giữ hàng đợi lịch sử trong RAM |
| [#28](https://github.com/hieuv526034/vietroute-web/pull/28) | V006 và audit system ID 0; system không đăng nhập/nhận role hoặc JWT |
| [#29](https://github.com/hieuv526034/vietroute-web/pull/29) | Bảng/phân trang/validation dùng chung, điều hướng Home/login theo principal, cURL URL thực + token placeholder, tài liệu công nghệ |
| [#30](https://github.com/hieuv526034/vietroute-web/pull/30) | Menu Hệ thống cách mục 6px, bộ lọc/nút danh mục cùng hàng, modal có sẵn dấu bắt buộc |

Mốc PR #30: Maven verify đạt 91 test Java, 10 test Node, format và tài liệu đạt; ba check CI đạt, gồm Docker và PostgreSQL tạm. Kiểm UI vi/en, desktop/mobile 390px bằng HTML Thymeleaf thật + API fixture riêng. Đây là kết quả của mốc đã nêu, không thay thế kiểm tra sau thay đổi tiếp theo hoặc xác nhận production.

Chưa xác nhận Render Live, Gmail/Cloudinary thật, receiver cảnh báo, backup/restore và tải staging chỉ từ việc source có adapter/mẫu. Kafka/Redis chưa cần bật. Điểm phụ thuộc đầu tiên trước CRUD nhà xe là SCOPE01 và hợp đồng cấp tài khoản; xem thứ tự trong bảng tiến độ. Lượt hoàn thiện tài liệu này không bắt đầu một module nghiệp vụ mới.

## 4. Bản đồ đọc code theo việc cần làm

| Việc | Điểm bắt đầu trong source |
|---|---|
| Chạy app / hiểu hai Spring context | [VietRouteApplication](../src/dev/java/vn/vietroute/dev/VietRouteApplication.java) → [WebApplicationInitializer](../src/main/java/vn/vietroute/common/config/WebApplicationInitializer.java), [RootConfig](../src/main/java/vn/vietroute/common/config/RootConfig.java), [WebConfig](../src/main/java/vn/vietroute/common/config/WebConfig.java) |
| Login/JWT/quyền | [AuthController](../src/main/java/vn/vietroute/auth/controller/AuthController.java) → [AuthService](../src/main/java/vn/vietroute/auth/service/AuthService.java), [JwtService](../src/main/java/vn/vietroute/auth/service/JwtService.java); đọc [SecurityConfig](../src/main/java/vn/vietroute/auth/config/SecurityConfig.java) và [AccessService](../src/main/java/vn/vietroute/auth/service/AccessService.java) |
| Học mẫu CRUD | [CatalogController](../src/main/java/vn/vietroute/business/catalog/controller/CatalogController.java) → [CatalogService](../src/main/java/vn/vietroute/business/catalog/service/CatalogService.java) → [CatalogRepository](../src/main/java/vn/vietroute/business/catalog/repository/CatalogRepository.java); xem [BaseController](../src/main/java/vn/vietroute/common/base/BaseController.java) |
| Env/route/regex/schema/transaction | [ApplicationConstants](../src/main/java/vn/vietroute/common/constant/ApplicationConstants.java), [DatabaseSettings](../src/main/java/vn/vietroute/common/config/DatabaseSettings.java), [JdbcConnectionFactory](../src/main/java/vn/vietroute/common/jdbc/JdbcConnectionFactory.java), [JdbcTransactions](../src/main/java/vn/vietroute/common/jdbc/JdbcTransactions.java) |
| Tab/modal/bảng/form | [layout.html](../src/main/webapp/WEB-INF/views/fragments/layout.html), [workspace.js](../src/main/webapp/assets/js/workspace.js), [table.js](../src/main/webapp/assets/js/table.js), [forms.js](../src/main/webapp/assets/js/forms.js); trang mẫu [admin.html](../src/main/webapp/WEB-INF/views/auth/admin.html) và [catalog.js](../src/main/webapp/assets/js/catalog.js) |
| TraceId / export debug | [ApiJournalService](../src/main/java/vn/vietroute/common/monitoring/service/ApiJournalService.java) → [ApiJournalRepository](../src/main/java/vn/vietroute/common/monitoring/repository/ApiJournalRepository.java); [hướng dẫn nhật ký](NHAT_KY_API_VA_DEBUG.md) chốt dữ liệu được lưu/xuất |
| CI / deploy | [build.yml](../.github/workflows/build.yml), [format.yml](../.github/workflows/format.yml), [docs.yml](../.github/workflows/docs.yml), [Dockerfile](../Dockerfile), [render.yaml](../render.yaml) |

Các link trỏ đến file tương đối trong repo để dùng được sau clone, không phụ thuộc đường dẫn `C:\Users\...` hoặc port thử nghiệm trên máy cũ.

## 5. Thiết lập máy mới

### Lấy đúng source và công cụ

Cài Git, JDK 21 và Node theo môi trường phát triển đã dùng trong CI (Node 22). Java source target 17; Docker hiện dùng Java 21. Maven Wrapper trong repo quản lý phiên bản Maven; không cần mang Maven cache hoặc thư mục build từ máy cũ. Quyền truy cập GitHub của từng thành viên được thiết lập bằng tài khoản của chính người đó.

```powershell
git clone https://github.com/hieuv526034/vietroute-web.git
cd vietroute-web
git status --short --branch
git log -5 --oneline
java -version
node --version
npm ci
npm run format:check
.\mvnw.cmd verify
node --test src/test/resources/dev/*.test.cjs
```

Linux/macOS dùng `./mvnw` hoặc `sh ./mvnw` thay `mvnw.cmd`. Cấu hình danh tính Git của chính thành viên theo [CONTRIBUTING.md](../CONTRIBUTING.md). Các lệnh trên là kiểm tra phát triển; không dùng cấu hình production để thử migration/seed hoặc ghi dữ liệu. Docker integration trên GitHub sử dụng PostgreSQL tạm.

Nếu công việc còn ở nhánh thành viên, fetch và checkout đúng nhánh đã push thay vì bắt đầu lại từ main hoặc ghi đè file. Trước khi rời máy cũ, kiểm tra `git status`, commit/push đúng nhánh những thay đổi được phép chia sẻ và ghi phần còn dở ở PR; chưa đủ kiểm tra thì giữ PR draft, không merge cho có trạng thái xong.

### Cấp lại môi trường và chạy IntelliJ

1. Lấy tên key và placeholder từ [.env.example](../.env.example), tạo cấu hình cá nhân. Secret của DB/JWT/Gmail/Cloudinary/metrics nhận qua kênh riêng hoặc kho quản lý secret, không qua Git/Markdown/ảnh chụp.
2. Java không tự đọc `.env`. Nạp env vào Run/Debug của IntelliJ hoặc Tomcat theo [hướng dẫn IDE](CLOUDINARY_VA_INTELLIJ.md); `.run` được chia sẻ không chứa secret. Chọn JDK, bật Maven profile `local`, tắt `production`, Reload Maven và chạy **VietRoute Local**.
3. Trên máy mới, không bật DB_INIT/worker/provider chỉ để xem giao diện. Muốn đăng nhập cần DB development đã migrate; chỉ khởi tạo có chủ đích trên đúng DB/schema được phép. Bootstrap không đặt lại mật khẩu admin đã đổi.
4. Các node cùng môi trường phải có cấu hình JWT tương thích; không tự tạo lại khóa production vì đổi máy phát triển. Development dùng môi trường/secret riêng. Xem [API/auth](API_BASE.md) khi cần xoay khóa hoặc kiểm token.
5. Chọn hostname/port thống nhất theo env/Run config. URL `127.0.0.1:18095` trong ảnh kiểm thử cũ không phải địa chỉ deploy hoặc server được bảo đảm còn chạy; app local mặc định dùng cổng 8080 theo launcher.

| Thứ ở máy cũ | Sau clone trên máy mới |
|---|---|
| Source/docs/AGENTS, migrations, Maven/npm lock, `.run`, codeStyles đã commit/push | Có theo commit/nhánh đã lấy |
| `.env`, secret, cấu hình IDE cá nhân, Git credentials | Cấp lại riêng; không có trong clone |
| `target/`, `node_modules/`, cache | Dựng lại bằng công cụ của repo |
| `.tools/`, script tạm, API fixture và process localhost | Bị ignore; không coi là công cụ bàn giao hoặc dịch vụ vẫn chạy. Bằng chứng đã kiểm nằm trong PR/docs, kiểm thử chia sẻ nằm ở `src/test`/CI |
| Dữ liệu PostgreSQL / Cloudinary / Gmail / Render | Thuộc dịch vụ bên ngoài, không nằm trong Git; cấu hình và quyền truy cập phải được cấp đúng môi trường |
| Nội dung cuộc chat và công việc chưa push | Không dùng làm nguồn duy nhất để tiếp tục; ghi quyết định/việc dở vào tài liệu hoặc PR trước khi chuyển |

## 6. Mẫu yêu cầu cho phiên làm việc mới

```text
Tiếp tục repo vietroute-web trên máy này.
Đọc AGENTS.md, docs/README.md, docs/BAN_GIAO_VA_TIEP_TUC.md,
docs/TIEN_DO_VA_KE_HOACH_NHOM.md và tài liệu liên quan đến mã việc bên dưới.
Kiểm tra git status/nhánh/commit và code thực tế trước khi sửa.

Mã việc: <mã và mục tiêu cụ thể>.
Nhánh/PR đang làm: <nếu có>.
Đã hoàn tất: <đầu ra, commit/PR>.
Còn dở hoặc đang lỗi: <thao tác, kết quả mong muốn, lỗi đã lọc secret>.
Đã kiểm tra: <lệnh/ca/kết quả; phần chưa chạy ghi rõ>.
Làm tiếp theo quy ước của repo; không suy ra chức năng đã xong từ kế hoạch cũ.
```

## 7. Giữ ngữ cảnh luôn cập nhật

Sau mỗi task: cập nhật trạng thái/bằng chứng ở bảng tiến độ, sửa tài liệu chuyên đề nếu hợp đồng/luồng thay đổi và thêm link vào mục lục nếu có tài liệu mới. PR ghi đầu ra, test thực sự đã chạy, giới hạn, việc còn dở và bước tiếp theo. Quyết định dùng lại qua nhiều phiên được lưu vào docs/AGENTS, không chỉ nhắn trong chat.

Không ghi secret, payload người dùng hoặc thông tin kết nối có mật khẩu vào phần bàn giao. Không viết lại lịch sử hoặc đổi tác giả để khớp bảng phân công. Khi tài liệu khác code, đối chiếu commit/PR rồi cập nhật đúng trạng thái trước khi tiếp tục.
