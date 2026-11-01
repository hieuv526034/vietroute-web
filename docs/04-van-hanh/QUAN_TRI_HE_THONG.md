# Giao diện quản trị hệ thống

## Các màn hiện hành

Hướng dẫn theo source AUTHDB04/AUTHDTO02 ngày 01/11/2026. Không nhận màn đang có trên máy local là đã được phát hành lên Render; xem [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md). Quyền do DB gán theo **User → Role → Function → Menu/API**, không do tên role hoặc việc ẩn nút.

| URL | Dùng để làm gì | Điều kiện |
|---|---|---|
| /operations/accounts | Danh sách, tạo/sửa tên, khóa/mở/xóa mềm và xem quyền đích | Grant GET page và từng API riêng |
| /operations/access | Ba tab Role, Chức năng, Phạm vi dữ liệu | Grant GET page; API CRUD/gán/tra cứu tương ứng |
| /operations/access/resources | Xem inventory menu/API từ MVC | GET page + GET access/resources |
| /account | Hồ sơ, avatar và đổi password của chính mình | Đăng nhập hợp lệ; self-service |
| /dashboard | Tổng quan và liên kết theo capabilities | Đăng nhập, trạng thái được filter chấp nhận |
| /operations/jobs | Tra/retry công việc nền | GET page, API list và API retry độc lập |
| /operations/configuration | Cấu hình whitelist chỉ đọc | GET page và GET system/configuration |
| /operations/api-journal, /monitoring | Trace đã lọc và quan sát runtime | Grant page/API đúng đích, xem chuyên đề vận hành |

Trang nhiều chức năng dùng tab; thêm/sửa/gán dùng modal, lỗi giữ bản nháp trong modal, không tự replay mutation. Bảng phân trang server; lookup inventory nguồn MVC là danh sách đã thu thập tại node. Hướng dẫn chung tại [quy ước UI](../03-giao-dien/QUY_UOC_UI.md).

## Cấp quyền cho một nhân viên

1. Tab Chức năng: tạo Function, đặt tên vi/en và chọn đúng menu/API từ danh bạ. Modal đọc GET /api/v1/admin/access/targets; public/self-service chỉ để xem, không gán. Có menu mà thiếu API thì trang mở được nhưng dữ liệu/nút có thể bị chặn.
2. Tab Role: tạo nhóm quyền, sau đó Gán chức năng. Không gán trực tiếp URL hoặc permission/action cho Role nữa. Tập function rỗng thu hồi toàn bộ. Protected role bootstrap không sửa/xóa/gán qua API thường.
3. Trang User: tạo account với username/displayName/password/roles, email tùy chọn do người vận hành xác minh. Account mới phải đổi password khởi tạo trước khi có private access. Chỉ chọn Role không vượt trần quyền của người thao tác.
4. Với nhân viên nhà xe, thêm membership trong tab Phạm vi, chọn role là tập con global roles của chính user và nhà xe thực có. Role DRIVER không tự là hồ sơ tài xế; dùng luồng cấp/liên kết trong module nhà xe.
5. Đăng nhập bằng user đích và thử URL trực tiếp/API. Xem preview theo tài khoản đích, không dựa vào menu của admin hoặc tên role. Thay quyền có hiệu lực ở lần kiểm tiếp theo.

Ví dụ xem xe cần GET /operations/vehicles và GET /api/v1/admin/vehicles. Muốn xem chi tiết hoặc sửa cần thêm endpoint riêng và quyền scope tương ứng. Không tự chọn một bộ CRUD theo tên chức năng. Endpoint mới mặc định deny, muốn cấp phải gán qua Function/Role hoặc sync bootstrap chủ động.

## Quản lý tài khoản và phiên

User update chỉ sửa displayName/version. Khóa/mở qua endpoint state; xóa mềm thu hồi token/challenge/role/membership, giữ audit/ID/tham chiếu nghiệp vụ. Các thao tác bảo vệ system/self/bootstrap/account có protected role. Không sửa email, password hoặc audit tùy ý từ form CRUD.

Đổi mật khẩu tại /account kiểm password hiện tại, không trùng hiện tại/hai lần trước, thu hồi token và chuyển về login. Login/password sai 5 lần khóa 15 phút. Không có tự đăng ký, gửi xác minh email, forgot/reset hoặc nút khôi phục quản trị gốc trong bản này. Account cũ chưa xác minh cần người vận hành xử lý trước chuyển đổi, không bấm một link mail đã bỏ.

JWT 30 phút, không refresh token; cookie HttpOnly và CSRF. 401 có thể cần đăng nhập lại; 403 thiếu grant/thiết lập; 409 phải tải lại version và so bản nháp, không tự đổi version rồi gửi lại. Tên role ADMIN không tự cấp ngoại lệ trên runtime.

## Gán phạm vi và version

Phạm vi được tạo cùng nhà xe, không có nút tạo scope rời. Sửa tên vi/en/active của scope khác thay trạng thái kinh doanh nhà xe. Membership có active, tập roles và version; kể cả sau thu hồi hết role vẫn phải dùng mốc version hiện hành. Thu hồi global role loại bỏ scoped role tương ứng, history ghi trong cùng transaction.

all_scopes chỉ có hiệu lực cho target được chính role đó cấp; không ghép quyền của role/phạm vi khác. Từng nút canUpdate/canAssign được SQL kiểm bằng endpoint mutation. Chi tiết [scope và ownership](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#phạm-vi-dữ-liệu-và-ownership).

## Khởi tạo bootstrap và tài khoản mặc định

Admin bootstrap lấy username/email/password từ cấu hình, không đồng nhất mọi account tên ADMIN. DB_INIT có thể tạo/chuẩn hóa đúng identity gốc, giữ hash/history/khóa cũ; ACCESS_SYNC sửa role/function bootstrap với target nguồn hiện tại. Không có API recovery riêng. [Cờ và phạm vi init](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#khởi-tạo-và-bảo-vệ-bootstrap) là hướng dẫn chính, không dùng restart tùy tiện như thao tác reset password.

custom01/driver01/operator01 được tạo một lần khi DEFAULT_ACCOUNTS_ENABLED trong init và env hợp lệ. Account đã có giữ trạng thái/hash; ordinary grants và membership đã thu hồi không tự phục hồi sau restart. Dữ liệu nhà xe/xe/tuyến khởi tạo do dự án tạo theo yêu cầu, chưa xác minh là hoạt động thực của một đơn vị. Lịch chuyến/đơn và số dư hiện tại phải xem DB thực; không lấy số liệu fixture hoặc mốc triển khai cũ làm lời hứa môi trường mới.

## Job, cấu hình và nhật ký

Job có trạng thái/attempt/lease, retry là mutation cần grant riêng và CSRF; không sửa payload job từ UI. Retry có thể gọi provider nên kiểm provider/config trước. Trang cấu hình chỉ whitelist thông số, không đọc/ghi env/secret qua browser. `/health` không thử DB; readiness có DB; metrics scrape dùng token riêng.

AccessChange vẫn ghi lịch sử mutation nguyên tử nhưng không có tab/API tra history riêng. API journal là trace đã lọc trong common.monitoring, không thay history quyền hoặc DB audit. Đọc [nhật ký/debug](NHAT_KY_API_VA_DEBUG.md) và [giám sát](GIAM_SAT_VA_VAN_HANH.md) cho projection, retention, missed-write và giới hạn nhiều node. Config/template không chứng minh provider, backup hoặc cảnh báo production đã chạy.

## Cách phát triển và nghiệm thu tiếp

Đọc [schema/luồng auth](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [36 API auth](../02-phat-trien/API_BASE.md#api-auth-hiện-hành), [cấu trúc 40 class](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md#authdb04--auth-gọn-40-class-tám-bảng-01112026) và tiến độ đúng commit. Kiểm cả hai context WAR, URL trực tiếp, stale version, grant/revoke, scope/owner và rollback history. Không bật init hoặc ghi Neon để nghiệm thu build. Merge vào dev theo PR; main/deploy chỉ khi có lệnh phát hành mới.
