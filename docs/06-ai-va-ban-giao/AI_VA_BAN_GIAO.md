# AI và bàn giao khi đổi máy/người làm

Đọc khi mở phiên AI mới, quay lại task hoặc chuyển máy. Người mới hoàn toàn theo [5 bước ở mục lục](../README.md) trước; bản đồ module/luồng nằm trong [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md), phiên bản thư viện ở [công nghệ BE/UI](../01-bat-dau/CONG_NGHE_VA_THU_VIEN.md).

## 1. Nguồn cần đọc trước khi sửa

| Thứ tự | Nguồn | Dùng để biết |
|---|---|---|
| 1 | [AGENTS](../../AGENTS.md), `git status --short --branch`, `git log -5 --oneline` | Quy tắc, nhánh/commit và thay đổi có sẵn phải giữ nguyên |
| 2 | [Tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), [phân công](../05-ke-hoach/PHAN_CONG.md) | Đã làm/còn thiếu, tác giả, phụ thuộc và PR bằng chứng; kế hoạch không phải đóng góp đã hoàn thành |
| 3 | Chuyên đề theo [mục lục](../README.md), code/PR của phần đang sửa | Hợp đồng, quyết định/lý do và giới hạn thật tại đúng commit |
| 4 | [Git/PR](../../CONTRIBUTING.md), [format](../01-bat-dau/FORMAT_INTELLIJ.md) | Nhánh đúng người, kiểm tra và commit/PR tiếng Việt |

AI nêu ngắn gọn phần đã đọc, mục tiêu/mã việc, phần tái sử dụng/còn thiếu, nhánh/người thực hiện và cách kiểm trước khi sửa. Đây là xác nhận hiểu ngữ cảnh, không thêm vòng xin phép cho việc người dùng đã yêu cầu. Khi docs khác code, đối chiếu mốc/PR rồi cập nhật đúng nguồn; không tự ghi đè thay đổi có sẵn của người khác.

## 2. Điểm đọc cho từng AI

| Công cụ | File đi theo Git |
|---|---|
| Codex | [AGENTS.md](../../AGENTS.md) |
| Copilot | [.github/copilot-instructions.md](../../.github/copilot-instructions.md) chỉ dẫn mở AGENTS |
| Cursor | [.cursor/rules/vietroute.mdc](../../.cursor/rules/vietroute.mdc) dùng alwaysApply và dẫn về AGENTS |
| Claude Code | [CLAUDE.md](../../CLAUDE.md) import AGENTS |
| Gemini CLI | [GEMINI.md](../../GEMINI.md) import AGENTS |

Chỉ quản lý một bộ quy tắc ở AGENTS và chuyên đề liên quan. Phiên bản/quyền đọc/cài đặt công cụ có thể ảnh hưởng tự nạp; file chỉ dẫn không chứng minh AI đã đọc hoặc hiểu đúng. AI khác cần được cấp file; không dựa vào memory cá nhân hoặc câu trả lời “đã nhớ”. Chưa xác nhận phiên thực trên mọi sản phẩm chỉ bằng kiểm tĩnh.

Mẫu bắt đầu phiên mới:

```text
Tiếp tục repo VietRoute. Đọc AGENTS.md,
docs/06-ai-va-ban-giao/AI_VA_BAN_GIAO.md,
docs/05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md và PHAN_CONG.md cùng thư mục.
Kiểm git status/nhánh/commit và code liên quan.
Mã việc/mục tiêu: <điền>; nhánh/PR đang dở: <nếu có>.
Trước khi sửa, nêu nguồn đã đọc, phần đã có/còn thiếu và cách kiểm.
Tiếp tục trong phạm vi yêu cầu, giữ nguyên thay đổi của người khác.
```

## 3. Chuyển máy và chạy lại

Cài Git, JDK 21 (Java source target 17), Node 22 như CI; Maven dùng Wrapper của repo. Clone đúng repo, hoặc fetch/checkout đúng nhánh đã push nếu task còn dở:

```powershell
git clone https://github.com/hieuv526034/vietroute-web.git
cd vietroute-web
npm ci
npm run format:check
npm run check:rules
npm run check:docs
.\mvnw.cmd verify
node --test src/test/resources/dev/*.test.cjs
```

Linux/macOS dùng `./mvnw` hoặc `sh ./mvnw`. Cấu hình Git identity của chính thành viên theo CONTRIBUTING. Cách Run/Debug IntelliJ, profile `local` và env nằm trong [chạy local](../01-bat-dau/CHAY_LOCAL_VA_CLOUDINARY.md#nút-rundebug-trong-intellij); không bật DB_INIT/worker/provider chỉ để thử giao diện trên Neon production. `/health` lên chưa chứng minh DB/login hoạt động.

| Theo clone | Cần chuẩn bị riêng |
|---|---|
| Source/docs/AGENTS, migrations, lockfile, `.run`, codeStyles đã push | `.env`, Git credentials, env/thiết lập IDE cá nhân; lấy tên key từ [.env.example](../../.env.example), secret cấp qua kênh riêng |
| Test chia sẻ ở `src/test`, CI và bằng chứng PR | Dựng lại `target/`, `node_modules/`, cache; `.tools/` và process localhost máy cũ không phải công cụ/dịch vụ bàn giao |
| Cấu hình mẫu và adapter | Quyền/dữ liệu PostgreSQL, Cloudinary, Gmail, Render thuộc dịch vụ ngoài Git; có mẫu không đồng nghĩa đã hoạt động |

Java không tự đọc `.env`. Các node cùng môi trường cần cấu hình JWT tương thích; không tạo lại khóa production chỉ vì đổi máy, development dùng secret riêng. Bootstrap không đặt lại mật khẩu admin đã đổi. Port thử 18095 trong ảnh cũ không phải service luôn chạy; launcher local mặc định 8080. Không ghi secret/token/payload riêng tư vào docs, PR hoặc ảnh chụp.

## 4. Chốt thay đổi và lưu việc còn dở

Trước bàn giao source: `npm run format` → `npm run format:check` → `npm run check:rules` → `npm run check:docs` → test phù hợp → `git diff --check`. Review rồi stage file mới để checker nhận file Git theo dõi. Check rules kiểm tĩnh DTO/import/Date/FQN/Timestamp và điểm đọc AI; check docs kiểm link/ảnh, vị trí thư mục và mục lục. Chúng không thay review quyền/scope/transaction, Instant đúng nghiệp vụ, Swagger/locale hoặc comment tiếng Việt. Không đổi rule/xóa test để né lỗi; không format migration đã áp dụng.

CI chạy check theo phạm vi; Node chỉ dùng phát triển/CI, không thêm vào Docker production. Không có branch protection chỉ vì có workflow; quản trị viên cần cấu hình ruleset nếu muốn cưỡng chế mọi cách merge. Routine test dùng DB tạm/giả, không ghi/migrate/seed/cleanup lên production để kiểm build.

Quyết định dài hạn ghi ở chuyên đề (ngày/mốc, lý do, giới hạn, PR); trạng thái ghi ở tiến độ. Việc đang dở ghi trong PR draft/Issue của mã việc, hoặc một dòng Đang làm ở tiến độ nếu chưa có PR. Dùng mẫu:

```text
Mã việc / người thực hiện / nhánh / commit đối chiếu:
Mục tiêu và tiêu chí nghiệm thu:
Đã hoàn tất + bằng chứng:
Còn dở / lỗi tái hiện / phụ thuộc:
Quyết định + lý do + tài liệu đã cập nhật:
Kiểm tra đã chạy / chưa chạy / giới hạn:
Bước tiếp theo cụ thể:
```

Commit/push đúng nhánh trước khi rời máy; chưa đủ kiểm tra thì giữ draft. Chỉ ghi Đã có trên main khi có merge thật. Không sao chép toàn bộ quy tắc/backlog/log chat thành file memory mới, không đổi tác giả commit theo kế hoạch.

Nguồn cơ chế AI đã đối chiếu ngày 23/10/2026: [Codex](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [Copilot](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions), [Cursor](https://cursor.com/docs/rules), [Claude](https://code.claude.com/docs/en/memory), [Gemini](https://geminicli.com/docs/cli/gemini-md/). Repo không tự cài/bật các công cụ trên máy thành viên.
