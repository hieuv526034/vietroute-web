# Audit và định danh system

Đọc sau [luồng base](LUONG_XU_LY_BASE.md), trước khi viết repository hoặc job mới. `created_by` và `updated_by` là **BIGINT khóa ngoại tới auth_users**, không chuyển thành chuỗi. Giá trị **0 tham chiếu username `system`**; không còn dùng NULL để biểu diễn tác vụ hệ thống. Java dùng `ApplicationConstants.SystemActor.ID/USERNAME`, thời gian dùng `Instant`/`TIMESTAMPTZ`.

## Chọn người thực hiện

| Luồng | created_by | updated_by |
|---|---|---|
| Bootstrap admin, registry role/quyền, danh mục mẫu | system | system |
| Tạo/sửa danh mục, upload ảnh | ID từ principal đã xác thực | ID người thực hiện |
| Admin gán role/quyền | Mapping mới ghi ID admin | ID admin; role/user đích cũng cập nhật audit/version |
| Tạo job có người yêu cầu | ID người yêu cầu | Cùng người yêu cầu lúc tạo |
| Tạo job tự động, không có người yêu cầu | system | system |
| Worker claim/ACK/NACK/hết lease, dọn ảnh | Giữ người tạo | system |
| Retry job thủ công | Giữ người tạo | ID principal người retry; worker có thể cập nhật tiếp ngay sau đó |
| Bộ đếm đăng nhập sai/khóa tạm/reset đếm | Giữ người tạo | system vì đây là quyết định tự động của auth |
| Đổi mật khẩu/logout | Giữ người tạo | ID tài khoản đã xác thực |
| Nhật ký API | system | system; không thu thêm danh tính người gọi vào log |

Không nhận audit từ request, header hay role do client gửi. Luồng có người dùng phải truyền actor tường minh vào repository; không dùng `system` để che việc thiếu principal. Chỉ `JobRepository.enqueueAt` chấp nhận actor null cho lịch/tác vụ nội bộ và đổi thành system trước khi bind JDBC. Default SQL là lớp bảo vệ cho seed/insert tự động, không thay kiểm quyền hoặc scope.

Sau khi gán lại tập quyền/role, mapping cũ được xóa và tạo mới trong transaction; audit mapping phản ánh lần gán hiện tại. Đây không phải lịch sử thay đổi bất biến. Nếu cần đối soát ai đã thu hồi quyền nào phải thiết kế audit event riêng; nhật ký API cũng không thay thế audit đó.

## Vì sao system nằm trong auth_users?

Base hiện dùng khóa ngoại số đến bảng tài khoản. Một dòng kỹ thuật giữ quan hệ tham chiếu hợp lệ và giúp JOIN ra tên `system`, không cần đổi kiểu dữ liệu/hợp đồng API. ID 0 được chèn tường minh, không tăng sequence; trên DB mới admin vẫn nhận ID 1. Không suy đoán ID admin khi xử lý nghiệp vụ, luôn lấy principal hoặc truy vấn username.

System có `active=false`, không có role và hash đánh dấu vô hiệu `!SYSTEM_DISABLED` (không phải mật khẩu). DB CHECK chặn bật active, đổi tên/hash và gán role cho ID 0. Auth không cho đăng nhập, không tăng bộ đếm từ request vào system; JwtService từ chối cấp token và filter từ chối subject này. Service gán role chặn system trước khi mở transaction. Danh sách tìm người để gán role loại dòng kỹ thuật này. Không dùng username system làm admin bootstrap.

API audit vẫn trả `createdBy: 0`/`updatedBy: 0`, Swagger mô tả ý nghĩa. Modal chi tiết job hiển thị `system` cho giá trị 0; những ID còn lại giữ nguyên. Không thêm username/email người gọi vào projection nhật ký chỉ để hiển thị audit.

## Migration V006 và nâng cấp dữ liệu cũ

Initializer chạy V001–V006 cùng transaction có advisory lock khi `VIETROUTE_DB_INIT_ENABLED=true`. **Giữ nguyên V001–V005 và checksum**. V006 dùng search_path schema cấu hình, không chứa prefix schema cố định.

V006 tạo actor system, bổ sung audit còn thiếu trên registry/mapping quyền, `updated_by` của job và actor kỹ thuật của nhật ký; lấp riêng các giá trị NULL bằng system. Actor đã có, timestamp/version và mật khẩu cũ không bị ghi đè. Cột thời gian mới trên registry/mapping cũ nhận thời điểm nâng cấp vì trước đây chưa ghi nhận, không được diễn giải là thời điểm tạo lịch sử. Các cột audit actor có FK, DEFAULT 0 và NOT NULL; truyền NULL tường minh bị DB từ chối.

Nếu đã tồn tại username `system` hoặc ID 0 trước V006, migration **dừng và rollback** để tránh chiếm tài khoản cũ. Người phụ trách cần kiểm tra/xử lý xung đột trên môi trường đó rồi khởi động lại; không tự đổi mật khẩu hoặc gán lại quyền. Chạy nhiều node vẫn dùng khóa initializer chung và một dòng system. Bảng history migration là metadata hạ tầng, dùng version/checksum/installed_at, không biến thành entity nghiệp vụ.

Không chạy migration hoặc backfill lên Neon để xác minh build. Test Java dùng port giả; CI Docker dùng PostgreSQL tạm kiểm seed đồng thời, NOT NULL/FK/CHECK, người thật còn nguyên audit, legacy nâng cấp, custom schema và restart giữ mật khẩu. Source merge không tự xác nhận môi trường Render/IntelliJ đã chạy V006; môi trường thực nhận thay đổi khi deploy/restart với initializer bật.
