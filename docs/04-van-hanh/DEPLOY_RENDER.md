# Triển khai VietRoute lên Render

## Luồng phát hành

Mỗi thành viên PR vào dev sau kiểm tra/review. Chỉ khi chủ dự án có lệnh phát hành mới mới mở/merge dev → main, chạy production CI và deploy. Render theo main + checksPass trong render.yaml; tài liệu này không cấp quyền phát hành. Không commit trực tiếp main hoặc bấm Manual Deploy để bỏ qua yêu cầu.

Dev CI kiểm Java/Node/format/rules/docs. Release CI kiểm Docker/PostgreSQL/auth/scope/transaction/pooler/recovery theo workflow; kết quả local không thay CI remote. Path filter production theo render.yaml và workflow phải cùng phạm vi; docs/test đơn lẻ không kích hoạt production build.

## Cấu hình service

Blueprint từ render.yaml: Docker, Tomcat 10.1, ROOT.war, PORT do Render cấp, health /health. Environment phải có DB URL JDBC/user/password/schema, JWT secret cố định, bootstrap credential và các cờ init cần thiết. .env gốc được phép theo dõi Git nhưng bị loại Docker context/WAR; Render không tự đọc file này. sync:false không tự đổi secret service đang tồn tại; quản trị viên cập nhật Environment khi cần.

Cookie production Secure; không đổi JWT secret mỗi deploy. Placeholder không hợp lệ trong production. Env có thể ưu tiên cấu hình cũ nên đối chiếu tên key với .env.example, không đưa giá trị vào log/docs. Không cần env cho GPS/mail/upload/worker/Redis/metrics.

## Database trước phát hành

Schema mặc định vietroute trong neondb, cấu hình bằng VIETROUTE_DB_SCHEMA. User DB phải có quyền cần thiết cho initializer. [Baseline hiện hành](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#baseline-theo-cụm-bảng) gồm tám SQL theo cụm, 24 bảng cuối cùng. Database mới chạy khi DB_INIT bật; database có history khác dừng trước seed và cần quy trình rebaseline ngoài runtime.

Trước rebaseline: dừng mọi node, backup và kiểm phục hồi riêng, chạy --check rồi mới --apply --confirm-stopped trên đúng đích đã cho phép. Công cụ đối chiếu cấu trúc trước khi đổi riêng history; không xóa dữ liệu hoặc mật khẩu. Không đổi history/seed Neon trong build/test CI. Sau bảo trì phải dùng source tương ứng; không khởi động lại WAR khác baseline.

## Build và kiểm tra

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -Pproduction package
docker build -t vietroute-web .
```

Production Docker chỉ copy src/main và dùng -Pproduction, bỏ cả compile/test fixture và dev launcher. Node/formatter là công cụ phát triển. Chạy container cần truyền env của đúng môi trường có chủ đích; không dùng Neon cho smoke test thông thường.

Sau deploy kiểm Events/Logs/health, login/me, một luồng có DB, thiếu quyền trực tiếp 403, scope/owner, nhật ký và đổi ngôn ngữ. /health không query DB nên UP không chứng minh dữ liệu/quyền/seed đã đúng. Ghi commit/CI/môi trường/kết quả vào tiến độ; chưa deploy thì không ghi Đã có trên main/Render.

## Kết nối và chẩn đoán

Pool mặc định tối đa bốn connection nghiệp vụ + một journal/node, không ping giữ DB thức. Journal ghi trực tiếp PostgreSQL, không queue RAM, có timeout/backoff và failed-write counter; có thể thiếu record khi DB/slot lỗi. Xem [vận hành](GIAM_SAT_VA_VAN_HANH.md) và [nhật ký](NHAT_KY_API_VA_DEBUG.md). Không sửa guard/cache authority để che lỗi DB; không tăng gói hoặc bật polling khi chưa được yêu cầu.
