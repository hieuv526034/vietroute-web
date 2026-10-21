# Làm nhóm và ghi nhận đóng góp

## 1. Tác giả commit và tài khoản đăng nhập khác nhau

Git lưu Author (tác giả) và Committer (người tạo commit). GitHub liên kết commit với tài khoản chủ yếu qua email tác giả. Tài khoản dùng để push chỉ xác thực quyền truy cập, không tự biến mọi commit thành của người push.

Mỗi người thêm và xác minh email của mình trong GitHub → Settings → Emails. Nếu dùng chế độ ẩn email, lấy đúng địa chỉ `noreply` từ trang Settings của chính tài khoản đó; không tự đoán địa chỉ.

Các định danh như `bangpham.it` trong phân công được lấy từ email người dùng cung cấp; không mặc định đó là username GitHub. Khi mời người hoặc assign Issue/PR, dùng đúng username tài khoản đã xác nhận.

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
git switch -c feat/tracking-trip-progress
# Code và kiểm tra chức năng của mình.
.\mvnw.cmd verify
git status
git add src/main/java/vn/vietroute/business/tracking src/main/webapp/WEB-INF/views/tracking
git commit -m "feat(tracking): add trip progress page"
git push -u origin feat/tracking-trip-progress
```

Lệnh `git add` trên minh họa các thư mục sẽ được tạo khi có chức năng; chỉ thêm đường dẫn đã tồn tại và đúng thay đổi của mình. CSS/JS/SQL/tài liệu liên quan cũng được thêm rõ ràng. Không thêm `target/`, mật khẩu hoặc file IDE.

Sau đó mở Pull request vào `main`, mô tả Issue, chức năng, cách kiểm tra và hình ảnh nếu có giao diện. Một thành viên khác review. Chỉ merge khi build và luồng liên quan chạy được. Nhánh nên gắn với chức năng nhỏ, không giữ một nhánh khổng lồ suốt 2 tuần.

Nếu `git pull --ff-only` báo lịch sử khác nhau, dừng và đọc tình trạng; không dùng force push hoặc reset để bỏ thay đổi chưa hiểu. Trước khi bắt đầu chức năng mới, lấy `main` mới nhất.

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
