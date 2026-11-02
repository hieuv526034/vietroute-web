# Kịch bản demo và hỏi–đáp bảo vệ VietRoute

Học theo [phần từng thành viên](PHAN_CONG.md), [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md) và source được chỉ ở cuối mỗi nhóm câu hỏi. Câu trả lời dưới đây là mẫu nói ngắn; người trình bày phải chỉ ra được một màn hình, một method/query/test và giới hạn thật. Không học thuộc để nhận chức năng hoặc đóng góp chưa có.

## Kiểm tra trước buổi demo

1. Chốt đúng nhánh/commit/môi trường với [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md). Đối chiếu commit đang chạy trước khi khẳng định menu/select và Swagger private đã có trên Render.
2. Dùng DB demo riêng đã được phép; không thao tác thu–hoàn/khóa account/đổi grant/xóa/reset DB thật để thử. Nếu không có môi trường riêng thì chỉ trình bày đọc, muốn ghi phải có phạm vi được chủ dự án cho phép.
3. Chạy app, kiểm /health rồi login/me và API có DB. /health200 không chứng minh DB/quyền/seed tốt. Render Free có thể cold start hơn3 phút, chuẩn bị thời gian và phương án local.
4. Kiểm username admin/operator01/operator02/driver01/driver02/custom01 và key mật khẩu ở [local](../01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo). Không đưa mật khẩu/JWT lên slide, ảnh hoặc terminal đang chia sẻ.
5. Chọn tuyến/chuyến còn hợp lệ ngày demo, xe/tài xế không chồng lịch. Ngày seed neo lần tạo đầu, restart không dịch lịch; nếu hết cửa sổ thì tạo/mở chuyến mới, không reset seed.
6. Chuẩn bị chuyến khởi hành khoảng 10 phút tới để còn nhận đặt và START được trong cửa sổ30 phút sớm; chỉ trên DB demo. Ghi ID/code nhà xe/tuyến/chuyến/đơn vào ghi chú không chứa liên hệ cá nhân.
7. custom01 có thể đang có WEB active. Xử lý đơn đó hợp lệ trong DB demo trước khi tạo đơn mới; mỗi khách chỉ một WEB PENDING/CONFIRMED. Chuẩn bị đơn khác cho nhánh hủy/hoàn.
8. Dùng browser profile/phiên riêng theo tác nhân để tránh nhầm cookie. Mỗi người biết cách quay về luồng đọc khi lỗi, giữ traceId; không bấm lại mutation liên tục.

Lệnh kiểm source dành cho máy phát triển, không tự seed DB thật:

```powershell
npm ci
npm run format
npm run format:check
npm run check:rules
npm run check:docs
.\mvnw.cmd verify
node --test src/test/resources/dev/*.test.cjs
```

Maven mặc định có thể bỏ qua ca PostgreSQL có điều kiện; đọc số run/skipped. Bộ PostgreSQL dùng DB loopback tên vietroute_business_test, cấp user/URL test riêng theo BusinessIntegrationTest và workflow, không dùng root .env. Không gọi kết quả H2/CI là đã kiểm production/Oracle hoặc SLA.

## Kịch bản cả nhóm 15 phút

| Phút | Người trình bày | Thao tác và câu nói cần đạt |
|---|---|---|
| 0–2 | Hieu | “Đây là web đặt chỗ theo chặng; quyền endpoint từ DB và SQL giới hạn dữ liệu.” Nêu stack, tác nhân, scope và phần chưa có |
| 2–4 | Anh | Mở danh mục/địa điểm, xe/tài xế trong nhà xe. Chỉ quan hệ cha/ID/capacity và liên kết account, bàn giao cho Bang |
| 4–8 | Bang | Mở chuyến hợp lệ, khách tìm/đặt WEB PENDING; điều hành xác nhận, chỉ chỗ giảm trên chặng, ghi thu thủ công |
| 8–10 | Bang | Tài xế đúng assignment START → ARRIVE → DEPART điểm giữa → ARRIVE cuối → COMPLETE. Mốc server, không GPS |
| 10–12 | Hien | Cùng ngày/nhà xe đọc báo cáo, đối chiếu đơn; tách giá trị đặt/thu/hoàn và lượt chỗ |
| 12–14 | Hieu + mỗi chủ khối | Một ca403 hoặc404 ngoài scope và một409 version cũ trên DB demo. Journal đã che dữ liệu nếu có grant |
| 14–15 | Cả nhóm | Nêu test/bằng chứng đúng commit, giới hạn và hướng phát triển; trả lời ngắn bằng source |

Đối chiếu sau mỗi bước: PENDING chưa giữ chỗ → CONFIRMED giữ mọi đoạn → thu không quá total → hủy đủ điều kiện trả chỗ → hoàn riêng không vượt đã thu. Mốc ARRIVE đến điểm trả hoàn tất các đơn CONFIRMED phù hợp; START/DEPART có thể từ chối đơn PENDING đã lỡ điểm đón. Không chạy tất cả nhánh trên cùng một đơn rồi kỳ vọng quay lại trạng thái trước.

## Khi demo gặp lỗi

| Tình huống | Cách trình bày và xử lý |
|---|---|
| Không có chuyến | Nói rõ cửa sổ ngày/trạng thái/chiều/chỗ, chọn chuyến tương lai hợp lệ; không tự đổi clock/DB để giả dữ liệu |
| Account bị khóa | Dừng thử password; đổi sang account demo đã chuẩn bị hoặc nhờ đúng luồng quản trị, không bypass BCrypt/lock |
| Không mở menu/API | Kiểm đúng actor và page/API/scope; đây có thể là kết quả bảo mật mong đợi |
| 409 | Hai màn dùng version khác; tải lại để đối chiếu, không bỏ version hoặc gửi lại tự động |
| Render chậm | Giải thích cold start/mạng và chuyển sang local đã chuẩn bị, ghi đúng môi trường đang trình bày |
| Không thấy journal ngay | Request/log commit khác thời điểm, có retention/backoff/failedWrites; không hứa đủ mọi trace |
| Chức năng ở PR chưa có | Mở source/PR và nói “đang chờ tích hợp”, không mô tả là đã deploy |

## Cách trả lời

Mỗi câu theo ba nhịp: **kết luận → ví dụ → chỉ source**. Nói “em chưa triển khai/kiểm thật phần đó” khi phù hợp. Các nhóm Qxx để tra nhanh, không phải danh sách lời hứa sản phẩm.

## Tổng quan — Q01 đến Q06

### Q01. Dự án giải quyết việc gì, ai sử dụng?

Web chuẩn bị nhà xe/tuyến/chuyến, tiếp nhận đặt chỗ theo đoạn, xác nhận chỗ, ghi thu–hoàn và theo dõi mốc chuyến. Khách, điều hành, tài xế, quản trị có luồng khác nhau; báo cáo đọc cùng dữ liệu theo quyền. Phạm vi chưa có GPS, app mobile hoặc payment gateway.

### Q02. Vì sao dùng Spring MVC, có Spring Boot không?

Dự án dùng Spring MVC/Security và Thymeleaf, cấu hình RootConfig/WebConfig/SecurityConfig tường minh, đóng WAR cho Tomcat10.1; không Spring Boot. Local có launcher Tomcat nhúng để Run/Debug, production loại launcher và dùng WAR/image. Chỉ vào pom.xml và WebApplicationInitializer để chứng minh.

### Q03. Một request từ nút bấm xuống DB đi thế nào?

Browser → filter trace/journal/body guard → CSRF/JWT → EndpointAccess → Controller bind/validate → Service/transaction → Repo kiểm scope/owner và query/JPA → DTO/envelope. Ví dụ xác nhận booking: controller không sửa chỗ trực tiếp; BookingService phối hợp booking và segment trong transaction.

### Q04. Sao đã dùng JPA lại còn JDBC?

CRUD đơn giản dùng entity/JPA. Batch, SQL tổng hợp, giữ chỗ/khóa/CAS và query phức tạp dùng JDBC. Hai cách cùng JpaTransactionManager/connection qua proxy nên một thao tác không bị JPA commit mà JDBC rollback riêng. JDBC không đồng nghĩa bỏ entity hoặc nhét SQL vào service.

### Q05. OOP và phân lớp thể hiện ở đâu?

Entity mô tả dữ liệu/ràng buộc, Request/DTO là hợp đồng riêng; Service đóng gói quy tắc trạng thái/transaction; Repo che cách truy cập DB, controller điều phối HTTP. Kế thừa có mục đích ở BaseController/BaseDTO/JpaRepositorySupport; constructor injection giúp thay dependency trong test. Không tạo lớp rỗng để minh họa OOP; một Request/tài nguyên vẫn validation theo operation.

### Q06. Vì sao có 24 bảng, không giảm hết về vài bảng?

Auth 8, business 14, journal 1 và history 1. Các bảng tách quan hệ nhiều–nhiều, snapshot stop/segment, sự kiện tiền/trạng thái và dữ liệu nguồn có vòng đời khác nhau. Report/bootstrap không thêm bảng. Nêu một ví dụ: gộp trip_segments vào bookings sẽ khó giữ tồn theo đoạn và dùng lại chỗ trên đoạn không giao nhau.

Nguồn: [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md), [quy ước backend](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md), [pom.xml](../../pom.xml).

## Auth và dữ liệu được phép — Q07 đến Q18

### Q07. Đăng nhập username hay email, đăng ký ở đâu?

Chỉ username/password. Email là thông tin account, không fallback login. Bản hiện hành không tự đăng ký hoặc quên password qua email; quản trị tạo account được xác minh và yêu cầu đổi password, seed có ngoại lệ đã chốt. Không demo luồng email/reset đã bỏ.

### Q08. Password lưu thế nào, có giải mã được không?

DB giữ BCrypt hash với salt/cost12, không giữ password thuần. Login dùng matches, không “giải mã hash”. Có kiểm độ dài/độ phức tạp theo luồng và lịch sử2 hash trước khi đổi; ordinary seed có quy tắc riêng đã chốt, không phải mọi API đều cho password yếu. Không mở hash/.env trong lúc trình bày.

### Q09. JWT có mã hóa thông tin và có loại bỏ DB check không?

JWT được ký HS256 để kiểm tính toàn vẹn, không coi payload là bí mật được mã hóa. App kiểm issuer/audience/thời hạn và account/tokenVersion/grant trong DB. Token30 phút không có nghĩa quyền bị thu hồi vẫn dùng30 phút; lần kiểm tiếp theo đọc trạng thái mới. Không lưu JWT vào localStorage.

### Q10. Có JWT rồi sao còn CSRF?

Trình duyệt tự gửi cookie nên mutation qua cookie cần CSRF. Bearer được client gắn chủ động theo contract hiện hành, không dùng cookie tự gửi để xác thực, nên chain bỏ yêu cầu CSRF tương ứng. HTTPS/CORS/JWT không tự thay thế CSRF; không tắt CSRF để bấm Swagger cho dễ.

### Q11. Ẩn menu hoặc kiểm ROLE_ADMIN có đủ không?

Không. Menu và API có target riêng trong DB; người có page mà thiếu API có thể mở trang nhưng không đọc/ghi được. Backend kiểm URL trực tiếp, service và SQL kiểm phần dữ liệu. Tên ADMIN chỉ là cấu hình role; token tự khai role hoặc thay HTML không tạo grant.

### Q12. Scope, owner và tài xế được phân công khác nhau thế nào?

Scope giới hạn nhà xe, owner giới hạn đơn của khách, driver assignment giới hạn chuyến gắn đúng account tài xế. operator01 không được đọc ID Bắc Nam; custom01 không đọc đơn custom02; driver01 không được lấy chuyến driver02 chỉ vì biết ID. Thường404 khi ngoài dữ liệu được thấy,403 khi thiếu quyền endpoint.

### Q13. all_scopes của một role có nới quyền từ role khác không?

Không. Endpoint và all_scopes phải do cùng role cấp. Nếu roleA cấp endpoint X trong scope hẹp, roleB all_scopes nhưng không cấp X, không được ghép hai role để mở toàn bộ dữ liệu X. SQL visibleSql/bindVisible giữ ràng buộc này cả count/page/ID.

### Q14. Version để làm gì, khác tokenVersion thế nào?

Version chống cập nhật bằng snapshot cũ: hai người cùng đọc v3, người đầu ghi thành v4, người sau dùng v3 nhận409 và phải đối chiếu lại. tokenVersion kiểm phiên xác thực, không là version của một booking. Client không tự cộng version vì một mutation có thể đổi nhiều mốc liên quan.

### Q15. Logout hoặc thu hồi quyền có hiệu lực khi nào?

Logout tăng tokenVersion và invalidate challenge trong transaction, cookie được xóa; JWT cũ không qua lần kiểm tiếp theo. Thu hồi grant cũng được đọc lại từ DB ở request sau, không dựa authority cached trong token. Request đã qua guard và đang chạy không được coi là đã bị hủy giữa chừng.

### Q16. Vì sao lỗi password phải ném sau transaction?

Cần commit failed_login_attempts/locked_until rồi mới trả lỗi. Nếu ném lỗi trong callback làm rollback thì người tấn công có thể thử sai mà bộ đếm không tăng. UserService giữ kết quả lỗi, commit phần đếm rồi ném BusinessException;5 lần sai khóa 15 phút, không tự reset mật khẩu.

### Q17. Thêm endpoint mới có tự cấp cho mọi user không?

Không. Inventory thu thập method + route template từ MVC; target chưa gán default deny. Khi bật sync chủ động, bootstrap được gán tập private vào DB; ordinary role/function init một lần, không tự khôi phục grant đã thu hồi. Public/self-service chỉ source WebRoutePolicy quyết định, không phải input DB/client.

### Q18. Vì sao còn challenge/history khi đã bỏ email/reset?

Account lifecycle còn gọi invalidate challenge, history ghi thay đổi quyền/tài khoản trong mutation transaction. Không có API email/reset hoặc màn history. AccountChallengeRepo còn method legacy không caller, trong đó challenge() tham chiếu system_jobs đã bỏ; đó là phần cần dọn, không phải bằng chứng luồng mail đang chạy. Không tự sửa migration đã áp dụng chỉ để xóa một bảng.

Nguồn: [schema/quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [UserService](../../src/main/java/vn/vietroute/auth/service/UserService.java), [EndpointAccess](../../src/main/java/vn/vietroute/auth/security/EndpointAccess.java), UserRoleRepo và các test EndpointSecurityTest/DataScopeSecurityTest/AccountSecurityTest.

## Dữ liệu nguồn, tuyến và đặt chỗ — Q19 đến Q29

### Q19. Vì sao không trả entity thẳng ra API, inactive khác deleted ra sao?

Entity chứa trường kỹ thuật/PII/quyền mà API không nên công bố, nên DTO là projection riêng. Inactive thường ngừng sử dụng nhưng còn lịch sử/tham chiếu; deleted của auth là xóa mềm theo contract. Không áp một kiểu DELETE cho tất cả bảng. Lookup công khai địa điểm yêu cầu cả địa điểm và tỉnh active, quản trị có thể cần đọc bản ngừng dùng.

### Q20. Tuyến khác chuyến, vì sao snapshot?

Tuyến là mẫu một chiều gồm thứ tự điểm, phút đến/rời và giá cộng dồn; chiều về là tuyến khác. Chuyến là lần chạy có ngày/xe/tài xế và snapshot điểm/giá/lịch. Sửa tuyến mẫu không sửa lịch sử chuyến/đơn đã tạo. Khi mở chuyến vẫn kiểm dữ liệu nguồn còn hợp lệ, không ghi đè snapshot từ mẫu mới.

### Q21. Vì sao xe/tài xế không thể bị phân trùng lịch?

Service kiểm asset cùng nhà xe/scope, account tài xế và trạng thái, thời gian/sức chứa; repo kiểm khoảng lịch dưới cơ chế khóa tương ứng khi publish/phân công. Hai request cạnh tranh không được cùng dựa một snapshot rảnh không khóa. Demo bằng DB test và ca concurrent của BusinessIntegrationTest, không đặt hai chuyến thật để thử.

### Q22. Chống SQL injection và giới hạn dữ liệu ở đâu?

Dữ liệu bind PreparedStatement, identifier/sort/fragment chỉ whitelist phía server; SQL đặt trong Repo. Validation Request không thay bind parameter hoặc scope SQL. Count và page cùng bộ lọc, size tối đa 100; không lấy toàn bảng xuống JavaScript rồi lọc quyền. JSONB/array nhận dữ liệu bind, không ghép chuỗi SQL từ client.

### Q23. Tồn chỗ và giá theo chặng tính thế nào?

Tuyến A→B→C→D có capacity30. AB đã giữ 5, BC đã giữ 10, CD đã giữ 3 thì khách A→C thấy min(25,20)=20 chỗ. Xác nhận2 chỗ tăng reserved AB và BC thêm2, không thay CD. Nếu giá cộng dồn A=0,B=100.000,C=220.000 thì A→C là220.000/chỗ,2 chỗ440.000; server tính, client không tự gửi total đáng tin.

### Q24. PENDING có giữ chỗ chưa, hai người giành chỗ cuối thế nào?

PENDING chỉ là yêu cầu, chưa giữ chỗ nên nhiều yêu cầu có thể chờ. Lúc CONFIRMED, transaction khóa/kiểm lại sức chứa trên mọi segment và cập nhật nguyên tử. Nếu chỉ còn 1 chỗ cho hai yêu cầu, không được cả hai cùng thành công; bên không đủ nhận BOOKING_CAPACITY/409. Kết quả tìm kiếm không phải lời hứa chỗ đến lúc xác nhận.

### Q25. Vì sao phải có thứ tự khóa?

Trip → booking → segments là thứ tự chung để các luồng cập nhật không giữ khóa ngược nhau và tăng nguy cơ deadlock. Ngoài khóa còn kiểm state/version/CAS và đủ số đoạn. Chỉ nói “đã dùng transaction” chưa chứng minh không bán vượt chỗ; cần query khóa và ca concurrent thật.

### Q26. History ghi lỗi thì chỗ/tiền có bị cập nhật nửa chừng không?

Booking/segments/history cùng transaction, nên lỗi ghi sự kiện làm rollback phần cập nhật liên quan. Tương tự mutation quyền có AccessChange cùng transaction. Journal là log chẩn đoán riêng, không có cùng bảo đảm đó. Chỉ ca fault injection trên DB test; không phá trigger production để chứng minh rollback.

### Q27. WEB khác PHONE thế nào, sao khách chỉ có một đơn active?

WEB gắn owner là principal khách; PHONE do staff tạo trong scope, không nhận owner/giá/audit đáng tin từ client. Cả hai ban đầu PENDING. WEB giới hạn một đơn PENDING/CONFIRMED mỗi khách để tránh ôm nhiều yêu cầu; tạo lặp cùng requestKey/actor/nội dung trả đơn cũ, khác nội dung là xung đột. Không phải tất cả endpoint đều idempotent.

### Q28. Hủy đơn/hủy chuyến có tự hoàn tiền không?

Không. Hủy đủ điều kiện đổi trạng thái và trả chỗ nếu đơn CONFIRMED; còn kiểm điểm đón đã rời chưa. Hủy chuyến DRAFT/OPEN có lý do, xử lý nhóm đơn/chỗ/history theo tập. Tiền đã thu vẫn cần ghi hoàn thực tế riêng; báo cáo giữ khoản phải hoàn. Không gọi hủy đơn là tiền đã chuyển về khách.

### Q29. Bấm thu tiền hai lần hoặc dùng lại requestKey khác nội dung thì sao?

Thu/hoàn có UUID requestKey. Cùng booking/actor/kind/amount/note đã ghi trả kết quả hiện hành, không cộng tiền lần nữa; key trùng khác nội dung bị xung đột. Thao tác mới cần version đúng. PAYMENT chỉ CONFIRMED/COMPLETED và không vượt total; REFUND chỉ CANCELLED và tổng hoàn không vượt đã thu. Đây là ghi nhận thủ công, không tích hợp ngân hàng.

Nguồn: [catalog](../../src/main/java/vn/vietroute/business/catalog), [carrier](../../src/main/java/vn/vietroute/business/carrier), [BookingService](../../src/main/java/vn/vietroute/business/booking/service/BookingService.java), [TripSegmentRepo](../../src/main/java/vn/vietroute/business/trip/repo/TripSegmentRepo.java), TripService/TripRepo và BusinessIntegrationTest.

## Báo cáo, tài xế và ngày — Q30 đến Q34

### Q30. Giá trị đặt, đã thu, thực thu ròng và còn phải hoàn khác nhau thế nào?

confirmedValue cộng total của đơn CONFIRMED/COMPLETED, không là tiền đã thu. netAmount = paidAmount − refundedAmount. Ví dụ đơn đã hủy có total1.000.000, thu 600.000, hoàn 100.000 thì net 500.000 và còn phải hoàn 500.000; đơn hủy không nằm trong confirmedValue. Không gọi tổng giá đặt là doanh thu thực nhận.

### Q31. Báo cáo chọn ngày chuyến hay ngày giao dịch tiền?

Bản hiện tại lọc tập chuyến theo departure_at trong khoảng ngày Việt Nam, rồi tổng hợp đơn/tiền của tập đó. Không phải sổ tiền theo ngày PAYMENT/REFUND event. Dùng đầu ngày đến trước đầu ngày sau cận cuối để không bỏ mốc cuối ngày. Muốn báo cáo tiền theo ngày thu phải thêm contract/query/test mới.

### Q32. Làm sao tránh báo cáo nhân đôi số chuyến hoặc đọc các tổng lệch nhau?

ReportRepo dùng CTE chọn tập chuyến theo scope, tổng chuyến và tổng đơn ở các nhánh tổng hợp rồi kết hợp kết quả trong một statement. Không join từng chuyến với nhiều đơn rồi count chuyến thẳng. asOf là snapshot statement; sau đó dữ liệu vẫn có thể đổi, UI phải chủ động tải lại. confirmedSeats là lượt chỗ các đơn, không số ghế vật lý duy nhất.

### Q33. Không GPS thì theo dõi tài xế bằng gì, có chống giả tọa độ không?

Driver xác nhận START/ARRIVE/DEPART/COMPLETE theo stop và version, server ghi thời điểm, SQL kiểm assignment. START không sớm hơn30 phút trước lịch; ARRIVE/DEPART phải đúng thứ tự, COMPLETE ở điểm cuối đã đến. Đây là xác nhận thủ công, không chứng minh xe đang ở đâu và không có bài toán chống giả GPS trong phạm vi này.

### Q34. Chuyến qua nửa đêm tìm theo ngày nào?

Theo ngày tại điểm đón ở Asia/Ho_Chi_Minh, không mặc định ngày rời bến đầu hoặc timezone browser. Một chuyến đang chạy có thể còn nhận tại điểm phía trước nếu điều kiện bookable và stop chưa rời; điểm đi/đến phải đúng thứ tự. Thời gian lưu Instant/TIMESTAMPTZ, hiển thị theo múi giờ nghiệp vụ; dùng ví dụ đón00:30 hôm sau để giải thích.

Nguồn: [ReportRepo](../../src/main/java/vn/vietroute/business/report/repo/ReportRepo.java), ReportSummaryResponse/ReportSearch, [DriverTripService](../../src/main/java/vn/vietroute/business/trip/service/DriverTripService.java), JourneyRepo và TripStopRepo.

## Vận hành, bằng chứng và giới hạn — Q35 đến Q45

### Q35. Nhật ký có lưu password, payload và response nguyên bản không?

Không. Journal chỉ giữ projection field whitelist, class/field mới mặc định bỏ; auth/access body bị bỏ, PII/secret/token/header/cookie không ghi raw. Có traceId/status/duration, user từ xác thực server và snapshot đã lọc. Export cURL/Postman dùng placeholder, phải tự điền credential/nội dung trên môi trường được phép.

### Q36. Nếu DB log lỗi thì nghiệp vụ lỗi theo hay có đủ mọi trace không?

Journal có pool/commit riêng, timeout/backoff và failedWrites; lỗi ghi không biến một booking đã thành công thành thất bại. Không RAM queue/gửi bù nên có thể mất trace, counter reset khi restart. History trạng thái/tiền/quyền cùng transaction là chuyện khác, lỗi phải rollback nghiệp vụ. Không gọi journal là audit bất biến.

### Q37. Demo401/403/404/409 nên giải thích thế nào?

401 là vấn đề xác thực/phiên;403 là thiếu grant hoặc CSRF/quyền tương ứng, xem code/messageKey;404 có thể là ngoài scope/owner;409 là version/trạng thái/requestKey xung đột. UI giữ draft và không tự gửi lại mutation. Thay ID trên URL không được dùng để dò dữ liệu nhà xe khác.

### Q38. Vì sao migration có 8 file và không sửa file cũ để thêm cột?

Đây là baseline theo 6 cụm schema + system + FK, manifest giữ thứ tự phụ thuộc và checksum byte gốc. DB đã áp có history; sửa file cũ phá đối chiếu. Thay schema tiếp theo thêm bước theo cụm. Rebaseline là bảo trì được cho phép, backup/kiểm phục hồi/dừng node/kiểm cấu trúc trước đổi history, không tự reset DB khi startup lỗi.

### Q39. Dữ liệu này có thật không, restart có tự tạo lại hoặc đổi password không?

Đây là bộ giả lập vận hành có quan hệ/giá/chỗ/tiền/history, không dữ liệu thương mại được xác minh. DB mới đủ 4 cờ có 46 account/7 nhà xe/646 chuyến/3866 đơn. Mã/ngày neo lần đầu, seed chỉ thêm thiếu và giữ hash/quyền/trạng thái đã sửa hoặc thu hồi; restart không biến demo thành ngày hôm nay. Email giả lập không là inbox đã gửi thư.

### Q40. Kiểm thử được gì, H2 thay PostgreSQL thật được không?

H2 kiểm phần portable, model và một số repo; PostgreSQL riêng kiểm dialect/array/JSONB/lock/FK/migration/transaction/concurrent. Unit/MVC kiểm validation/security/context và Node/browser kiểm UI. Số test phải gắn commit/môi trường và số skipped; CI xanh không là SLA/tải thật/Oracle đã chạy. Mốc199 Java/66 Node thuộc UI02, không tự gán vào mọi nhánh.

### Q41. Swagger và tìm chuyến mới đã có trên Render chưa?

Tra tiến độ/PR trước trả lời. Tại mốc quy hoạch này PR154 đã CI xanh nhưng chưa merge: khóa Swagger vào grant quản trị, menu cách 6px và select tỉnh→địa điểm/một nút tìm nằm ở nhánh đó. Render đang image main bbeec57. Không bấm bản preview rồi khẳng định production đã khóa Swagger.

### Q42. Local khác Render và bí mật cấu hình được giữ ở đâu?

Local dùng launcher/Run config, .env local; OS/IDE ưu tiên. Render dùng env và WAR/image, không đọc .env hoặc cài Node. DB hiện hành hieuboy/vietroute, .env không theo dõi Git. Chủ dự án cho phép .env.example giữ đầy đủ password/key khác, riêng VIETROUTE_DB_PASSWORD là XXX; không trình chiếu nội dung file. Lịch sử cũ chưa viết lại. Máy chủ DB chưa TLS, chủ dự án đã chấp thuận riêng; không quảng bá kết nối đã mã hóa.

### Q43. Đã hỗ trợ Oracle26ai đầy đủ chưa?

Chưa. Có JDBC Thin/ojdbc17, profile và dialect chuẩn bị nhưng runtime chủ động chặn trước SQL kể cả init tắt. Repo còn array/JSONB/ON CONFLICT/RETURNING/advisory lock theo PostgreSQL, chưa có migration/transaction schema hoặc regression Oracle thật. Driver nhận URL không chứng minh nghiệp vụ đã chuyển DB.

### Q44. Nếu đưa vào dùng thật, những giới hạn nào cần xử lý?

Đánh giá tải/SLA trên staging; TLS DB, backup di động/PITR và drill/RPO/RTO; quy trình vận hành, dữ liệu đơn vị và cách xử lý sự cố. Render Free có cold start; chưa có payment gateway, GPS/mobile, SSO/MFA/refresh token hoặc mail/reset. Nêu thứ cần thiết theo yêu cầu mới, không nói đã làm; không phục hồi module phụ chỉ để slide có nhiều chức năng.

### Q45. Từng thành viên làm gì, AI có tham gia thì trình bày thế nào?

Nêu khối được giao và bằng chứng commit/PR/review/test thật. Hieu phụ trách nền auth/common/DB/deploy; Anh catalog/carrier; Bang trip/booking; Hien report/dashboard. Trách nhiệm bảo trì không biến code nền đã có thành tác giả mới; không đổi author hoặc đếm commit thay chất lượng. Có hỗ trợ AI thì nói rõ công cụ hỗ trợ, người phải hiểu/kiểm source và chịu trách nhiệm phần trình bày.

Nguồn: [vận hành](../04-van-hanh/VAN_HANH.md), [schema](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), [phân công](PHAN_CONG.md) và các regression trong [src/test](../../src/test).

## Báo cáo sản phẩm theo mẫu Công nghệ Java

Ngày 31/10/2026, chủ dự án chỉ định [mẫu Word Công nghệ Java](mau/Mau_BTL_Cong_nghe_Java.docx) làm format cho tài liệu sản phẩm VietRoute và yêu cầu lưu file mẫu gốc trong Git để dùng chung khi đổi máy. Giữ nguyên byte của bản mẫu; SHA-256 là `bd8fc0707ebadb9373d988f28bc7d4b036542f6bb8a662191228f410ca5089c6`. Mẫu theo repository tại `docs/05-ke-hoach/mau/`, không dùng đường dẫn máy cá nhân làm nguồn thay thế khi bàn giao. Đây là báo cáo bài tập lớn học phần Công nghệ Java, khác với hướng dẫn vận hành/API hiện có.

Chủ dự án đã xác nhận: **chỉ đọc mẫu và chốt cấu trúc trước, hoàn thiện ứng dụng rồi mới soạn tài liệu**. Báo cáo thuộc **Nhóm 10**, gồm bốn thành viên đã xác định tại [phân công](PHAN_CONG.md#thông-tin-thành-viên): `anhhoang104`, `BangPhamIT`, `hiencoi250404-design`, `hieuv526034`. **Lớp, khóa và giảng viên để trống**, bổ sung sau theo thông tin chủ dự án cung cấp. Họ tên/MSSV dùng dữ liệu đã được xác nhận khi soạn; không suy từ username hoặc tự tạo danh tính. Không bắt đầu viết báo cáo Word hoàn chỉnh chỉ từ việc ghi nhận thông tin này.

### Cấu trúc giữ từ mẫu

| Phần | Nội dung áp dụng cho VietRoute |
|---|---|
| Bìa ngoài | Cơ quan/trường/khoa/học phần theo thông tin được xác nhận, tên đề tài VietRoute, sinh viên/lớp/khóa và năm |
| Phụ bìa | Số nhóm, danh sách sinh viên/MSSV, ô điểm và cán bộ chấm như mẫu; không tự điền điểm, tên hoặc chữ ký |
| Mục lục | Trang riêng, dùng Heading 1–3 và mục lục Word; cập nhật số trang sau khi bố cục cuối đạt |
| Danh mục viết tắt | Giải thích các từ thực sự dùng như OOP, JDK, MVC, JDBC, DTO, JWT, API và CSRF |
| Danh mục bảng/hình | Trang riêng khi có bảng/sơ đồ; tên và số theo chương, đối chiếu đúng trang sau kết xuất |
| Chương 1 Tổng quan về đề tài | Bài toán quản lý vận tải/đặt vé theo chặng, mục tiêu/phạm vi, tác nhân và phân công có căn cứ |
| Chương 2 Phân tích thiết kế chương trình | Yêu cầu, kiến trúc lớp, sơ đồ lớp/quan hệ, mô hình dữ liệu và lưu đồ các chức năng chính; giải thích transaction, quyền/scope và tồn chỗ |
| Chương 3 Cài đặt | Môi trường/công nghệ; module thực tế, luồng màn hình/API, validation/ngoại lệ, hướng dẫn chạy và ca kiểm/ảnh minh họa |
| Kết luận | Kết quả đã kiểm, hạn chế và hướng phát triển; nêu đúng mức hỗ trợ Oracle và dịch vụ ngoài |
| Tài liệu tham khảo | Đánh số theo thứ tự trích dẫn, dùng [n] trong nội dung; ghi cơ quan/tác giả, năm, tên, nguồn và ngày truy cập với tài liệu web |

### Định dạng và xử lý phần mẫu

Hướng dẫn viết trong mẫu yêu cầu A4 dọc; lề trên 2,5 cm, dưới 2 cm, trái 3 cm, phải 1,5 hoặc 2 cm. Chọn phải 2 cm khi chưa có yêu cầu khác. Font Times New Roman, thân bài 13pt, căn đều, dòng đầu 1,27 cm, Before/After 4pt và giãn dòng Multiple 1,3. Tên chương 14pt, đậm/in hoa/căn giữa; mục cấp một 13pt đậm, cấp hai đậm nghiêng; tối đa bốn mức. Tên bảng 13pt nghiêng đặt phía trên, tên hình đặt phía dưới; nội dung bảng theo mẫu và không thu chữ để ép số trang. Bìa giữ các mức 14/18pt được chú thích ở các textbox.

Thiết lập section hiện lưu trong file khác hướng dẫn chữ: khoảng 20,99 × 29,66 cm, trên 2,50 cm/dưới 3,00 cm/trái 2,50 cm/phải 2,00 cm. Khi tạo bản nộp, áp dụng yêu cầu định dạng ghi trong mẫu ở đoạn trên và ghi nhận thay đổi này; không sao chép nhầm các lề đang lệch. Bản mẫu có sáu section, TOC/PAGEREF/SEQ và PAGE trong footer; cần giữ cơ chế mục lục/đánh số, kiểm lại section bắt đầu số trang sau phần bìa. Không suy số trang thật từ metadata DOCX.

Thay toàn bộ ví dụ quản lý sinh viên/Student/ArrayList và tên Nguyễn Văn A/B/C bằng mô tả VietRoute đúng source. Bỏ các chỉ dẫn “xóa khi in”, bảng quy cách trình bày, textbox hướng dẫn bìa và hình minh họa thuộc đề tài mẫu khỏi bản nộp; giữ cấu trúc bìa, bảng thông tin, heading và số trang có chủ đích. Không gán UI Swing/JavaFX cho sản phẩm Spring MVC/Thymeleaf/JPA/JDBC. Khi soạn, dùng số nhóm và quy tắc điền thông tin đã chốt ở đầu mục này; không lấy tên/MSSV minh họa trong mẫu làm dữ liệu thật.

### Nguồn nội dung và điều kiện chốt

Dùng [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md) cho bài toán; [API](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md#hợp-đồng-api), [schema/quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [UI](../03-giao-dien/QUY_UOC_UI.md) và source cho thiết kế/cài đặt. [Phân công](PHAN_CONG.md) là trách nhiệm; chỉ ghi đóng góp có commit/PR/bằng chứng trong [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md).

Phân biệt bản trên dev và main/Render, ca thử cô lập và hạ tầng thật. Oracle 26ai hiện mới ORACLECFG01 cấu hình JDBC Thin/profile/Hibernate dialect, chưa có runtime/migration/SQL nghiệp vụ và chưa kiểm kết nối Oracle thật; các dịch vụ ngoài phạm vi hiện hành không đưa vào báo cáo như chức năng đã làm. Backup/restore phải có bằng chứng riêng. Chỉ đưa ảnh được xác minh thuộc phiên bản báo cáo; che token/mật khẩu/PII, ghi rõ ảnh dùng dữ liệu kiểm thử khi cần. Kết quả test có mốc commit/môi trường, không diễn giải CI thành SLA production.

Trước bàn giao Word: đối chiếu nội dung từng chương với source, cập nhật mục lục/danh mục/số trang, kiểm bản mẫu còn nguyên checksum, kết xuất và xem mọi trang để phát hiện vỡ bảng, thiếu dấu, khoảng trống, ảnh/heading bị tách. Lượt đọc mẫu mới kiểm nội dung OOXML/styles/section/textbox/field; công cụ kết xuất đóng gói chưa chạy được vì thiếu LibreOffice trên môi trường Windows. Chưa có bản Word sản phẩm được tạo hoặc nghiệm thu bố cục.
