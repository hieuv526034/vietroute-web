# AI và bàn giao khi đổi máy/người làm

## Bắt đầu một phiên

1. Đọc [AGENTS](../../AGENTS.md), [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md), [phân công/luồng thành viên](../05-ke-hoach/PHAN_CONG.md) và chuyên đề theo [5 bước](../README.md).
2. Kiểm git status --short --branch, commit gần nhất và PR liên quan. Nêu mã việc, nhánh/người làm, đã có/còn thiếu và cách kiểm; không ghi đè thay đổi của người khác.
3. Quy tắc ở AGENTS/chuyên đề; trạng thái/bằng chứng ở tiến độ/PR. Chat, kế hoạch, .tools/process local không tự chứng minh hoàn thành hoặc đã deploy.
4. Tiếp tục việc đã được phép. Nhánh <member>/<task> từ dev, PR/check/review → dev; chỉ lệnh phát hành mới cho phép main/production. Không đổi author để khớp phân công.

## Một bộ quy tắc cho các công cụ

| Công cụ | Điểm vào repo |
|---|---|
| Codex | AGENTS.md |
| Copilot | .github/copilot-instructions.md dẫn về AGENTS |
| Cursor | .cursor/rules/vietroute.mdc alwaysApply, dẫn về AGENTS |
| Claude Code | CLAUDE.md import AGENTS |
| Gemini CLI | GEMINI.md import AGENTS |

Không nhân bản quy tắc theo từng công cụ, không dùng memory riêng thay source. File có trong repo không chứng minh một phiên AI đã đọc/hiểu; xác nhận nguồn trước khi sửa. Công cụ/máy khác cần được cấp quyền và file tương ứng, repo không tự cài hoặc cấu hình tài khoản cho thành viên.

## Đổi máy và bảo toàn cấu hình

Clone/pull đúng nhánh đã có công việc cần dùng; cài JDK21/Node22, Maven dùng Wrapper. .env local bị ignore Git; copy .env.example rồi điền riêng VIETROUTE_DB_PASSWORD đang là XXX, OS/IDE ưu tiên file. Chủ dự án cho phép giữ nguyên mọi password/key khác trong .env.example để bàn giao; không tự che thêm. Git credentials và thiết lập IDE cá nhân không theo source; lịch sử .env cũ không viết lại. Không in giá trị vào docs/log/chat/ảnh.

Chạy theo [local](../01-bat-dau/CHAY_LOCAL.md). WAR/Render nhận env, không tự đọc .env hoặc đóng launcher. Không tự xoay JWT/reset password/seed DB thật để thử build; init opt-in giữ dữ liệu/grant đã thay đổi. Với DB hiện hành cần timezone chuẩn; Oracle vẫn chặn runtime. Port ảnh/phiên cũ không chứng minh service đang chạy.

## Kết thúc và kiểm tra

Theo npm ci → format → format:check → check:rules → check:docs → test phù hợp → git diff --check. Review rồi stage file mới để checker thấy đủ Git. Không sửa formatter/rule/check để né lỗi, không format SQL đã áp. Routine test dùng DB riêng, không root .env hoặc DB thật. Docs-only kiểm link/anchor/nội dung source và quy tắc; không cần khởi tạo DB hay phát hành image.

Ghi bàn giao vào tiến độ/PR hiện có, không tạo file memory/log mới cho cùng sự việc:

```text
Mã việc / người thực hiện / nhánh / commit đối chiếu:
Mục tiêu và kết quả đã có:
Quyết định, lý do, tài liệu đã cập nhật:
Kiểm thực tế / chưa chạy / giới hạn:
Còn dở / phụ thuộc / bước tiếp:
```

Chỉ ghi Đã có trên dev/main khi có merge thật, Live khi đối chiếu deployment và HTTP/DB phù hợp. Dọn hướng dẫn đã thay thế bằng gộp/xóa và cập nhật link; lịch sử tra Git. Khi viết hướng dẫn demo cần giữ ranh giới source/PR/production, dữ liệu giả lập, giới hạn và đóng góp thật của từng người.
