# Nhật ký API và xuất request để debug

## Sử dụng trên giao diện

Mở `/operations/api-journal`. Page và hai API GET `/api/v1/admin/system/api-journal`, GET `/api/v1/admin/system/api-journal/{id}` cần grant endpoint tương ứng từ DB. Không suy quyền từ tên ADMIN/SYSTEM_READ. Người thiếu page grant nhập URL trực tiếp bị từ chối; service cũng yêu cầu principal đã được duyệt đúng target.

Lọc user ID, traceId, endpoint template, method, HTTP status, mã lỗi, thời gian tối thiểu hoặc chỉ lỗi. Server phân trang; danh sách chỉ tải metadata, bấm một dòng mới lấy snapshot. User ID lấy từ JWT/password đã xác minh, username join auth_users theo ID; khách/record cũ chưa biết danh tính là null. Không tin X-User-ID hoặc userId client gửi trong body.

Thời điểm UTC và durationMs cho biết thời gian đến khi hoàn tất handler/security; chưa gồm chính thao tác ghi dòng journal. Ghi nhận cả lỗi trước controller như 401/403/413/503; route chưa xác định hiển thị UNMAPPED/PRE_CONTROLLER. Journal không tự ghi truy vấn của chính nó.

## Copy cURL và Postman

Mở chi tiết, chọn copy cURL hoặc xuất collection Postman. URL dùng origin/context ứng dụng hiện tại và replayPath đã lọc; chọn token/placeholder theo UI. Không đọc cookie auth hoặc tự gửi lại mutation. Tự điền token/body bị che, kiểm URL/ID/quyền trước chạy; snapshot phục vụ debug, không bảo đảm replay nguyên trạng.

Path variable chỉ giữ ID/UUID/mã theo whitelist; query giữ paging, locale, ID cha và enum hợp lệ. Query text/PII chưa được duyệt bị bỏ. Cookie, Authorization, header raw, mật khẩu và JWT không nằm trong bản xuất.

## Chính sách dữ liệu

Request/response chỉ qua danh sách DTO và field được duyệt trong ApiJournalService. Có projection cho catalog, đặt chỗ, nhà xe/xe/tài xế, địa điểm/tuyến/chuyến và báo cáo đã khai báo; class/field mới mặc định bị bỏ. Giữ ID nghiệp vụ, số lượng/giá, version, trạng thái và boolean; tên/chuỗi tự do được che hoặc bỏ. Không ghi liên hệ hành khách, email, điện thoại riêng, license, tọa độ GPS, hash/token/password hoặc raw HTTP.

Body auth/access và kiểu chưa hỗ trợ được đánh dấu BODY_OMITTED. Response giữ code/messageKey/locale và field lỗi; không ghi message chứa tham số cá nhân. Mỗi snapshot tối đa 4096 ký tự, trang tối đa ba item trong snapshot. Không lưu ảnh/blob/stack trace/SQL/param; chỉ SQLState chuẩn và nhóm diagnostic. Đây là log chẩn đoán, không audit bất biến hoặc kho dữ liệu để phát lại giao dịch.

## Kiến trúc và cấu hình

Mười file ở common.api_journal; SQL trong ApiJournalRepo, ApiJournalService gộp projection/store/query, state nội bộ trong filter; body advice dùng GlobalExceptionHandler, envelope dùng ApiResponses. Entity ApiJournal ánh xạ đủ 22 cột; JDBC ghi/commit trực tiếp, không queue lịch sử RAM.

VIETROUTE_API_JOURNAL_ENABLED mặc định true; CAPACITY=10000 và MINUTES=10080 (7 ngày). Node/restart cùng DB thấy bản ghi đã commit. API luôn ẩn record hết hạn; cleanup tối đa mỗi phút có request ghi thành công, xóa lô 500 hết hạn và 500 vượt mục tiêu, có advisory lock. Cùng lần bảo trì dọn auth_access_changes theo lô 200/7 ngày/mục tiêu 1000 ID; startup DB_INIT cũng dọn auth history. Không có worker; lúc không có traffic/startup, xóa vật lý chờ lần bảo trì tiếp theo.

Pool lazy riêng tối đa một connection/node, chờ pool 250ms, connect/socket 2 giây, query 1 giây. Pool nghiệp vụ mặc định bốn: tổng tối đa năm connection/node. Một slot ghi chờ tối đa 100ms; insert lỗi backoff 30 giây và tăng failedWrites, không retry mutation. Counter reset khi restart. Pool lạnh hoặc mạng DB xa có thể làm mất một số trace đầu trong lúc chờ kết nối/backoff; giao diện có failedWrites, không giữ lại payload để gửi bù. Body HTTP có thể tới client trước khi filter commit xong, vì vậy tra cứu ngay lập tức có thể chưa thấy bản ghi; tải lại sau khi request kết thúc. Log vận hành chỉ nêu event/traceId, không payload/credential/exception chưa lọc. Không cam kết đủ mọi trace khi DB/slot/process lỗi.

## Kiểm tra và nâng cấp

common/001_schema.sql tạo trực tiếp journal, foundation/002_references.sql nối audit FK. Startup migration chỉ chạy khi chủ động bật; routine tests dùng PostgreSQL/H2 cô lập. Kiểm quyền hai context WAR, redaction sentinel, validation/security trước handler, restart/DB persistence, backoff và cURL/Postman; source/test local không chứng minh đã deploy.
