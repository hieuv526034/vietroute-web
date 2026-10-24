# Đặc tả đồ án: quản lý nhà xe, tìm chuyến, đặt chỗ và theo dõi hành trình

Ngày lập: 21/10/2026. Nhóm: 4 thành viên. Thời gian thực hiện: 2 tuần.

Tài liệu này chốt phạm vi và tiêu chí nghiệm thu để nhóm triển khai. Đây là đặc tả, chưa phải ứng dụng đã được xây dựng hoặc kiểm thử.

## 1. Yêu cầu người dùng đã xác nhận

| Mã | Yêu cầu |
|---|---|
| R01 | Một project Spring MVC + Thymeleaf, xử lý Java và giao diện cùng source. |
| R02 | JDBC thuần; dùng Connection, PreparedStatement, ResultSet. Không dùng Spring Boot, React, JPA/Hibernate, JdbcTemplate hoặc Spring Data JDBC. |
| R03 | Nhiều hãng/nhà xe và nhiều loại xe, nhiều chuyến trên toàn quốc, có tuyến Bắc–Nam và Nam–Bắc. |
| R04 | Có tuyến đi, tuyến về, các điểm đón/trả và chặng theo thứ tự. |
| R05 | Khách xem thông tin, liên hệ tài xế/nhà xe để đặt chỗ. |
| R06 | Khách xem tiến độ, vị trí và thời điểm cập nhật. Phương án bấm từng chặng ban đầu được điều chỉnh theo phản hồi về vận hành; xem mục 17 về tự gửi vị trí từ website. |
| R07 | Tích hợp Google Maps để xem lộ trình và địa điểm. |
| R08 | Đủ các luồng chính và tình huống lỗi; chia công việc cho 4 thành viên. |
| R09 | Chỉ xây dựng website; không phát triển ứng dụng Android/iOS. Tài xế và khách dùng trình duyệt, có thể bố trí giao diện thích ứng màn hình nhỏ. |

Tên đề tài đề xuất: **Website quản lý nhà xe, tìm kiếm và đặt chỗ xe khách liên tỉnh, theo dõi hành trình theo chặng tích hợp Google Maps**.

## 2. Các quyết định thiết kế đề xuất cho bản nộp

Đây là cách cụ thể hóa yêu cầu để triển khai trong 2 tuần, không phải các tính năng người dùng đã yêu cầu riêng lẻ.

- Mỗi nhà xe là một đơn vị vận hành độc lập. Không cần thêm tầng công ty mẹ/chi nhánh trong bản đầu.
- Quản trị viên tạo nhà xe và cấp tài khoản quản lý; khách tự đăng ký; quản lý nhà xe tạo tài khoản tài xế thuộc đơn vị mình.
- Hai chiều của tuyến là hai bản ghi riêng, mỗi chiều có điểm dừng và mức giá riêng.
- Có yêu cầu đặt chỗ trên website bên cạnh nút gọi điện; nhà xe xác nhận hoặc từ chối.
- Với đặt qua điện thoại, OPERATOR nhập yêu cầu thay khách và xác nhận bằng cùng nghiệp vụ tồn chỗ; tài xế chuyển thông tin cho nhà xe, không hứa giữ chỗ ngoài hệ thống rồi bỏ qua cập nhật.
- Bổ sung theo chủ dự án 24/10/2026: khách tra cứu được đầy đủ nhà xe/chuyến phù hợp trước khi lựa chọn. Mỗi khách chỉ có một chuyến đặt đang hoạt động để đặt/theo dõi; hoàn tất chuyến mới đặt chuyến khác. BOOKING01 cần chốt rõ các trạng thái pending/đã xác nhận/đang chạy/hủy/từ chối và kiểm nguyên tử khi tạo đồng thời; chưa triển khai quy tắc này chỉ bằng bộ phân quyền. Không dùng membership quản trị nhà xe để lọc mất kết quả tra cứu công khai của khách.
- Đặt theo số lượng chỗ, chưa chọn số ghế/giường cụ thể. Kiểm tra chỗ trống theo từng chặng và tái sử dụng chỗ trên các chặng không giao nhau.
- Yêu cầu chờ xác nhận chưa giữ chỗ. Giao diện phải nói rõ: chỉ đơn đã xác nhận mới bảo đảm chỗ.
- Thanh toán trực tiếp; có thể ghi nhận đã thu/chưa thu. Chưa tích hợp cổng thanh toán, hoàn tiền tự động, SMS hoặc email.
- Một chuyến dùng một xe và một tài xế phụ trách tài khoản. Việc bố trí thêm lái xe ngoài thực tế không thuộc chức năng quản trị nhân sự của đồ án.
- Lịch, giá và tiến độ là dữ liệu nhà xe khai báo. Giờ tương lai là giờ dự kiến, không phải ETA tính từ GPS.
- Số lượng nhà xe/tuyến/chuyến nằm trong dữ liệu, không viết cố định vào mã nguồn.
- Một đơn phục vụ đoạn đi trên một chuyến xe; bản đầu chưa tự tìm hành trình nối nhiều chuyến hoặc chuyển giữa các nhà xe.

## 3. Vai trò và phạm vi dữ liệu

| Vai trò | Quyền |
|---|---|
| Khách chưa đăng nhập | Tra cứu nhà xe, tuyến, chuyến, giá, chỗ còn, lộ trình, tiến độ và số điện thoại liên hệ được công bố. |
| CUSTOMER | Quyền tra cứu; tạo/hủy yêu cầu hợp lệ; xem lịch sử và trạng thái đơn của chính mình. |
| OPERATOR | Quản lý thông tin, xe, tài xế, tuyến, chuyến và đơn đặt của nhà xe mình; xử lý hủy chuyến và ghi nhận tiền thu trực tiếp. |
| DRIVER | Xem chuyến được phân công, danh sách khách cần đón/trả; cập nhật chặng của chuyến được giao. |
| ADMIN | Quản lý danh mục dùng chung, tạo/kích hoạt/khóa nhà xe và tài khoản, xem thống kê tổng hợp. |

Các thao tác phải kiểm tra quyền trên server. OPERATOR không được đọc/sửa đơn, xe, tài xế hay chuyến của nhà xe khác khi thay ID trên URL. DRIVER chỉ cập nhật chuyến được giao. CUSTOMER chỉ xem dữ liệu cá nhân và đơn của mình. company_id của người quản lý lấy từ tài khoản đăng nhập, không tin giá trị gửi từ form.

Khóa nhà xe: dừng đăng chuyến mới và nhận yêu cầu mới; vẫn cho người có trách nhiệm xử lý các đơn/chuyến tồn tại. Khóa tài khoản tài xế: phải phân công người thay thế cho chuyến chưa hoàn thành. Không xóa lịch sử.

## 4. Mô hình nhà xe – tuyến – chuyến – điểm dừng

- **Nhà xe:** tên thương mại, điện thoại, mô tả, trạng thái.
- **Loại xe:** mô tả dạng xe như ghế ngồi, giường nằm, limousine; số chỗ bán của từng xe khai báo riêng.
- **Xe:** thuộc nhà xe, biển số, loại xe, số chỗ có thể bán, trạng thái.
- **Điểm dừng:** tên địa điểm cụ thể, tỉnh/thành, địa chỉ, tọa độ nhập bởi người quản lý.
- **Tuyến:** thuộc nhà xe, một chiều, tập hợp điểm dừng theo thứ tự.
- **Chuyến:** một lần chạy tuyến vào ngày giờ cụ thể, có xe, tài xế, các mốc thời gian và tình trạng đặt chỗ riêng.
- **Chặng:** đoạn giữa hai điểm dừng liền kề; là đơn vị tính chỗ trống.

Ví dụ minh họa, không phải lịch trình chính thức của nhà xe Oho:

| Tuyến | Điểm theo thứ tự |
|---|---|
| Oho Hải Phòng → Hà Nội | Hải Phòng → điểm A → điểm B → Hà Nội |
| Oho Hà Nội → Hải Phòng | Hà Nội → điểm C → điểm A → Hải Phòng |
| Nhà xe demo Bắc → Nam | điểm phía Bắc → điểm miền Trung → điểm phía Nam |
| Nhà xe demo Nam → Bắc | điểm phía Nam → các điểm chiều về → điểm phía Bắc |

Tuyến đi và về không bắt buộc có cùng đường đi, điểm dừng, giá hoặc thời gian chạy. Xe cùng loại của hai nhà xe vẫn là các xe độc lập. Nhiều nhà xe có thể cùng khai thác một cặp điểm đi/đến.

Khi tạo chuyến, sao chép danh sách điểm dừng, thời gian dự kiến và giá của tuyến vào dữ liệu chuyến. Sửa mẫu tuyến sau đó không làm thay đổi chuyến/đơn đã tồn tại. Chuyến đã có đơn không được tự ý thay thứ tự điểm, giá đã chốt hoặc giảm chỗ xuống dưới số đã bán.

Chuyến đường dài phải lưu **ngày và giờ đầy đủ** tại từng điểm; độ lệch thời gian so với điểm đầu được phép lớn hơn 24 giờ. Không lưu chỉ HH:mm. Hiển thị theo múi giờ Asia/Ho_Chi_Minh.

## 5. Danh sách luồng và điều kiện nghiệm thu

| Mã | Người thực hiện | Luồng thành công | Tình huống cần xử lý |
|---|---|---|---|
| F01 | Khách | Đăng ký → đăng nhập → đổi mật khẩu → đăng xuất. | Trùng định danh, nhập sai, tài khoản bị khóa; không lưu mật khẩu rõ. |
| F02 | ADMIN | Tạo nhà xe → cấp OPERATOR → kích hoạt. | Trùng dữ liệu cần duy nhất, khóa khi đang có chuyến, giữ lại lịch sử. |
| F03 | ADMIN | Quản lý tỉnh/thành, địa điểm và loại xe. | Danh mục đang được sử dụng chỉ ngừng sử dụng; không xóa gây mất tham chiếu. |
| F04 | OPERATOR | Tạo tài xế và xe của nhà xe. | Biển số trùng, số chỗ không hợp lệ, tài xế/xe không hoạt động. |
| F05 | OPERATOR | Tạo tuyến một chiều → thêm/sắp điểm dừng → giá và mốc giờ. | Ít hơn 2 điểm, thứ tự trùng, giờ giảm, giá giảm, điểm không tồn tại. |
| F06 | OPERATOR | Tạo chuyến từ tuyến → phân xe/tài xế → mở nhận đặt. | Xe/tài xế khác nhà xe, bị khóa hoặc có lịch chạy chồng nhau; chuyến thiếu điểm dừng. |
| F07 | Khách | Chọn điểm đón/trả, ngày lên xe, số chỗ → lọc nhà xe/loại xe/giá → xem kết quả có phân trang. | Không có chuyến, chọn ngược chiều, thiếu chỗ, điểm đón đã đi qua. |
| F08 | Khách | Xem chi tiết chuyến, điểm dừng, lịch dự kiến, giá, chỗ, Maps và liên hệ. | Chuyến bị hủy, Maps lỗi, dữ liệu cập nhật chậm; vẫn đọc được lộ trình dạng văn bản. |
| F09 | CUSTOMER | Chọn chuyến và điểm đón/trả → kiểm tra giá → gửi yêu cầu PENDING. | Chọn điểm khác chuyến, số chỗ <= 0, gửi lặp form, điểm đón đã đóng. |
| F10 | OPERATOR | Xem yêu cầu → xác nhận còn chỗ → CONFIRMED; lưu sự kiện. | Hết chỗ lúc xác nhận, bấm xác nhận hai lần, hai quản lý thao tác đồng thời, sai nhà xe. |
| F11 | OPERATOR | Từ chối PENDING và ghi lý do. | Không được từ chối lại đơn đã xác nhận; phải dùng luồng hủy tương ứng. |
| F12 | CUSTOMER/OPERATOR | Hủy đơn trước khi xe rời điểm đón; giải phóng đúng các chặng nếu đơn đã CONFIRMED. | Hủy lặp, hủy sau khi xe rời điểm đón, sai chủ đơn, trạng thái không cho phép. |
| F13 | DRIVER | Xem chuyến được phân công, danh sách khách theo điểm đón/trả, gọi liên hệ. | Không nhìn thấy khách của chuyến khác; giờ lên xe là giờ tại điểm đón của khách. |
| F14 | DRIVER | Bắt đầu chuyến → xác nhận đến/rời từng điểm theo thứ tự → đến điểm cuối → hoàn thành. | Sai tài xế, đi lùi/nhảy chặng, bấm lặp, cập nhật chuyến hủy/đã hoàn thành. |
| F15 | Khách | Xem điểm được xác nhận gần nhất, thời điểm cập nhật, chặng tiếp theo và lộ trình Maps. | Chưa khởi hành, chưa có cập nhật, chuyến hoãn/hủy, bản đồ không tải được. |
| F16 | OPERATOR | Hủy chuyến chưa khởi hành → đóng nhận đặt → hủy đơn liên quan → ghi thông báo trong lịch sử đơn. | Không bỏ sót đơn hoặc trả chỗ hai lần; chuyến đang chạy không dùng luồng hủy thông thường. |
| F17 | CUSTOMER | Xem lịch sử đơn, xác nhận/từ chối/hủy, thông tin liên hệ và tiến độ chuyến. | Không truy cập đơn người khác bằng mã đơn hoặc ID. |
| F18 | OPERATOR/ADMIN | Xem số chuyến, yêu cầu, đơn xác nhận/hủy, lượt chỗ đã bán và tiền đã ghi nhận thu. | OPERATOR chỉ xem nhà xe mình; không coi đơn PENDING hoặc giá niêm yết là tiền đã thu. |
| F19 | OPERATOR | Khách gọi điện → nhập thông tin liên hệ, chuyến, điểm đón/trả, số chỗ → xác nhận cùng luồng tồn chỗ. | Không xác nhận nếu hết chỗ; ghi nguồn PHONE và người tạo; không bắt khách phải có tài khoản website. |

Đơn vị chịu trách nhiệm F02–F06 là nhà xe/quản trị; F07–F12 và F19 là tìm và đặt; F13–F15 là tài xế/hành trình; F16–F18 nối các phần để hoàn thành vòng đời.

## 6. Tìm chuyến đúng chiều, đúng ngày và đúng chặng

Với hai điểm của một chuyến có thứ tự i và j, chuyến hợp lệ khi i < j. Cả hai điểm phải thuộc cùng chuyến và được cho phép đón/trả tương ứng. Bản đầu không lặp cùng một điểm trong một tuyến để tránh nhiều cách hiểu thứ tự.

Ngày khách tìm là **ngày dự kiến lên xe tại điểm đón**, không nhất thiết là ngày chuyến rời điểm đầu. Ví dụ chuyến xuất phát tối ngày D, qua điểm đón vào sáng D+1, thì khách tại điểm đó phải tìm được chuyến ở ngày D+1.

Chỉ cho gửi/xác nhận yêu cầu khi chuyến đang nhận khách, điểm đón chưa rời và đủ chỗ trên toàn bộ đoạn khách đi. Chuyến đang chạy có thể nhận khách tại các điểm phía trước chưa đi qua. Nếu giờ dự kiến đã qua nhưng chuyến đang trễ, hiển thị lịch dự kiến và trạng thái đã cập nhật; không khẳng định xe đã qua chỉ dựa trên đồng hồ.

Đối với chuyến chưa khởi hành, không để bán những chuyến đã quá giờ nhưng không có ai quản lý trạng thái: quản lý phải cập nhật giờ trước khi mở nhận đặt lại. Mốc thời gian quá hạn và trạng thái cập nhật phải được diễn đạt rõ trên UI.

Tìm theo tỉnh/thành có thể có nhiều điểm đón; kết quả phải chỉ rõ điểm đón, điểm trả và giờ tương ứng. Bộ lọc lấy dữ liệu từ database, có phân trang và thứ tự sắp xếp ổn định.

## 7. Giá và số chỗ theo từng chặng

### 7.1. Giá

Để giảm khối lượng, dùng giá cộng dồn từ điểm đầu. Mỗi điểm dừng có cumulative_fare; giá một chỗ từ i đến j bằng cumulative_fare[j] - cumulative_fare[i]. Tổng tiền dự kiến bằng giá một chỗ nhân số chỗ.

Đây là chính sách giá của bản demo. Nhà xe ngoài thực tế có thể áp dụng bảng giá riêng từng cặp điểm; phần đó chưa nằm trong bản đầu. Giá đơn phải được sao chép lúc gửi yêu cầu và giữ nguyên khi xác nhận; không âm thầm tính lại từ bảng tuyến đã sửa.

### 7.2. Tồn chỗ

Một chuyến A → B → C → D có ba chặng A–B, B–C, C–D. Đơn A → C chiếm chỗ trên A–B và B–C. Đơn C → D có thể dùng lại chỗ đó.

Số chỗ còn cho i → j bằng số nhỏ nhất của (capacity - reserved_seats) trên các chặng i đến j-1. Kiểm tra một tổng số chỗ cho toàn chuyến sẽ làm mất khả năng nhận khách dọc đường nên không dùng cách đó trong thiết kế này.

PENDING không tăng reserved_seats; CONFIRMED tăng đúng các chặng; hủy CONFIRMED giảm đúng các chặng. Không giảm lần nữa khi khách bấm hủy lại. Các chặng lịch sử của đơn đã hoàn thành không bị sửa để tái sử dụng cho chuyến khác.

### 7.3. Transaction JDBC

Khi xác nhận đơn:

1. Mở một Connection; setAutoCommit(false).
2. Khóa dòng chuyến, sau đó dòng đơn, rồi các dòng chặng liên quan theo thứ tự tăng dần. Các luồng thay đổi đặt chỗ/tiến độ/hủy cùng chuyến tuân thủ thứ tự khóa này.
3. Kiểm tra nhà xe, trạng thái chuyến/đơn, điểm đón chưa rời, số chỗ và giá đơn đã chốt.
4. Nếu mọi chặng đủ chỗ, cập nhật tồn chỗ, chuyển đơn CONFIRMED, ghi sự kiện.
5. commit; có lỗi thì rollback. Truyền cùng Connection cho các DAO tham gia; không để từng DAO tự mở một kết nối mới cho một phần giao dịch.

Hủy đơn và hủy chuyến cũng phải nguyên khối. Dùng PreparedStatement với tham số. Nút gửi form và mã yêu cầu duy nhất hỗ trợ chống tạo trùng; việc chống trùng trạng thái phải được kiểm tra trong transaction ở server.

## 8. Trạng thái và xử lý ngoại lệ

**Chuyến:** DRAFT → OPEN → IN_PROGRESS → COMPLETED. DRAFT/OPEN có thể chuyển CANCELLED. DRAFT không hiển thị cho khách; OPEN chỉ được bật khi dữ liệu xe, tài xế, giờ, điểm dừng và giá hợp lệ.

**Đơn:** PENDING → CONFIRMED hoặc REJECTED hoặc CANCELLED. CONFIRMED → CANCELLED trước khi xe rời điểm đón; CONFIRMED → COMPLETED khi hành trình phục vụ đơn kết thúc. Hoàn thành có thể cập nhật khi xe đến điểm trả; chức năng chỉ dựa vào mốc chặng, chưa xác nhận GPS hay lên xe tự động.

Khi xe rời một điểm đón, các đơn PENDING tại điểm đó được đóng thành REJECTED với lý do hết thời gian đón. Đơn không tự động được xác nhận vì tài xế đi qua điểm. Đơn đã CONFIRMED được giữ trong danh sách khách; kiểm đếm lên xe thực tế là phần mở rộng nếu còn thời gian.

Đơn đã hoàn thành không được hủy và không bị đổi theo chuyến tương lai. Chuyến đã kết thúc không được cập nhật tiến độ nữa. Đơn hủy giữ lịch sử lý do, người thao tác và thời gian.

Hủy chuyến OPEN: khóa chuyến trước, đóng nhận đặt, chuyển các đơn PENDING/CONFIRMED còn hiệu lực sang CANCELLED, giải phóng tồn chỗ liên quan đúng một lần, ghi sự kiện trong lịch sử đơn. Các giao dịch xác nhận đơn phải dùng cùng khóa chuyến để không chen vào lúc hủy.

Sự cố chuyến IN_PROGRESS: OPERATOR ghi thông báo sự cố và thông tin liên hệ để xử lý ngoài hệ thống; không tự hoàn tiền, chuyển khách hay đưa chuyến trở lại trạng thái chưa chạy. Tính năng tự động điều xe thay thế nằm ngoài bản đầu.

Nếu có ghi nhận thu trực tiếp: theo dõi tiền đã thu tách khỏi trạng thái đặt; đơn hủy đã thu phải có cờ cần xử lý hoàn tiền và ghi nhận thủ công sau khi thực sự hoàn. Không hiển thị đã hoàn tiền chỉ vì hủy đơn.

## 9. Google Maps và theo dõi chặng

- Bản đồ toàn tuyến dùng Maps Embed API directions: origin, destination, waypoints và driving.
- Màn hình theo dõi bản đầu có hai lựa chọn: “Toàn bộ lộ trình” dùng directions và “Điểm được xác nhận gần nhất” dùng place. Kèm danh sách tiến độ đã qua/chưa tới và thời điểm cập nhật. Không giả định iframe directions cho phép tự vẽ thêm biểu tượng xe.
- Điểm dừng dùng tên/địa chỉ hoặc Place ID hợp lệ cho directions; tọa độ có thể dùng cho liên kết vị trí. Địa điểm và thứ tự là dữ liệu ứng dụng, không đọc ngược nội dung iframe.
- Maps Embed hỗ trợ tối đa 20 điểm trung gian. Với tuyến nhiều điểm hơn, chia thành các đoạn bản đồ liền nhau; vẫn hiển thị toàn bộ điểm dừng trong danh sách. Liên kết mở Google Maps cũng có giới hạn độ dài và số waypoint theo thiết bị, nên không giả định một liên kết chứa được mọi điểm.
- Bản đồ hiển thị đường Google đề xuất qua các điểm khai báo. Không coi đó là bằng chứng xe đã đi đúng toàn bộ đường ấy.
- Tiến độ lấy từ trip_stops/trip_events: điểm đã đến/rời, điểm tiếp theo, người cập nhật và thời điểm.
- UI ghi: “Điểm được tài xế xác nhận gần nhất …, cập nhật lúc …”. Không đặt nhãn vị trí GPS hiện tại.
- Khách có thể bấm tải lại tiến độ; bản đầu dùng form/trang Thymeleaf là đủ. JavaScript nhỏ trong cùng project có thể bổ sung sau, không cần frontend riêng.
- Tài xế cập nhật qua trang web của mình, khách xem qua trang web của khách; hai phiên đăng nhập độc lập. Demo có thể mở hai trình duyệt trên máy tính. Việc thêm JavaScript fetch định kỳ vào trang Thymeleaf vẫn thuộc cùng ứng dụng web.
- Nếu bổ sung biểu tượng xe tùy chỉnh trên cùng bản đồ, dùng Maps JavaScript API; chỉ chuyển biểu tượng đến tọa độ điểm đã được tài xế xác nhận, không mô phỏng chuyển động để khẳng định vị trí thực. Đây là nâng cấp tùy chọn, có cấu hình API/billing riêng; không thay đổi bản đầu dùng Embed.
- Số kilomet trên Google Maps là quãng đường dự kiến. Chưa đo kilomet thực tế xe đã chạy. Muốn lấy khoảng cách/thời gian về database cần tích hợp API riêng, không lấy bằng cách đọc iframe.
- Maps Embed cần API key, kích hoạt API và billing; riêng Embed hiện không thu phí sử dụng. Giới hạn key cho website và đúng API. Có nút Maps URL không cần API key làm đường dẫn xem ngoài; đây là hỗ trợ, không thay thế yêu cầu bản đồ nhúng của bản nộp.
- Bản đồ lỗi hoặc mất mạng: vẫn xem được nhà xe, lịch dự kiến, điểm dừng và tiến độ dạng văn bản. Không để lỗi Maps làm hỏng đặt chỗ.

## 10. Database đề xuất

| Bảng | Trách nhiệm và trường chính |
|---|---|
| companies | Nhà xe; name, phone, status. |
| users | Tài khoản; company_id nullable, role, login, password_hash, phone, status. |
| vehicle_types | Loại xe dùng chung. |
| vehicles | company_id, vehicle_type_id, plate_number unique, sellable_capacity, status. |
| provinces | Tỉnh/thành để lọc; có thể gắn vùng Bắc/Trung/Nam phục vụ dữ liệu demo. |
| stops | province_id, name, address, latitude, longitude, trạng thái. |
| routes | company_id, name, status; một bản ghi cho một chiều. |
| route_stops | route_id, stop_id, stop_order, thời gian lệch từ điểm đầu, cumulative_fare, cho phép đón/trả. |
| trips | route_id, vehicle_id, driver_id, departure_at, trạng thái, sale_capacity, thông báo vận hành. |
| trip_stops | Bản sao điểm/tên/địa chỉ/thứ tự/giá; scheduled_arrival_at, scheduled_departure_at, actual_arrival_at, actual_departure_at. |
| trip_segments | trip_id, from_trip_stop_id, to_trip_stop_id, segment_order, capacity, reserved_seats. |
| bookings | customer_id nullable cho đặt PHONE, created_by, source WEB/PHONE, trip_id, pickup_trip_stop_id, dropoff_trip_stop_id, seats, unit_price, total_price, contact_name, contact_phone, status, request_key unique. |
| booking_events | booking_id, actor_id, from_status, to_status, reason, created_at; hỗ trợ thông báo trong lịch sử đơn. |
| trip_events | trip_id, trip_stop_id nullable, actor_id, event_type, note, created_at. |

Nếu ghi nhận thu/hoàn thủ công, bổ sung payment_status, paid_amount và refunded_amount vào bookings; sự kiện thu/hoàn lưu trong booking_events có số tiền đi kèm. Không thêm hệ thống thanh toán trực tuyến vào phạm vi.

Khoảng 14 bảng là mô hình đề xuất cho nhiều nhà xe và đặt theo chặng, thay cho mô hình 6 bảng đơn giản ở trao đổi ban đầu. Không cần cơ sở dữ liệu tách riêng cho từng nhà xe.

Ràng buộc cần có: khóa ngoại; thứ tự điểm/chặng duy nhất trong một tuyến/chuyến; số chỗ dương; 0 <= reserved_seats <= capacity; giá không âm; email/định danh và biển số theo quy tắc duy nhất đã chọn. DAO/service kiểm tra hai điểm cùng chuyến, thứ tự và chủ nhà xe. Chỉ mục phục vụ lọc điểm/tỉnh, ngày tại điểm đón, nhà xe, trạng thái đơn và số thứ tự chặng.

## 11. Màn hình và cấu trúc project

Khách: trang tìm chuyến; kết quả; danh sách/chi tiết nhà xe; chi tiết chuyến + Maps + tiến độ; đăng ký/đăng nhập; form đặt; lịch sử/chi tiết đơn.

Nhà xe: tổng quan; hồ sơ nhà xe; xe; tài xế; tuyến và điểm dừng; chuyến và phân công; yêu cầu đặt; lịch sử đơn và hủy chuyến; báo cáo đơn giản.

Tài xế: chuyến được giao; danh sách đón/trả; màn hình cập nhật đến/rời từng điểm; thông tin liên hệ.

Quản trị: nhà xe/tài khoản; địa điểm/tỉnh/thành/loại xe; tổng quan.

Một Maven project WAR với controller, service, dao, model/dto, config, filter/interceptor; Thymeleaf templates, CSS và JavaScript nằm cùng source. Controller nhận form và trả tên view; service xử lý nghiệp vụ; DAO viết SQL JDBC thuần. Dùng Post/Redirect/Get cho form; kiểm tra đầu vào và phân quyền server; cấu hình kết nối qua file mẫu/biến môi trường. Không đặt thông tin tài khoản thật hoặc khóa dùng thực tế trong Git.

## 12. Chia công việc cho 4 thành viên

Phân công dưới đây được đồng bộ với [PHAN_CONG.md](PHAN_CONG.md) ngày 22/10/2026. Xem [tiến độ và kế hoạch nhóm](TIEN_DO_VA_KE_HOACH_NHOM.md) để phân biệt phần đã có trên main, phần thiếu UI và module chưa triển khai. Bảng này là trách nhiệm dự kiến, không phải ghi nhận đã hoàn thành.

| Thành viên | Phần chính | Kết quả tự trình bày khi bảo vệ |
|---|---|---|
| hieuv526034 | Base/CI/database/auth và scope/cấp tài khoản; tồn chỗ theo chặng; Maps/Geolocation và luồng tài xế; tích hợp transaction và nghiệm thu. | Giải thích phần nền tảng thực đã merge, cơ chế khóa/rollback/scope, tracking thật và giới hạn; không nhận thay phần nhóm tự viết. |
| BangPhamIT | Chuyến/phân xe-tài xế/lịch chồng/snapshot (F06), booking WEB/PHONE/xác nhận-từ chối-hủy (F09–F12/F19), báo cáo phối hợp Hieu (F18). | Vòng đời chuyến/đơn, dữ liệu lịch sử, xử lý lặp và dùng service tồn chỗ trong cùng transaction. |
| anhhoang104 | Hoàn thiện UI danh mục, nhà xe/xe/hồ sơ tài xế (F02/F04, phần loại xe F03); tài khoản/quyền phối hợp auth của Hieu. | CRUD đúng nhà xe, validation/phiên bản/trạng thái, UI và kiểm truy cập chéo; không tự quản lý password hash. |
| hiencoi250404-design | Địa điểm/tuyến/điểm dừng (phần địa điểm F03/F05), tìm và chi tiết chuyến/nhà xe, lịch sử đơn khách (F07/F08/F17). | Tuyến một chiều/giờ/giá, tìm đúng ngày tại điểm đón, UI responsive/vi-en gọi service chung, không tính lại chỗ ở JS. |

F16 hủy chuyến: Hieu và Bang cùng xử lý transaction trip/booking/segments; Anh và Hien phối hợp UI/nghiệm thu theo phạm vi. F01 đăng ký CUSTOMER còn ở AUTH02 của Hieu, phần đăng nhập/đổi mật khẩu/logout đã có nền tảng. F13–F15 do Hieu chủ trì; Hien ghép hiển thị chi tiết và bản đồ theo API có thật.

F19 đặt qua điện thoại: Bang phụ trách, dùng cùng service đặt chỗ/tồn chỗ do Bang và Hieu thống nhất; ghi nguồn đặt/người tạo và cho phép không gắn CUSTOMER.

Mỗi người làm Controller + Service + DAO + Thymeleaf + dữ liệu mẫu + ca nghiệm thu + phần báo cáo của module. Không giao một người chỉ viết báo cáo hoặc chỉ ghép giao diện. Hieu xử lý nền tảng và tích hợp khó nhất; Bang xử lý nghiệp vụ gần khó; Anh và Hien nhận CRUD/UI theo phân công hiện hành. Cả nhóm tự cung cấp nội dung báo cáo và ca kiểm của phần mình; Hieu tổng hợp, không viết lại tác giả commit đã có.

Trước mỗi giai đoạn chốt mã trạng thái, bảng/cột, scope/quyền, Connection trong transaction, thứ tự điểm và công thức giá/chỗ. CAPACITY01 do Hieu cung cấp là cơ chế duy nhất thay tồn chỗ; booking/trip service gọi cơ chế đó cùng transaction khi xác nhận/hủy đơn, rời điểm hoặc hủy chuyến. Không để hai module tự viết hai cách tính tồn chỗ khác nhau.

## 13. Kế hoạch 14 ngày và dữ liệu demo

| Ngày | Mốc đầu ra |
|---|---|
| 1 | Chốt đặc tả và schema; Maven WAR chạy Tomcat; Thymeleaf + JDBC kết nối; thử Maps Embed và key. |
| 2 | Cả 4 máy chạy được source; tài khoản seed; layout; tuyến mẫu, chuyến mẫu và hợp đồng service dùng chung. |
| 3–5 | Các module có luồng chính; sớm hoàn thành tính chỗ theo chặng và transaction xác nhận. |
| 6–7 | Demo xuyên suốt: nhà xe → tuyến hai chiều → chuyến → tìm theo điểm trung gian → xác nhận đơn → cập nhật chặng. |
| 8–10 | Tích hợp hủy đơn/hủy chuyến, chặn đặt điểm đã qua, trùng lịch, qua ngày, phân quyền và nhiều nhà xe. |
| 11–12 | Nghiệm thu lỗi/concurrency; báo cáo đơn giản; dữ liệu demo có phân trang. |
| 13–14 | Sửa lỗi còn lại, hướng dẫn cài đặt, backup SQL, báo cáo/slide và diễn tập bảo vệ; không mở rộng chức năng. |

Đề xuất seed có thể sinh lặp: 20 nhà xe demo, 80 xe, ít nhất 40 tuyến một chiều gồm nhiều cặp chiều đi/về và tuyến Bắc–Nam/Nam–Bắc, 200 chuyến trên nhiều ngày, 100 đơn có trạng thái khác nhau. Có đủ điểm dừng trung gian, chuyến qua đêm, hết chỗ ở một chặng, chuyến hủy và chuyến đang chạy. Các số này là mục tiêu dữ liệu demo, không phải dữ liệu thật đã thu thập hoặc đã có sẵn.

Tên nhà xe, lịch và giá demo phải được phân biệt với thông tin kinh doanh chính thức. Không tự công bố lịch/giá giả cho nhà xe thật. Không cần nhập tay mọi nhà xe tại Việt Nam; chức năng phải hỗ trợ thêm dữ liệu từ hệ thống quản lý.

## 14. Bộ ca nghiệm thu bắt buộc

| Mã | Tình huống | Kết quả mong đợi |
|---|---|---|
| T01 | Hai nhà xe cùng có tuyến Hải Phòng → Hà Nội. | Tìm thấy cả hai; quản lý chỉ sửa dữ liệu nhà xe mình. |
| T02 | Chọn tuyến chiều về có các điểm khác chiều đi. | Hiển thị đúng thứ tự và giá chiều về, không tự đảo dữ liệu chuyến đi. |
| T03 | Chuyến qua nửa đêm, khách lên tại điểm ngày hôm sau. | Tìm theo ngày lên xe vẫn thấy chuyến; giờ và ngày không bị lệch. |
| T04 | Xe 2 chỗ; đơn A→B lấy 2 chỗ, đơn B→C lấy 2 chỗ. | Cả hai được xác nhận vì không chung chặng. |
| T05 | Chặng B→C hết chỗ; khách tìm A→C. | Không báo còn chỗ chỉ vì A→B còn; không xác nhận vượt chỗ. |
| T06 | Hai người cùng xác nhận đơn lấy chỗ cuối của một chặng. | Chỉ một giao dịch thành công; dữ liệu còn lại nhất quán. |
| T07 | Lỗi SQL sau bước thay tồn chỗ nhưng trước khi đổi trạng thái đơn. | Rollback toàn bộ; đơn và chỗ không lệch nhau. |
| T08 | Gửi form, xác nhận hoặc hủy lại cùng yêu cầu. | Không tạo trùng đơn, không trừ/trả chỗ hai lần. |
| T09 | Khách chọn hai điểm ngược thứ tự hoặc khác chuyến. | Từ chối ở server dù sửa form thủ công. |
| T10 | Tài xế rời điểm đón trong lúc nhà xe xác nhận đơn. | Transaction tuần tự; không xác nhận sau khi điểm đã đóng. |
| T11 | Truy cập ID của đơn/tuyến/chuyến thuộc nhà xe khác. | Bị từ chối; không lộ dữ liệu cá nhân. |
| T12 | Tài xế không được phân công bấm cập nhật chuyến. | Bị từ chối; tài xế đúng có thể cập nhật theo thứ tự. |
| T13 | Sửa mẫu tuyến sau khi đã tạo chuyến và đơn. | Chuyến/đơn cũ giữ đúng dữ liệu đã chốt. |
| T14 | Hai chuyến cùng phân một xe hoặc tài xế với khoảng thời gian chồng nhau. | Không cho mở nhận đặt; kiểm tra cả chuyến kéo dài qua ngày. |
| T15 | Hủy chuyến đồng thời với xác nhận đơn. | Không có đơn được xác nhận lọt vào chuyến đã hủy; lịch sử đầy đủ. |
| T16 | Maps lỗi, key chưa cấu hình hoặc tuyến vượt 20 điểm trung gian. | Có thông báo, danh sách chặng vẫn đọc được; tuyến dài chia đoạn bản đồ. |
| T17 | Bấm hoàn thành, cập nhật chặng hoặc hủy khi trạng thái không cho phép. | Không đi lùi trạng thái, không sửa lại tồn chỗ lịch sử. |
| T18 | Xem báo cáo nhà xe và báo cáo tổng. | Phạm vi đúng; phân biệt giá trị đặt chỗ và số tiền thực thu nếu có ghi nhận. |
| T19 | Khách gửi yêu cầu PENDING khi sắp hết chỗ. | Giao diện không cam kết giữ chỗ; xác nhận phải kiểm tra lại chỗ. |
| T20 | Khách hủy đơn đã xác nhận trước giờ xe rời điểm đón. | Trả đúng các chặng, giữ lịch sử; hủy sau thời điểm cho phép bị từ chối. |
| T21 | Khách gọi điện đặt mà không có tài khoản website. | Nhà xe tạo đơn PHONE và xác nhận bằng cùng tồn chỗ; khách tìm sau đó thấy chỗ đã giảm đúng. |

Đây là ca nghiệm thu cần thực hiện khi đã có code, chưa phải kết quả kiểm thử đã chạy. Phải kiểm tra bằng ít nhất hai phiên người dùng riêng cho các ca tranh chấp và phân quyền.

## 15. Giới hạn để giữ đúng thời hạn

Bắt buộc: nhiều nhà xe, tuyến hai chiều và đường dài qua ngày, tìm điểm trung gian, đặt/xác nhận/hủy theo chặng, tiến độ tài xế bấm, Google Maps, quyền và lịch sử, JDBC thuần trong một source.

Để sau: GPS liên tục, app di động riêng, tự ghép hành khách theo vị trí ngoài điểm dừng, tự tối ưu tuyến, chọn số ghế/giường, chuyển xe tự động khi sự cố, thanh toán online, SMS/email, voucher, đánh giá và import dữ liệu thật từ các nhà xe.

Phạm vi này rộng hơn đề xuất 6 bảng ban đầu và có rủi ro tiến độ với nhóm mới học. Quy mô nhiều nhà xe được giải quyết bằng mô hình dữ liệu dùng chung; ưu tiên hoàn thành vòng đời và kiểm tra đúng chặng trước khi làm giao diện cầu kỳ.

## 16. Nguồn kỹ thuật đã đối chiếu

- Spring MVC tích hợp Thymeleaf: https://docs.spring.io/spring-framework/reference/web/webmvc-view/mvc-thymeleaf.html
- JDBC transaction, commit/rollback: https://docs.oracle.com/javase/tutorial/jdbc/basics/transactions.html
- Maps Embed directions và giới hạn waypoint: https://developers.google.com/maps/documentation/embed/embedding-map
- Maps Embed yêu cầu thiết lập và billing: https://developers.google.com/maps/documentation/embed/quickstart
- Maps URL mở ngoài, không cần API key: https://developers.google.com/maps/documentation/urls/get-started

Thông tin Google Maps được đối chiếu ngày 21/10/2026. Cần kiểm tra lại cấu hình dịch vụ khi triển khai thực tế.

## 17. Cập nhật phương án tracking theo phản hồi mới nhất

Người dùng chỉ làm website và lưu ý tài xế không thể bấm cập nhật liên tục khi đang lái. Do đó, hướng triển khai đề xuất mới là **chia sẻ vị trí tự động từ trình duyệt trên thiết bị ở trên xe**, bật trước khi khởi hành. Các đoạn mô tả bấm từng chặng ở trên là phương án thủ công/dự phòng; không coi bấm từng chặng là cách duy nhất để theo dõi.

- Tài xế chọn chuyến, cho phép vị trí và bật chia sẻ trước khi chạy; dùng Geolocation watchPosition khi trang đang hiển thị/hoạt động.
- JavaScript gửi mẫu tọa độ mới về Spring MVC; JDBC lưu trip_id, nguồn, latitude, longitude, accuracy, thời điểm thiết bị đo và thời điểm server nhận. Chỉ tài xế được phân công mới gửi được cho chuyến đó.
- Có thể thêm bảng trip_locations khi triển khai module này; đây là phần bổ sung cho mô hình 14 bảng ban đầu.
- Trang khách lấy điểm mới nhất và cập nhật marker bằng Maps JavaScript API. Lưu chuỗi điểm để vẽ vệt đường đã ghi nhận; iframe Embed của bản đơn giản không đáp ứng việc tự điều khiển marker xe.
- Phân biệt lộ trình dự kiến, các điểm tọa độ thực nhận và tiến độ chặng. Đi gần điểm dừng không chứng minh đã dừng/đón khách; không tự hủy đơn hoặc kết luận hoàn thành phục vụ chỉ từ một mẫu vị trí thiếu tin cậy.
- HTTPS và quyền vị trí là điều kiện để thử trên điện thoại. Trình duyệt có thể ngừng vị trí khi khóa màn hình/chuyển nền. Không cam kết theo dõi liên tục toàn hành trình bằng phương án này.
- Hiển thị thời điểm nhận cuối và trạng thái chưa có dữ liệu/mất cập nhật. Không cho xe tự chạy theo lịch để giả làm vị trí thật.
- Dữ liệu mô phỏng dùng khi demo phải được gắn nhãn; kiểm tra thêm vị trí thật trên thiết bị khi trang đang hoạt động.
- Nếu cần vận hành liên tục ngoài điều kiện trình duyệt, tiếp nhận dữ liệu từ thiết bị GPS trên xe hoặc API định vị được nhà xe cho phép; khách vẫn dùng website. Chưa có quyền truy cập hay tích hợp nguồn bên ngoài trong source hiện tại.

Các điều kiện nghiệm thu bổ sung: người dùng từ chối vị trí; thiết bị không hỗ trợ; khóa màn hình; mất mạng; nhận tọa độ cũ/sai thứ tự; tài xế gửi cho chuyến không thuộc mình; marker và thời gian cập nhật nhất quán; dừng nhận khi kết thúc chuyến. Người phụ trách tracking thử sớm trong 1–2 ngày đầu vì đây là phần có rủi ro tiến độ.

Nguồn: https://www.w3.org/TR/geolocation/ và https://developers.google.com/maps/documentation/javascript/examples/map-geolocation/
