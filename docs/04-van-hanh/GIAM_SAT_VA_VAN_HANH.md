# Vận hành và chẩn đoán VietRoute

## Phạm vi hiện hành

Giữ `/health` liveness không query DB và [nhật ký API](NHAT_KY_API_VA_DEBUG.md). Đã bỏ Prometheus/metrics, thống kê CPU/RAM/online/activity, probe DB, job/mail/media/quota/Redis và các màn/API vận hành liên quan. Không còn collector/dashboard/rule alert mẫu trong repo; không cần token/provider tương ứng. Nhật ký vẫn có failedWrites và diagnostic SQLState để debug.

## Tích hợp dev và phát hành

Nhánh thành viên → PR dev sau checks. Chỉ có lệnh phát hành mới của chủ dự án mới được dev → main; không deploy trực tiếp. Dev CI chạy Java/Node/docs, release CI kiểm PostgreSQL/Docker/auth/pooler/recovery. Thay `.env` hoặc SQL không tự chứng minh Render đã nhận cấu hình/source.

## Nâng cấp database và khôi phục

Trước thay baseline phải dừng mọi node, backup và kiểm phục hồi cô lập. Công cụ rebaseline chỉ đổi history nếu cấu trúc khớp chính xác; không xóa tài khoản/dữ liệu để ép chạy. Có 23 bảng ứng dụng + history. Giữ backup được bảo vệ ngoài Git/image/log; xem [quy trình database](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#database-đã-có-dữ-liệu).

DB/JWT/quyền lỗi vẫn fail closed. Nhật ký cùng DB có thể không ghi được lúc DB lỗi; dùng Tomcat/Render Logs với traceId/SQLState đã lọc, không mở bypass auth. Lỗi history auth phải rollback mutation. Khi khôi phục cần kiểm table/sequence/FK/hash, đăng nhập và scope; không suy thành công chỉ từ health UP.

## Chẩn đoán đọc dữ liệu

Log mẫu event=http_completed bổ sung diagnostic và sqlState đã lọc từ trạng thái request. Khi journal DB không ghi được, mã trace trên log mẫu vẫn giúp xác định nhóm DB; log tối đa một dòng/giây/node như trước nên không hứa có log cho mọi request. Không ghi SQL/message driver/payload/email/credential hoặc dùng trace làm metric label. Dòng không có journal state ghi UNAVAILABLE; SQLState không có thì để trống, không suy luận DB quá tải chỉ từ 503.

Pool journal vẫn tối đa 1 connection/node, acquire tối đa 250ms. Validation connection dùng 1 giây như pool nghiệp vụ, tách khỏi thời gian chờ slot, tránh loại connection vì RTT mạng vượt 250ms. Socket/connect vẫn 2 giây, query vẫn 1 giây, backoff/missed-write và không queue RAM giữ nguyên. Cold start hoặc DB chậm vẫn có thể mất lượt ghi; không tăng pool/tắt nhật ký để che lỗi và không tự replay mutation.

## Phân biệt độ trễ giao diện và hạ tầng

Khi chuyển trang chậm, đo riêng DNS/connect, thời gian nhận byte đầu và tổng tải bằng vài GET public tuần tự (`/health`, `/login`, asset CSS), rồi kiểm layout/client bằng fixture loopback. Không dùng build/test routine để ghi production hoặc bật probe giữ DB thức. `/health` không query DB, CSS không cần dựng giao diện: nếu các request này chưa nhận byte đầu thì không thể quy toàn bộ thời gian chờ cho JavaScript hay SQL nghiệp vụ. TTFB chưa có khi timeout không phải TTFB bằng 0; phân biệt lỗi client timeout với HTTP 5xx.

[Render Free](https://render.com/docs/free#spinning-down-on-idle) có cơ chế ngủ sau 15 phút không nhận traffic và cần thời gian bật lại. DB hiện hành là PostgreSQL hieuboy trên máy chủ chủ dự án, không áp dụng mô tả scale-to-zero của Neon cũ để giải thích độ trễ hiện tại. Cần log runtime và đo DB trên cùng request để tách mạng/Render/app/pool/PostgreSQL; không tự bật polling giữ dịch vụ thức, nâng gói hoặc sửa guard/cache authority để làm giao diện có vẻ nhanh. Bằng chứng lượt đo và giới hạn hiện tại ghi ở tiến độ/PR.

## Đo đăng nhập và diễn tập khôi phục

Luồng web là GET CSRF → POST login → GET document dashboard/account. Đo riêng từng HTTP request và việc dựng trang; Server-Timing auth đo service xác thực, không gồm filter trước controller, journal sau controller hoặc mạng. Không log username/password/SQL hoặc suy tốc độ từ số query. Giữ transaction/khóa đăng nhập sai/BCrypt và kiểm quyền DB ở lần tiếp theo.

[verify-recovery.sh](../../.github/scripts/verify-recovery.sh) chỉ chạy trong GitHub Actions với PostgreSQL/container test riêng. Script dừng node test để snapshot ổn định, dump custom và restore sang database mới; đối chiếu digest dữ liệu, sequence và constraint qua catalog tham chiếu phục hồi. Sau đó kiểm tám history baseline, khởi động app phục hồi và thử CSRF/login/me/page/API. Không dùng so sánh chuỗi deparse khác parser hoặc bỏ biểu thức bằng regex để ép khớp. Dump/manifest chỉ ở môi trường CI tạm, không upload artifact hoặc in giá trị riêng tư.

Drill không tạo lịch backup, PITR, kho mã hóa, global PostgreSQL roles hoặc bảo đảm RPO/RTO cho DB production. Backup/rebaseline thực tế chỉ được ghi hoàn thành khi có bằng chứng trong [tiến độ](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md).

## Dữ liệu và kiểm tải cô lập

BusinessIntegrationTest giữ fixture dữ liệu đại diện, kiểm plan/index, giới hạn số query, scope/owner, race giữ/trả chỗ và rollback. Có ca hủy 5.001 đơn theo tập và đối chiếu event/version/chỗ; fixture này không phải dữ liệu khởi tạo người dùng hoặc workload production. [verify-business-load.sh](../../.github/scripts/verify-business-load.sh) hiện kiểm report JUnit/PostgreSQL qua gate business-load; không khởi động bộ sampler metrics hoặc quota đã bỏ.

Kết quả local/CI không chứng minh capacity, p95 hay SLA production. Muốn nghiệm thu tải HTTP nhiều node cần môi trường staging, workload và phép đo mới theo source hiện hành; không dùng số tải/metrics của phiên bản trước làm kết quả hiện tại. Không load test hoặc seed DB production để đo, không giảm BCrypt/CSRF/guard/journal để có số đẹp.
