# Quy trình AI dùng chung cho mọi thành viên

Mục tiêu: phiên AI mới trên máy khác lấy được cùng quy tắc, trạng thái và công cụ kiểm tra từ Git. Đây là quy trình của repository, không phải cam kết mọi sản phẩm AI đều tự nạp file hoặc luôn tuân thủ đúng. AI cần được mở tại đúng repo, có quyền đọc file và người dùng cần kiểm tra ngữ cảnh đã nạp.

## Một nguồn quy tắc, nhiều điểm đọc

| Công cụ | File dự án | Cách dùng trong repo này |
|---|---|---|
| Codex | [AGENTS.md](../AGENTS.md) | Quy tắc gốc và thứ tự đọc trước khi sửa |
| GitHub Copilot | [.github/copilot-instructions.md](../.github/copilot-instructions.md) | Chỉ dẫn mở AGENTS và quy trình này; bật custom instructions trong công cụ đang dùng |
| Cursor | [.cursor/rules/vietroute.mdc](../.cursor/rules/vietroute.mdc) | Rule alwaysApply dẫn về AGENTS; vẫn kiểm file/rule đã được nạp |
| Claude Code | [CLAUDE.md](../CLAUDE.md) | Import AGENTS, không sao chép nội dung quy tắc |
| Gemini CLI | [GEMINI.md](../GEMINI.md) | Import AGENTS, không sao chép nội dung quy tắc |
| AI khác / chat không có quyền đọc repo | Mẫu yêu cầu bên dưới | Đính kèm/cấp quyền đọc các file cần thiết; không giả định AI đã đọc chỉ vì tồn tại file |

Các file trên được commit/push cùng source, không có đường dẫn máy cá nhân, secret hoặc thiết lập tài khoản. Khi đổi quy tắc, sửa AGENTS/tài liệu chuyên đề liên quan; file chỉ dẫn theo công cụ chỉ đổi khi cơ chế nạp thay đổi. Giữ phần hướng dẫn luôn nạp ngắn gọn, không nhét mọi log/chat vào đó.

## Bắt đầu và tiếp tục một việc

1. Đọc AGENTS → [bàn giao](BAN_GIAO_VA_TIEP_TUC.md) → [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) → [phân công](PHAN_CONG.md); [mục lục](README.md) dẫn đến quy ước/API/schema/UI và tài liệu theo việc đang làm.
2. Kiểm nhánh, commit và thay đổi chưa commit; đối chiếu code/PR để biết phần thật sự đã có. Không reset/ghi đè thay đổi của thành viên khác hoặc gán lại tác giả theo kế hoạch.
3. Trước khi sửa, AI nêu ngắn gọn: mã việc/mục tiêu, nguồn đã đọc, phần sẽ tái sử dụng, phần còn thiếu, nhánh/người thực hiện và cách kiểm. Đây là xác nhận hiểu ngữ cảnh, không phải vòng xin phép thêm cho công việc đã được người dùng yêu cầu.
4. Đọc controller → service/transaction/quyền → repository của phần liên quan trước khi mở rộng. Dùng component và formatter đã có; không tự thêm framework, quy tắc indent hoặc bộ validation/UI thứ hai.
5. Khi gặp khác biệt giữa tài liệu/code, đối chiếu mốc commit và PR; ghi rõ phát hiện và cập nhật đúng nguồn. Yêu cầu mới của người dùng có thể thay đổi quy ước trong phạm vi được phép; không dùng rule repo để bỏ qua chỉ dẫn cấp cao hơn của môi trường làm việc.

Prompt kiểm tra ngữ cảnh, dùng trên máy/công cụ mới:

```text
Trước tiên chỉ đọc repo VietRoute: AGENTS.md, docs/QUY_TRINH_AI.md,
docs/BAN_GIAO_VA_TIEP_TUC.md, docs/TIEN_DO_VA_KE_HOACH_NHOM.md
và docs/PHAN_CONG.md. Kiểm git status/nhánh/commit và code liên quan.
Hãy nêu các file đã đọc, stack hiện có, nguồn trạng thái, quy tắc DTO/Instant,
format/import/comment, nhánh/commit, cách kiểm và phần còn thiếu.
Chưa sửa file trong lượt kiểm tra này.
```

Chỉ tiếp tục sau khi câu trả lời khớp repo hiện tại. Nếu AI không đọc được file hoặc custom instructions bị tắt, cấp đúng file hoặc bật tính năng theo tài liệu công cụ; không coi câu trả lời “đã nhớ” là bằng chứng. Mẫu giao việc đang dở nằm tại [bàn giao](BAN_GIAO_VA_TIEP_TUC.md), không tạo backlog thứ hai trong chat/memory cá nhân.

## Chốt format và quy tắc bằng công cụ chung

Tại thư mục gốc, stage file mới sau khi đã xem diff để các kiểm tra repository nhận diện đúng file sẽ được chia sẻ:

```powershell
npm ci
npm run format
npm run format:check
npm run check:rules
npm run check:docs
node --test src/test/resources/dev/*.test.cjs
git diff --check
# Khi thay đổi Java/hành vi: chạy thêm Maven verify và ca liên quan.
```

| Kiểm tra | Phạm vi tự động |
|---|---|
| `format:check` | Java/HTML/CSS/JS/XML theo formatter và lockfile của repo; IntelliJ dùng Project scheme/EditorConfig khi soạn thảo |
| `check:rules` | File chỉ dẫn AI vẫn dẫn về AGENTS; import Java tường minh, tên DTO viết hoa, chặn Date cũ/FQN và Timestamp ở entity/DTO/service |
| `check:docs` | Link nội bộ Markdown/ảnh/import AI trỏ đến file được Git theo dõi; mọi Markdown trong docs có đường từ mục lục |
| Kiểm thử phù hợp | Logic/quyền/transaction/luồng thực sự bị thay đổi; không thử ghi lên production |

Checker là phép kiểm tĩnh có phạm vi rõ ràng, không chứng minh AI đã đọc/hiểu, mọi timestamp đều đúng nghiệp vụ hoặc comment đã đủ dễ học. Reviewer vẫn đọc diff, luồng quyền/scope, audit, validation, Swagger/locale và mô tả tiếng Việt. Không đổi cấu hình check, xóa test hoặc thêm ngoại lệ chỉ để né lỗi.

GitHub Actions chạy lại check tương ứng khi PR thay đổi source/rule/docs. Check thất bại cần sửa trước merge theo quy ước nhóm. Muốn GitHub cưỡng chế chặn mọi cách merge, quản trị viên cần cấu hình branch protection/ruleset phù hợp; có workflow chưa tự chứng minh main đã được bảo vệ. Không tắt path filters để docs/test làm Docker/Render build. Node/kiểm tài liệu chỉ dùng phát triển/CI, không thêm vào production image.

## Ghi nhớ sau mỗi lượt, kể cả đang dở

Không tạo file “memory” với bản sao toàn bộ source/quy tắc. Ghi vào đúng nơi đã có:

| Loại thông tin | Nơi cập nhật |
|---|---|
| Quy tắc chung đã thống nhất | AGENTS và tài liệu chuyên đề được link từ [mục lục](README.md) |
| Trạng thái đã làm/chưa làm và phụ thuộc | [Tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), có PR/commit bằng chứng |
| Quyết định thiết kế cần dùng lại | Tài liệu chuyên đề: ngày/mốc, lý do, phương án chọn, giới hạn và PR; không ghi như đã triển khai nếu mới đề xuất |
| Việc còn dở ở nhánh | PR draft/Issue của đúng mã việc, theo mẫu dưới; nếu chưa có thì ghi một dòng có nhãn Đang làm trong tiến độ, không đánh dấu Đã có trên main |
| Kết quả kiểm thử của một đợt | PR và tài liệu nghiệm thu tương ứng; phân biệt lịch sử và kết quả vừa chạy |

Mẫu bàn giao trong PR/Issue:

```text
Mã việc / người thực hiện / nhánh / commit đối chiếu:
Mục tiêu và tiêu chí nghiệm thu:
Đã hoàn tất (file/API/UI/SQL và bằng chứng):
Còn dở / lỗi tái hiện / phụ thuộc:
Quyết định + lý do + tài liệu đã cập nhật:
Kiểm tra đã chạy và kết quả:
Chưa kiểm / giới hạn:
Bước tiếp theo cụ thể:
```

Trước chuyển máy, commit/push đúng nhánh phần được phép chia sẻ. Chỉ đóng task/cập nhật đã merge khi có bằng chứng thật. Một thay đổi của người khác còn ở local không được đưa vào commit bàn giao của mình. Env/secret, cache, `.tools/` và process máy cũ không đi theo clone; xem phần thiết lập máy mới trong tài liệu bàn giao.

## Nguồn chính thức và giới hạn kiểm chứng

Cơ chế file chỉ dẫn đã đối chiếu ngày 23/10/2026: [Codex AGENTS](https://learn.chatgpt.com/docs/agent-configuration/agents-md), [GitHub Copilot](https://docs.github.com/en/copilot/how-tos/copilot-on-github/customize-copilot/add-custom-instructions/add-repository-instructions), [Cursor rules](https://cursor.com/docs/rules), [Claude memory/import](https://code.claude.com/docs/en/memory), [Gemini context/import](https://geminicli.com/docs/cli/gemini-md/). Phiên bản, quyền workspace và cài đặt cá nhân ảnh hưởng việc nạp; repository không tự cài hoặc bật các sản phẩm này trên máy thành viên. Kiểm tĩnh file/import và CI không thay thế thử phiên mới trong từng công cụ.
