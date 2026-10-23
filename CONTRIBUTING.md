# Làm nhóm và ghi nhận đóng góp

Người mới theo [lộ trình đọc tài liệu](docs/README.md) trước khi nhận việc. Thiết lập [Code Style/EditorConfig và nút Format trong IntelliJ](docs/FORMAT_INTELLIJ.md); mọi lượt sửa source phải chốt format/check trước commit.

Khi đổi máy hoặc quay lại task đang dở, theo [Bàn giao và tiếp tục](docs/BAN_GIAO_VA_TIEP_TUC.md). Ghi quyết định dùng lại, trạng thái và bằng chứng vào docs/PR; source chưa push hoặc cuộc chat trên máy cũ không phải bản bàn giao đầy đủ.

## 1. Tác giả commit và tài khoản đăng nhập khác nhau

Git lưu Author (tác giả) và Committer (người tạo commit). GitHub liên kết commit với tài khoản chủ yếu qua email tác giả. Tài khoản dùng để push chỉ xác thực quyền truy cập, không tự biến mọi commit thành của người push.

Mỗi người thêm và xác minh email của mình trong GitHub → Settings → Emails. Nếu dùng chế độ ẩn email, lấy đúng địa chỉ `noreply` từ trang Settings của chính tài khoản đó; không tự đoán địa chỉ.

Username GitHub và email của bốn thành viên được chủ dự án cung cấp ngày 22/10/2026 và lưu tại [Thông tin thành viên](docs/PHAN_CONG.md#thông-tin-thành-viên). Dùng đúng bảng này khi cấu hình Git hoặc tham chiếu thành viên trong Issue/PR. Tài khoản của Bang là `BangPhamIT`, email `bangpham.it@gmail.com`; tiền tố nhánh cũ `bangpham.it/` là nhãn đã có, không phải username.

## 2. Cấu hình một lần sau khi clone, trên máy từng người

Ví dụ cho Hieu, chỉ áp dụng trong repository hiện tại:

```powershell
git config --local user.name "hieuv526034"
git config --local user.email "hieuv526034@gmail.com"
git config --get user.name
git config --get user.email
```

Ba thành viên còn lại dùng tên hiển thị và email của chính mình theo bảng phân công. `user.name` là tên tác giả hiển thị, không bắt buộc trùng username GitHub. Không đổi lần lượt cấu hình trên một máy để tạo đóng góp giả cho người khác.

## 3. Quy trình cho từng chức năng

Ví dụ thành viên phụ trách tracking làm màn hình theo dõi:

```powershell
git switch main
git pull --ff-only origin main
git switch -c hieuv526034/tracking-trip-progress
# Code và kiểm tra chức năng của mình.
npm ci
npm run format
npm run format:check
.\mvnw.cmd verify
git status
git add src/main/java/vn/vietroute/business/tracking src/main/webapp/WEB-INF/views/tracking
git commit -m "Bổ sung trang theo dõi tiến độ chuyến đi (tracking)" -m "Hiển thị trạng thái chuyến và điểm dừng hiện tại để điều hành theo dõi tiến độ." -m "Kiểm tra: ghi đúng lệnh đã chạy, kết quả và luồng đã kiểm tra trước khi commit."
git push -u origin hieuv526034/tracking-trip-progress
```

Lệnh `git add` trên minh họa các thư mục sẽ được tạo khi có chức năng; chỉ thêm đường dẫn đã tồn tại và đúng thay đổi của mình. CSS/JS/SQL/tài liệu liên quan cũng được thêm rõ ràng. Không thêm `target/`, mật khẩu hoặc file IDE.

Sau đó mở Pull request vào `main`, mô tả Issue, chức năng, cách kiểm tra và hình ảnh nếu có giao diện. Một thành viên khác review. Chỉ merge khi build và luồng liên quan chạy được. Nhánh theo mẫu `<người-phụ-trách>/<chức-năng>`, dùng đúng tiền tố trong `docs/PHAN_CONG.md`, không dùng `codex/`. Không commit source trực tiếp lên `main`. Nhánh nên gắn với chức năng nhỏ, không giữ một nhánh khổng lồ suốt 2 tuần.

`npm ci` chỉ cần khi cài môi trường hoặc lockfile thay đổi. Format chạy ở máy phát triển; Docker/Render không cài npm. Giữ khoảng cách giữa field/method/khối nghiệp vụ; tên lớp dùng `DTO` viết hoa, các thời điểm Java dùng `Instant`, theo quy ước trong `docs/QUY_UOC_PHAT_TRIEN.md`.

Nếu `git pull --ff-only` báo lịch sử khác nhau, dừng và đọc tình trạng; không dùng force push hoặc reset để bỏ thay đổi chưa hiểu. Trước khi bắt đầu chức năng mới, lấy `main` mới nhất.

### Viết commit và PR bằng tiếng Việt

Từ các thay đổi mới, tiêu đề và nội dung commit dùng **tiếng Việt có dấu**, kể cả commit do công cụ hỗ trợ tạo và merge commit. Tiêu đề PR và mô tả PR cũng dùng tiếng Việt để cả nhóm dễ đọc và tra cứu. Giữ nguyên tên class, module, API, biến môi trường, mã lỗi và lệnh kỹ thuật khi cần tham chiếu.

Tiêu đề commit nêu hành động cụ thể và phần bị ảnh hưởng, chẳng hạn `Sửa kiểm tra quyền xóa danh mục (catalog)` hoặc `Cấu hình schema cơ sở dữ liệu từ biến môi trường (common)`. Tránh tiêu đề chỉ có `update`, `fix`, `sửa code` hoặc `hoàn thiện`. Nội dung bên dưới giải thích đủ để người không đọc hội thoại vẫn hiểu:

- **Thay đổi:** chức năng hoặc cấu hình nào được bổ sung/sửa, hành vi trước và sau nếu cần.
- **Lý do:** vấn đề cần giải quyết hoặc mục đích của thay đổi.
- **Kiểm tra:** lệnh và luồng thực sự đã kiểm tra, kết quả, phần chưa kiểm tra hoặc giới hạn còn lại nếu có.

Mẫu sau dùng khi soạn nội dung commit; thay các phần trong ngoặc vuông bằng thông tin thực tế:

```text
[Hành động cụ thể] [phần bị ảnh hưởng] ([module nếu cần])

Thay đổi:
- [Mô tả thay đổi và kết quả đối với người dùng hoặc hệ thống.]

Lý do:
- [Vấn đề hoặc yêu cầu dẫn đến thay đổi.]

Kiểm tra:
- [Lệnh/luồng đã chạy và kết quả thực tế.]
- [Giới hạn hoặc phần chưa kiểm tra, nếu có.]
```

Lệnh commit ở ví dụ tracking phía trên chỉ minh họa cách dùng nhiều tham số `-m` để tách các đoạn; phải thay phần kiểm tra bằng kết quả thực tế, không commit nguyên câu hướng dẫn. Với mô tả dài, soạn thông điệp vào file UTF-8 rồi dùng `git commit --file <đường-dẫn-file>`. Không ghi mật khẩu, token hoặc chuỗi kết nối có thông tin đăng nhập vào commit/PR. Không sửa lịch sử đã chia sẻ chỉ để đổi các thông điệp cũ sang tiếng Việt.

## 4. Merge để giữ nguyên tác giả

Nhóm ưu tiên **Create a merge commit** cho PR. Commit gốc giữ nguyên tác giả; người bấm merge không nhận thay toàn bộ công việc của người viết.

Squash/rebase có thể dùng khi cả nhóm hiểu rõ ảnh hưởng, nhưng không dùng để so sánh số commit thô giữa thành viên. Merge commit là lựa chọn dễ theo dõi nhất cho đồ án cần trình bày lịch sử đóng góp.

Commit trên nhánh chức năng đã có tác giả, nhưng để được tính vào một số thống kê Contributors/contribution graph, commit thường cần có mặt trên nhánh mặc định `main` và đáp ứng điều kiện email/tài khoản của GitHub. Dữ liệu thống kê có thể cập nhật chậm. Thành viên được mời vào repo mà chưa thực hiện công việc sẽ chưa tự có commit.

## 5. Ghi nhận theo phân công

Mỗi nhiệm vụ có một GitHub Issue với mã module, người thực hiện thực tế, điều kiện nghiệm thu và PR liên quan. Dùng tiền tố `common`, `auth`, `catalog`, `carrier`, `network`, `booking`, `tracking` trong Issue/nhánh/commit để truy vết, không dùng tiền tố để giả định tác giả. Đọc quy ước base và phân công mới trong `docs/` trước khi code.

- Issue thể hiện phần được giao.
- PR và diff thể hiện kết quả thực hiện.
- Commit thể hiện tác giả, email và lịch sử thay đổi.
- Review và ca nghiệm thu thể hiện đóng góp kiểm tra/tích hợp.
- Khi bảo vệ, mỗi người giải thích được nghiệp vụ, SQL, kiểm tra lỗi và giao diện phần mình.

Các lệnh kiểm tra tại repository:

```powershell
git log --all --format="%h | %an <%ae> | %s"
git shortlog -sne main
git log --author="hieuv526034@gmail.com" --oneline main
```

Số commit hoặc số dòng không đồng nghĩa khối lượng/chất lượng. Một commit đầu tiên tạo khung chung không phải bằng chứng một người đã hoàn thành mọi module.

## 6. Công việc thực sự làm chung

Nếu hai người cùng thực hiện một thay đổi, có thể dùng trailer `Co-authored-by: Tên <email đã liên kết GitHub>` trong body commit, cách phần mô tả bằng một dòng trống. Chỉ thêm người thực sự tham gia; không thêm cả nhóm vào mọi commit để làm đẹp thống kê.

Không dùng `git commit --author` để gán code của mình cho thành viên khác. Nếu đã lỡ commit sai email, kiểm tra trước khi sửa: commit chưa push có thể sửa cục bộ; lịch sử đã chia sẻ cần thống nhất với cả nhóm, không tự force push.

## Nguồn

- Email commit: https://docs.github.com/en/account-and-profile/how-tos/email-preferences/setting-your-commit-email-address
- Điều kiện ghi nhận contribution: https://docs.github.com/en/account-and-profile/reference/profile-contributions-reference
- Quy trình nhánh và PR: https://docs.github.com/en/get-started/using-github/github-flow
