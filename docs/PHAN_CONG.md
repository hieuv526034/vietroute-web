# Phân công VietRoute — nền tảng và nghiệp vụ

Cập nhật ngày 22/10/2026 theo username GitHub và email do chủ dự án cung cấp.

Đọc [bảng tiến độ và kế hoạch nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md) để xem phần đã có trên main, phần chỉ có API/chưa có UI, công việc tiếp theo của từng người và điều kiện bàn giao. Tài liệu này giữ phạm vi phụ trách; bảng tiến độ mới là nơi cập nhật trạng thái cùng bằng chứng PR.

## Thông tin thành viên

| Username GitHub | Email tác giả commit |
|---|---|
| `anhhoang104` | `hanh104204@gmail.com` |
| `BangPhamIT` | `bangpham.it@gmail.com` |
| `hiencoi250404-design` | `hiencoi250404@gmail.com` |
| `hieuv526034` | `hieuv526034@gmail.com` |

Dùng đúng bảng này khi tham chiếu thành viên, chuẩn bị phân công/PR và hướng dẫn cấu hình Git. Email cần được chính thành viên thêm/xác minh trong tài khoản GitHub để liên kết commit. Không suy diễn email từ username hoặc tên nhánh; không đổi tác giả các commit cũ theo bảng này.

## Phần nền tảng đã có trên main

**hieuv526034 <hieuv526034@gmail.com>** đã triển khai source base, cấu hình/build/Docker/Render Blueprint, database/migration/audit, common/locale/error/validation, JWT/auth/bootstrap, quyền theo hành động, nền transaction/worker/mail/media và giám sát. Mốc đối chiếu là `b1f3cde` sau PR #13. Đây là code nền tảng có trên main, không phân bổ lại tác giả commit cho người khác; không tự coi Render/provider/cảnh báo production đã được kết nối.

Các module nghiệp vụ bên dưới là kế hoạch triển khai tiếp, chưa phải các chức năng đã hoàn thành. Catalog có API CRUD và UI xem/tạo; UI sửa/xóa/phân trang còn ở CAT02. Thư viện ảnh public có API/UI nhưng cần credentials để thử Cloudinary thật. Đọc `QUY_UOC_PHAT_TRIEN.md` và `API_BASE.md` trước khi bắt đầu; dùng catalog làm ví dụ controller → service → repository → entity/response.

## Chia theo độ khó

| Người phụ trách | Mức độ | Phạm vi chính | Phối hợp |
|---|---|---|---|
| hieuv526034 | Khó nhất, nền tảng/tích hợp | Common, auth, CI/CD, schema và chuẩn SQL; khóa/tồn chỗ theo chặng và xử lý cạnh tranh; hủy chuyến xuyên module; tracking/Geolocation/Google Maps; tích hợp và nghiệm thu | Đưa hợp đồng transaction và quyền cho Bang; ghép giao diện từ cả nhóm |
| BangPhamIT | Gần khó | Vòng đời chuyến: phân xe/tài xế, kiểm tra lịch chồng, snapshot; vòng đời booking WEB/PHONE, xác nhận/từ chối/hủy, chặn thao tác lặp | Dùng cơ chế khóa/tồn chỗ do Hieu cung cấp, không tự làm phiên bản tồn chỗ khác |
| anhhoang104 | Dễ hơn, CRUD có phạm vi | Hoàn thiện danh mục; nhà xe, loại xe/xe, thông tin tài xế và form quản trị; validation nhập liệu, phân trang và trạng thái | Tài khoản/quyền dùng auth chung; không sửa mật khẩu bằng CRUD entity |
| hiencoi250404-design | Dễ hơn, danh mục và giao diện tra cứu | Địa điểm, tuyến một chiều, điểm dừng theo thứ tự và tên vi/en; trang tìm chuyến, chi tiết hiển thị và lịch sử đơn khách | Service tìm/tồn chỗ/đặt vé lấy từ Bang/Hieu, không tự tính lại ở JavaScript |

Tuyến đi và về là hai bản ghi riêng. Mọi người viết UI Thymeleaf, API, request/response, SQL và kiểm thử liên quan cho phần mình. Phạm vi nhà xe lấy từ principal/server; không tin companyId do client gửi. Không để hai người cùng sửa một migration đã áp dụng.

## Nhánh theo người phụ trách

| Người phụ trách | Nhánh chuẩn bị | Phần việc |
|---|---|---|
| hieuv526034 | `hieuv526034/application-foundation` | Base/common/auth/schema/CI và tích hợp nền tảng |
| BangPhamIT | `bangpham.it/trip-booking` | TRIP01 và BOOKING01, theo thứ tự phụ thuộc |
| anhhoang104 | `anhhoang104/carrier-catalog` | CAT01/CARRIER01: danh mục, nhà xe, xe, tài xế |
| hiencoi250404-design | `hiencoi250404-design/route-search-ui` | ROUTE01/SEARCH01: tuyến, điểm dừng và giao diện tra cứu |

Nhánh có sẵn `bangpham.it/trip-booking` thuộc `BangPhamIT`; `bangpham.it` là nhãn nhánh cũ, không phải username GitHub. Nhánh mới của Bang dùng tiền tố `BangPhamIT/`; các thành viên còn lại dùng username trong bảng thông tin. Ba nhánh nghiệp vụ được chuẩn bị từ cùng commit nền tảng đã kiểm tra, chưa thể hiện thành viên đã viết code. Nhánh `hieuv526034/neon-render-cicd` giữ lịch sử phần CI đã merge.

Mọi thay đổi đi từ nhánh phụ trách → PR → CI/review → merge commit vào `main`. Không dùng nhánh `codex/`, không commit source trực tiếp lên `main`. Sau khi base được merge, cập nhật `main` vào nhánh chuẩn bị trước khi code. Với mục nhỏ tiếp theo, tạo nhánh mới từ `main` mới nhất theo mẫu `<tiền-tố-thành-viên>/<mã-việc>-<mô-tả>` rồi tạo PR riêng; không dồn toàn bộ nghiệp vụ vào một PR lớn.

```powershell
git fetch origin
git switch bangpham.it/trip-booking
git merge origin/main
# Chỉ bắt đầu phần việc khi các API/schema phụ thuộc đã sẵn sàng.
```

## Thứ tự làm từng mục

| Giai đoạn | Mã việc | Chủ trì | Điều kiện nghiệm thu |
|---|---|---|---|
| 1 | BASE01 — common/locale/error/audit/Swagger | Hieu | vi/en cùng mã lỗi, validation field có key, schema đầy đủ, build production bỏ test |
| 1 | AUTH01 — JWT/bootstrap | Hieu | Deploy seed admin một lần; login/me/đổi mật khẩu/logout; CSRF; khóa tạm; thu hồi token cũ |
| 1 | CAT01 — danh mục mẫu | Hieu khởi tạo, Anh hoàn thiện | Seed lặp không trùng; API vi/en, mã hợp lệ, ADMIN tạo được; người khác bị chặn |
| 2 | CARRIER01 — nhà xe/xe/tài xế | Anh | CRUD đúng tenant, mã/biển số/sức chứa hợp lệ, trạng thái và phân trang |
| 2 | ROUTE01 — địa điểm/tuyến/điểm dừng | Hien | Hai chiều riêng, thứ tự/giờ/giá hợp lệ; tên vi/en; không đảo dữ liệu lịch sử |
| 3 | TRIP01 — tạo chuyến/phân công/snapshot | Bang | Qua ngày đúng; chặn xe/tài xế trùng lịch; dữ liệu chuyến độc lập mẫu tuyến |
| 3 | CAPACITY01 — transaction chỗ theo chặng | Hieu | Khóa thống nhất, không bán vượt chỗ khi xác nhận đồng thời, rollback toàn bộ khi lỗi |
| 4 | BOOKING01 — yêu cầu/xác nhận/hủy WEB/PHONE | Bang | Luồng trạng thái đúng, chống thao tác lặp, ghi lịch sử; gọi CAPACITY01 trong cùng transaction |
| 4 | SEARCH01 — tìm/chi tiết/lịch sử khách | Hien | Gọi API chung, hiển thị trạng thái/tồn chỗ thật, responsive và vi/en |
| 5 | TRACK01 — bản đồ/vị trí/tiến độ | Hieu | Quyền chuyến được giao, hiển thị timestamp/stale; xử lý từ chối vị trí/mất mạng; không giả lập GPS thật |
| 5 | INTEGRATION01 — hủy chuyến/tổng hợp | Hieu + Bang | Không lọt booking sau hủy; audit/khóa/rollback nhất quán; nghiệm thu theo đặc tả |

Trước giai đoạn 2 phải chốt SCOPE01 (membership/scope nhà xe) và hợp đồng AUTH03 (cấp tài khoản). AUTH02 đăng ký CUSTOMER, CAT02 hoàn thiện UI catalog và LOCATION01 được tách thành các việc rõ ràng trong bảng tiến độ. DRIVERUI01, DETAIL01/HISTORY01, REPORT01 và ACCEPT01 cũng có người phối hợp/đầu ra tại đó, tránh bỏ sót luồng F01–F19.

Không làm tất cả cùng lúc. Chốt schema và hợp đồng trước mỗi giai đoạn; chức năng tiếp theo chỉ bắt đầu khi phần phụ thuộc đã có API dùng được. Bảng này là kế hoạch, chưa tạo Issue/assign trên GitHub. Backlog UI quản lý job/quyền/cấu hình, triển khai provider/cảnh báo/backup được ghi riêng, không đánh dấu xong bằng API hoặc mẫu cấu hình.

## Quy tắc bàn giao và commit

- Tên module: `business/<module>/{controller,dto/request,dto/response,entity,repository,service}`; auth và common là hai phần riêng.
- Mỗi task có PR nhỏ, API/Swagger, hướng dẫn UI, migration mới nếu cần, ca thành công và lỗi thực tế. Comment/Javadoc tiếng Việt giải thích luồng và transaction.
- Phần nền tảng hiện tại ghi dưới định danh hieuv526034 đã cấu hình. Khi thành viên tự triển khai, họ commit bằng tài khoản/email của mình; bảng phân công không phải căn cứ tạo đóng góp giả.
- Hieu review transaction/schema/security/Maps; Bang review các hợp đồng booking/chuyến; Anh và Hien review UI/validation giao tiếp trực tiếp.
- Báo cáo/slide: mỗi người cung cấp ảnh, luồng, SQL và ca kiểm thử của phần mình; Hieu tổng hợp nội dung tích hợp, không ghi mọi nghiệp vụ thành đóng góp cá nhân của người tổng hợp.
