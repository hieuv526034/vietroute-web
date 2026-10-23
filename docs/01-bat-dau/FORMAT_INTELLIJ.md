# Format code trong IntelliJ IDEA

## Những file dùng chung của nhóm

| File | Vai trò |
|---|---|
| [`.editorconfig`](../../.editorconfig) | UTF-8, LF, indent 4, dòng 120; các key `ij_java_*` cho Java trong IntelliJ |
| [`.idea/codeStyles/Project.xml`](../../.idea/codeStyles/Project.xml) | Project scheme để IntelliJ dùng/import: indent, dòng trống, annotation, import tường minh |
| [`.idea/codeStyles/codeStyleConfig.xml`](../../.idea/codeStyles/codeStyleConfig.xml) | Chọn cấu hình cấp project khi clone, không dùng scheme cá nhân của người khác |
| [`.prettierrc.json`](../../.prettierrc.json) và [`package-lock.json`](../../package-lock.json) | Formatter Java/HTML/CSS/JS/XML đã khóa phiên bản, đầu ra cuối trước commit |
| [Run Format](../../.run/VietRoute%20Format.run.xml) / [Run Format Check](../../.run/VietRoute%20Format%20Check.run.xml) | Nút Run các npm script trong IntelliJ có hỗ trợ Node/npm |

Git chỉ chia sẻ hai file `codeStyles` trên; `.idea/workspace.xml`, cấu hình chạy cá nhân có env và các file IDE khác tiếp tục bị ignore. Không đưa mật khẩu/token vào cấu hình chia sẻ. Bộ format chỉ dùng lúc phát triển; Docker/Render vẫn chỉ build `src/main` bằng Maven production.

## Thiết lập lần đầu

1. Cài JDK 17/21 và Node.js, mở `pom.xml` bằng IntelliJ, chạy `npm ci` trong Terminal tại thư mục project. Bước này chỉ cài công cụ format từ lockfile, không khởi động app hay đụng DB.
2. **Settings → Editor → Code Style**: chọn **Project** và bật **Enable EditorConfig support**. Project đã có scheme chia sẻ; các giá trị EditorConfig được ưu tiên so với scheme. Nếu chưa nhận, Reload/Reopen project và đối chiếu file `.idea/codeStyles/Project.xml` với các giá trị ở bảng dưới. Khi trao đổi scheme với IDE khác, dùng Export/Import Scheme theo định dạng IntelliJ XML do IDE xuất ra; không nhập file workspace cá nhân.
3. Nếu IDE tự đoán indent khác, tắt **Detect and use existing file indents for editing** trong Code Style; kiểm widget indent ở status bar là 4 spaces. Không dùng scheme mặc định cá nhân để ghi đè project.
4. Trong **Settings → Languages & Frameworks → Node.js** (hoặc mục JavaScript Runtime tương ứng phiên bản), chọn Node/npm đã cài. Chọn **VietRoute Format** rồi Run; tiếp theo **VietRoute Format Check** rồi Run. Cấu hình dùng `$PROJECT_DIR$` và Node của project, không khóa đường dẫn máy của Hieu.
5. Nếu bản IntelliJ không có plugin Node/npm, mở `package.json` và Terminal, chạy `npm run format` rồi `npm run format:check`. Java Code Style/EditorConfig vẫn dùng được; không cần nâng cấp IDE để build WAR.

## Quy tắc khi viết và format

| Nội dung | Quy tắc |
|---|---|
| Indent | 4 dấu cách, continuation 4; JSON/YAML/Markdown indent 2 theo EditorConfig |
| Dòng | Mục tiêu 120 ký tự; không bẻ chuỗi SQL/URL làm đổi giá trị |
| Khoảng cách | Một dòng trống giữa field/method/class và các khối xử lý có ý nghĩa |
| Annotation/lệnh | Annotation class/field/method tách dòng; mỗi lệnh riêng, không nhét nhiều lệnh vào một dòng |
| Import | Tường minh đầu file; không `*`, không FQN trong thân code. Ngưỡng auto wildcard 999 và danh sách package wildcard rỗng |
| Tên/kiểu | `DTO` viết hoa, package `dto` viết thường, timestamp Java dùng `Instant` |
| Comment | Tiếng Việt giải thích luồng/transaction và nhánh khó; format không tự sinh mô tả nghiệp vụ |
| Lambda | IDE inspection đề xuất method reference khi chỉ chuyển tiếp; chỉ đổi khi không thay hành vi |

`Ctrl+Alt+L` format file/vùng chọn bằng formatter native của IntelliJ; `Ctrl+Alt+O` optimize imports. Không chạy cleanup/refactor toàn repository chỉ để xóa cảnh báo. Formatter không tự kiểm đúng quyền, scope, transaction, tên DTO, kiểu Instant hoặc nội dung comment; reviewer vẫn đối chiếu [quy ước phát triển](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md).

Native IntelliJ và Prettier Java có khác biệt ở wrap chain/annotation/record. **Không cam kết hai formatter cho đầu ra giống từng byte.** Dùng IntelliJ để soạn code dễ đọc; trước commit, bấm **VietRoute Format** rồi **VietRoute Format Check** để chốt một kết quả chung, tránh hai người liên tục đổi format qua lại. Khi đổi rule, cập nhật cả `.editorconfig`, Project scheme và cấu hình Prettier liên quan rồi xem diff; không thay rule chỉ trên máy cá nhân.

## Tự format khi lưu và trước bàn giao

Trong **Settings → Tools → Actions on Save**, bật **Reformat code**, chọn **Changed lines** và phạm vi Java/HTML/CSS/JS/XML đang sửa. Có thể bật Optimize imports cho Java. Đây là lựa chọn cá nhân của IDE, repo không ghi đè `workspace.xml` của từng người. Không bật Rearrange/code cleanup toàn bộ một cách tự động. Với bản IDE có Prettier plugin, tránh để hai formatter cùng chạy trên một loại file lúc save; chọn một cơ chế rồi chốt lại bằng npm script trước commit.

Loại trừ `src/main/resources/db/migration/*.sql`, `target`, `.tools`, `.m2`, `node_modules` và file credential khỏi format-on-save/whole-project. SQL migration đã áp dụng có checksum: không sửa dù chỉ whitespace. EditorConfig không trim/chèn newline cho migration; `.prettierignore` và glob npm không format SQL. Nếu chọn **Reformat directory**, luôn giới hạn scope, không chọn toàn project kèm thư mục DB.

Mỗi lần hoàn tất thay đổi source:

```powershell
npm run format
npm run format:check
npm run check:rules
npm run check:docs
git diff --check
# Nếu có thay đổi Java/hành vi, chạy thêm Maven verify theo phạm vi thực tế.
```

Người hỗ trợ code trong repo cũng phải thực hiện quy trình này ở các lượt sau theo `AGENTS.md`. Xem diff trước stage để bảo đảm không có sửa logic, migration hoặc file cá nhân ngoài phạm vi. Không gắn format vào startup app, Maven package hay Docker: thao tác deploy không được tự sửa source.

Các check repository chỉ đọc file Git theo dõi; stage file mới sau khi review trước khi chạy. `check:rules` bổ sung kiểm tĩnh DTO/import/Date/FQN/Timestamp ở contract và điểm đọc AI; `check:docs` kiểm link nội bộ/ảnh/mục lục. Hai lệnh không thay thế formatter, kiểm nghiệp vụ hoặc review comment tiếng Việt. Xem [quy trình AI](../06-ai-va-ban-giao/AI_VA_BAN_GIAO.md) để thống nhất cách nạp quy tắc và bàn giao giữa các công cụ/máy.

Tham khảo chính thức: [Project code style scheme](https://www.jetbrains.com/help/idea/configuring-code-style.html), [EditorConfig](https://www.jetbrains.com/help/idea/editorconfig.html), [Reformat và Actions on Save](https://www.jetbrains.com/help/idea/reformat-and-rearrange-code.html). Tên menu có thể khác giữa phiên bản IDE; project hiện giữ rule độc lập với phiên bản IDE cài trên máy.

## Kiểm chứng đợt cấu hình

`npm ci`, format/check và parse XML đạt; key Code Style và định dạng npm Run đã đối chiếu với class của IntelliJ cài trên máy. Thử formatter CLI trên fixture riêng dừng ở `No valid license found`, chưa xác nhận native reformat/Run trong IDE bằng thao tác UI. Không thay license/cài đặt IDE để vượt lỗi này. Chạy npm script trực tiếp vẫn hoạt động và cho kết quả format chuẩn của repository.

Workflow **Development formatting** kiểm `npm run format:check` khi source/rule format thay đổi, chỉ đọc source và không tự commit. Job này tách khỏi Production Docker, không chạm DB và không khiến thay đổi docs/test/cấu hình IDE tự build/deploy Render; build filters production giữ nguyên.
