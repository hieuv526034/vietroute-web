# Audit và định danh system

Đọc cùng [luồng request](LUONG_XU_LY_BASE.md) và [schema](SCHEMA_VA_PHAN_QUYEN.md). Cột created_by/updated_by là BIGINT có khóa ngoại đến auth_users; timestamp nghiệp vụ dùng Instant/TIMESTAMPTZ. Actor hệ thống là ApplicationConstants.SystemActor.ID = 0, username system; không dùng NULL.

## Chọn người thực hiện

| Luồng | Người ghi audit |
|---|---|
| Bootstrap tài khoản, danh mục, dữ liệu nền hoặc bảo trì | system |
| CRUD, gán quyền, đặt chỗ, thu/hoàn, tiến độ chuyến | ID principal đã xác thực và được kiểm endpoint/scope/owner |
| Đăng nhập sai hoặc khóa tạm tự động | system; giữ người tạo |
| Đổi mật khẩu, logout | ID tài khoản đã xác thực; giữ người tạo |
| Nhật ký API | Actor kỹ thuật system; request_user_id là metadata riêng từ xác thực server |

Không nhận actor/audit/role/scope đáng tin từ request hoặc header. DEFAULT 0/NOT NULL/FK bảo vệ dữ liệu nhưng không thay kiểm principal hoặc quyền. Khi cập nhật giữ created_by/created_at, tăng version theo model và ghi updated_by/updated_at đúng transaction.

## Tài khoản kỹ thuật

foundation/001_system_account.sql tạo system sau PK/UNIQUE và trước FK liên cụm. Dòng này inactive, không có role, không đăng nhập và không nhận JWT. CHECK của DB và auth cùng bảo vệ identity này. Chèn ID 0 không lấy số từ sequence; không hardcode ID quản trị trong nghiệp vụ.

## Lịch sử và transaction

AccessChangeRepo chỉ ghi whitelist state trong cùng transaction mutation; lỗi history rollback mutation. Mapping hiện tại không thay cho history. Cleanup theo lô ở startup hoặc bảo trì gắn nhật ký, không có worker/RAM queue. Không có API/màn history. Nhật ký request cũng không phải audit bất biến; metadata userId/username theo [hợp đồng journal](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md), không ghi email/hash/token.

Baseline tạo trực tiếp các cột/default/FK hiện hành. Không có backfill tự động hoặc bộ SQL lịch sử trong runtime. Database sẵn có chỉ đổi history bằng công cụ bảo trì sau khi kiểm cấu trúc và backup; không thay actor, timestamp, version hoặc mật khẩu. Test dùng database cô lập.
