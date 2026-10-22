# Tạo repository GitHub chung

## 1. Chủ repository tạo repo

Trên GitHub tạo repository tên **vietroute-web**, mô tả: “Website quản lý nhà xe và đặt chỗ xe khách liên tỉnh bằng Spring MVC, Thymeleaf và JDBC thuần”.

Chọn Public/Private theo yêu cầu môn học. Vì source local đã có README và cấu hình Git, **không chọn tạo thêm README, .gitignore hoặc license trên GitHub** ở bước tạo repo này; giữ repo remote trống để push lịch sử local dễ dàng.

Không cần chia thành bốn repo. Một repository chung và các nhánh chức năng là đủ.

## 2. Mời 3 thành viên còn lại

Repository → Settings → Collaborators → Add people. Dùng username GitHub thật của từng người hoặc email có thể mời được; họ phải nhận lời mời. Người tạo repo đã là owner, không cần mời lại chính mình.

Username GitHub do chủ dự án cung cấp: `anhhoang104`, `BangPhamIT`, `hiencoi250404-design`, `hieuv526034` (owner). Email tương ứng được lưu tại [Thông tin thành viên](PHAN_CONG.md#thông-tin-thành-viên); dùng bảng này để tránh lấy nhầm email từ username.

Mời vào repo cấp quyền cộng tác; chưa tạo commit hoặc ghi nhận một chức năng đã hoàn thành cho người đó. Không cần và không chia sẻ mật khẩu GitHub giữa các thành viên.

## 3. Kết nối source local với repo remote trống

Đứng ở thư mục chứa `pom.xml`. Thay URL bên dưới bằng URL thật do GitHub cung cấp; OWNER là username/tổ chức sở hữu repo, không phải email.

```powershell
git status
git log -1 --format="%h | %an <%ae> | %s"
git remote add origin https://github.com/OWNER/vietroute-web.git
git push -u origin main
```

Khung source được chuẩn bị với nhánh `main`. Nếu chưa có commit đầu, chỉ commit sau khi kiểm tra `git status` và cấu hình Author đúng; xem CONTRIBUTING.md. Không tạo lại initial commit nếu đã có.

Nếu `origin` đã tồn tại, dùng `git remote -v` kiểm tra trước, không tự thêm lần nữa hoặc đổi URL khi chưa biết remote cũ là gì. Nếu remote không trống, đọc trạng thái và tích hợp có chủ đích, không force push để đè lịch sử.

Push dùng phiên đăng nhập GitHub của chủ repo qua Git Credential Manager/SSH/PAT phù hợp; không nhập mật khẩu tài khoản trực tiếp thay PAT và không đưa token vào URL hoặc tài liệu.

## 4. Các thành viên clone và cấu hình email của mình

```powershell
git clone https://github.com/OWNER/vietroute-web.git
cd vietroute-web
git config --local user.name "TEN_HIEN_THI_CUA_BAN"
git config --local user.email "EMAIL_CUA_CHINH_BAN"
```

Xác minh email đó trong GitHub Settings → Emails, rồi làm theo CONTRIBUTING.md. Không copy cấu hình Author của người tạo source sang ba máy còn lại.

## 5. Thiết lập nhóm sau khi push

- Dùng `main` làm default branch.
- Tạo Issue theo docs/PHAN_CONG.md và assign đúng username đã nhận lời mời.
- Dùng PR vào main; ưu tiên Create a merge commit để giữ lịch sử commit gốc dễ theo dõi.
- Nếu gói GitHub và loại repo cho phép ruleset/branch protection, chặn force push và yêu cầu review/build thành công. Dù không có tính năng này, nhóm vẫn có thể thống nhất quy trình PR.
- Workflow build có sẵn trong `.github/workflows/build.yml`; GitHub Actions có thể cần bật/được cho phép theo thiết lập tài khoản hoặc tổ chức.
- Username/email đã có trong `docs/PHAN_CONG.md`; chưa cấu hình CODEOWNERS tự động. Khi thiết lập quyền review, dùng đúng username và kiểm tra thành viên đã có quyền cộng tác.

## Nguồn

- Mời cộng tác: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository
- Ghi nhận commit: https://docs.github.com/en/account-and-profile/reference/profile-contributions-reference
