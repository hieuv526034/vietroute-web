# Kịch bản demo và hỏi–đáp bảo vệ VietRoute

Học theo [phần từng thành viên](PHAN_CONG.md), [tổng quan](../01-bat-dau/TONG_QUAN_HE_THONG.md) và source được chỉ ở cuối mỗi nhóm câu hỏi. Câu trả lời dưới đây là mẫu nói ngắn; người trình bày phải chỉ ra được một màn hình, một method/query/test và giới hạn thật. Không học thuộc để nhận chức năng hoặc đóng góp chưa có.

Đọc nhanh: [Lời thoại hai dev](#lời-thoại-trình-bày-của-hai-dev) → [chuẩn bị luồng](#chuẩn-bị-một-luồng-xuyên-suốt) → [hỏi sâu Q46–Q55](#phản-biện-theo-phần-của-hai-dev--q46-đến-q55). Q01–Q45 là bộ nền; phần cuối vẫn giữ yêu cầu báo cáo Word.

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
| 0–2 | anhhoang104 | Trình bày tổng quan/phạm vi/tác nhân, bộ demo đa nhà xe và tài liệu hướng dẫn đã đối chiếu; nêu đúng vai trò tài liệu |
| 2–4 | hieuv526034 | Nền auth/common/DB: username, grant endpoint, scope/owner, Swagger quản trị và môi trường/commit đang chạy |
| 4–8 | BangPhamIT | Từ danh mục/địa điểm/xe–tài xế đến tuyến/chuyến hợp lệ; khách đặt WEB PENDING, điều hành xác nhận/giữ chỗ và ghi thu trên DB demo |
| 8–10 | BangPhamIT | Tài xế đúng assignment START → ARRIVE → DEPART điểm giữa → ARRIVE cuối → COMPLETE. Mốc server, không GPS |
| 10–12 | hieuv526034 + hiencoi250404-design | Hieu trình bày report/dashboard; Hien đối soát ca kiểm với đơn cùng ngày/scope, tách giá trị đặt/thu/hoàn và lượt chỗ |
| 12–14 | hiencoi250404-design + hieuv526034 | Hien trình bày bảng test thủ công/ảnh chức năng và kết quả kiểm lại; Hieu giải thích guard/trace ở source. Không nhận ca chưa chạy là đạt |
| 14–15 | anhhoang104 + cả nhóm | Chốt tài liệu/test đúng commit, giới hạn và hướng phát triển; hai dev xác nhận phần kỹ thuật, cả bốn trả lời câu hỏi |

Đối chiếu sau mỗi bước: PENDING chưa giữ chỗ → CONFIRMED giữ mọi đoạn → thu không quá total → hủy đủ điều kiện trả chỗ → hoàn riêng không vượt đã thu. Mốc ARRIVE đến điểm trả hoàn tất các đơn CONFIRMED phù hợp; START/DEPART có thể từ chối đơn PENDING đã lỡ điểm đón. Không chạy tất cả nhánh trên cùng một đơn rồi kỳ vọng quay lại trạng thái trước.

## Lời thoại trình bày của hai dev

Phần này là kịch bản có thể tập nói trực tiếp, không phải biên bản đã demo thành công. Dùng khoảng15–18 phút cho hai dev; nếu cả nhóm chỉ có15 phút thì rút từng bước theo chỉ dẫn “Bản ngắn”. Anh mở đầu/tổng hợp Word, Hien trình bày ảnh và kết quả test thủ công theo phân công; hai dev thực hiện luồng dưới đây. Phần hỏi–đáp dùng sau demo, không đọc hết trên sân khấu.

### Chuẩn bị một luồng xuyên suốt

| Việc cần chuẩn bị trước | Điều kiện chốt |
|---|---|
| Môi trường | Local/demo được phép ghi, UI tiếng Việt, phiên bản/source đã kiểm; Render là phương án đọc khi phản hồi tốt, không phụ thuộc mạng để hoàn thành thao tác ghi |
| Phiên đăng nhập | Profile trình duyệt riêng cho admin, operator01, custom01 và driver01; không dùng các tab chung cookie để giả nhiều người |
| Dữ liệu chính | Thiên Hành: operator01, driver01/Quách Tĩnh, custom01; An Phúc: operator02 để đối chiếu scope. Khách custom01 có thể tìm/đặt nhà xe khác, không bị giới hạn theo tên nhóm account |
| Chuyến chính | Một chuyến Thiên Hành OPEN, phân công đúng driver01, ba điểm A→B→C, giờ đi khoảng10 phút tới, còn ít nhất2 chỗ trên cả AB/BC; xe/tài xế không trùng lịch. Đánh dấu ID/code, ngày và điểm đón/trả thực tế đã chọn |
| Khách | custom01 không còn WEB PENDING/CONFIRMED từ lần tập trước; xử lý hợp lệ trên DB demo hoặc chuẩn bị account khác, không sửa SQL/reset để lách điều kiện |
| Giá minh họa | Nếu chuẩn bị chặng A→C giá180.000đ/chỗ thì2 chỗ thành360.000đ. Nếu dùng tuyến khác, đọc giá server và thay các con số trong lời nói theo dữ liệu thực trên màn |
| Hai nhánh | Đơn A: xác nhận/thu rồi đi theo tiến độ đến hoàn tất. Đơn B riêng: xác nhận/thu/hủy/hoàn trước khi rời điểm đón; không dùng lại đơn A cho cả hai nhánh |
| Báo cáo | Ghi số liệu nền, cùng nhà xe/ngày khởi hành Việt Nam với chuyến chính. Đối chiếu phần tăng/giảm sau thao tác, không kỳ vọng toàn báo cáo chỉ có một đơn |
| Tư liệu dự phòng | Ảnh chức năng và sơ đồ đúng phiên bản do Anh/Hien chuẩn bị; ghi rõ ảnh minh họa khi dùng, không gọi là thao tác Live vừa thành công |

Không đọc mật khẩu thành tiếng hoặc chiếu .env. Lấy mật khẩu từ cấu hình trên máy cá nhân theo hướng dẫn local. Nếu chưa chuẩn bị được chuyến ba điểm, dùng lịch sử một chuyến nhiều chặng để giải thích AB/BC, rồi demo tiến độ trên chuyến hai điểm hợp lệ; khi đó bỏ bước DEPART điểm giữa. Các thao tác bên dưới có thể đổi nhãn theo locale nhưng dùng đúng chức năng hiện hành.

### Hieu — mở bài, kiến trúc và quyền (khoảng4 phút)

**H1 — Mở Home và sơ đồ hệ thống.**

> Em chào thầy/cô. Nhóm em xây dựng VietRoute để quản lý nhà xe, tuyến, chuyến và đặt chỗ theo chặng. Sản phẩm có bốn tác nhân: quản trị viên, điều hành nhà xe, tài xế và khách hàng. Trong phần này em trình bày nền xác thực, phân quyền và cách tổ chức hệ thống; bạn Bang sẽ đi một luồng vận hành từ dữ liệu nguồn đến chuyến và đơn, sau đó em đối chiếu bằng báo cáo/dashboard.
>
> Ứng dụng dùng Java, Spring MVC, Spring Security và Thymeleaf, đóng gói WAR chạy trên Tomcat. Dự án không dùng Spring Boot. Dữ liệu hiện chạy bằng PostgreSQL; CRUD dùng JPA, truy vấn tổng hợp và khóa giữ chỗ dùng native query Spring Data. JDBC ghi batch trong cùng transaction. Controller nhận và kiểm dữ liệu, service xử lý nghiệp vụ, repo làm việc với DB; API trả DTO riêng.

Chỉ sơ đồ Browser → Controller → Service → Repo → PostgreSQL, rồi chỉ RootConfig/WebConfig và một BookingAPI/BookingService/BookingRepo nếu được hỏi. Không mở hàng loạt file hoặc đọc tên công nghệ quá lâu. **Bản ngắn:** giới thiệu bài toán/bốn tác nhân, nói ba lớp và chuyển H2.

**H2 — Đăng nhập admin, mở menu tài khoản và phân quyền.**

> Người dùng đăng nhập bằng username và password. Mật khẩu được lưu dưới dạng BCrypt hash; JWT có thời hạn và được kiểm cùng trạng thái tài khoản hiện hành. Quyền đi theo User → Role → Function → Menu/API. Menu giúp điều hướng; quyền endpoint mới quyết định một request có được thực hiện hay không. Danh sách endpoint được thu thập từ Spring MVC, nhưng endpoint mới chưa được gán thì mặc định bị chặn.
>
> Ở đây em mở tài khoản, role và chức năng đã gán. Chúng em không viết enum cố định cho từng quyền nghiệp vụ. Sau quyền chức năng còn có phạm vi dữ liệu nhà xe. Vì vậy hai điều hành có thể có cùng chức năng nhưng nhìn thấy dữ liệu khác nhau.

Mở các tab sẵn có tại /operations/accounts và /operations/access; chọn một role/function có sẵn để chỉ liên kết, không gán/xóa quyền ngẫu hứng. Có thể mở inventory /operations/access/resources để chỉ method + route template. **Bản ngắn:** chỉ một role, một function, một target endpoint.

**H3 — Chứng minh quyền bằng hai phiên.**

> Em dùng admin mở Tài liệu API. Đích này thuộc bộ grant quản trị. Sau đó em chuyển sang phiên operator01 và nhập trực tiếp cùng đường dẫn /docs; ứng dụng phải chặn quyền, không chỉ ẩn menu. Tiếp theo em mở danh sách nghiệp vụ: operator01 thuộc Thiên Hành, operator02 thuộc An Phúc. Bộ lọc giao diện không tự cấp quyền; server vẫn kiểm scope khi đọc hoặc cập nhật ID.
>
> Với khách hàng, giới hạn là đơn của chính mình. Với tài xế, giới hạn là chuyến được phân công cho account. Đây là ba quan hệ khác nhau: scope nhà xe, owner đơn và assignment tài xế.

Kết quả mong đợi: admin /docs đọc được, operator /docs bị403; hai danh sách điều hành đúng scope. Chỉ dùng ID hai nhà xe đã chuẩn bị trên DB demo nếu cần chứng minh ngoài scope; không dò ID. Nếu quyền thực tế không khớp thì dừng và ghi lỗi, không gọi mọi403 là “đúng bảo mật”. **Bản ngắn:** chỉ admin/operator với /docs.

**H4 — Chuyển lời.**

> Nền quyền bảo đảm đúng người được thao tác đúng chức năng và dữ liệu. Tiếp theo bạn Bang sẽ dùng chính các vai trò này để trình bày luồng tạo chuyến, tìm chuyến, đặt và xác nhận chỗ.

### Bang — dữ liệu nguồn, chuyến và đặt chỗ (khoảng7–9 phút)

**B1 — Mở danh mục/địa điểm và nhà xe/xe/tài xế.**

> Luồng nghiệp vụ bắt đầu từ dữ liệu nguồn: tỉnh, địa điểm, loại xe, nhà xe, xe và tài xế. Dữ liệu có danh mục được chọn từ API thay vì yêu cầu người dùng nhớ ID. Chọn tỉnh thì danh sách địa điểm phụ thuộc thay đổi; chọn nhà xe thì xe, tài xế và tuyến phải thuộc nhà xe đó. Backend kiểm lại các quan hệ này khi lưu.
>
> Bộ dữ liệu có nhiều nhà xe. Thiên Hành là đơn vị em dùng cho luồng chính; Duy Long là một trường hợp khác có xe16 chỗ và bốn lượt Hải Phòng–Hà Nội mỗi ngày. Tên nhà xe và phần lớn dữ liệu là giả lập để học/nghiệm thu, không phải lịch khai thác thương mại đã xác minh. Tên tài xế Quách Tĩnh cũng là tên nhân vật giả lập.

Mở modal xe/tài xế có sẵn để chỉ field và quan hệ; không cần tạo đủ mọi danh mục tại sân khấu. Duy Long giữ Phạm Thành Lương; không chuyển driver01 sang Duy Long để tiện demo. **Bản ngắn:** chỉ quan hệ nhà xe → xe/tài xế, bỏ ví dụ Duy Long.

**B2 — Mở /operations/routes và /operations/trips.**

> Tuyến là mẫu một chiều gồm các điểm có thứ tự, thời gian tương đối và giá cộng dồn. Chuyến là một lần chạy cụ thể có ngày giờ, xe, tài xế và sức chứa. Khi tạo chuyến, hệ thống lưu snapshot các điểm, giá và lịch để việc sửa tuyến mẫu sau này không thay đổi lịch sử chuyến đã tạo.
>
> Em mở chuyến đã chuẩn bị và chỉ tuyến, giờ khởi hành, xe và tài xế được phân công. Khi mở chuyến, service kiểm nguồn còn hợp lệ và lịch xe/tài xế không bị chồng. Phân công cùng tên nhà xe chưa đủ; chuyến phải gắn đúng account tài xế.

Chỉ chuyến chính OPEN và các điểm đón/trả. Nếu muốn demo tạo mới, chuẩn bị trước mẫu tuyến và xe/tài xế rảnh, tạo DRAFT rồi mở hợp lệ trên DB demo; không chỉnh sửa một chuyến đã có đơn chỉ để minh họa CRUD. **Bản ngắn:** xem chuyến đã chuẩn bị, không tạo mới.

**B3 — Chuyển phiên khách custom01, mở /journeys.**

> Khách chọn nơi đi, nơi đến, ngày đón và số chỗ. Các danh mục được tải từ API; các ô phụ thuộc cập nhật theo lựa chọn trước. Chỉ một nút tìm áp tiêu chí vào bảng kết quả. Phân trang thuộc bảng kết quả, không nằm bên cạnh từng ô chọn.
>
> Em tìm chặng A đến C của chuyến vừa giới thiệu. Giá và chỗ còn do server tính. Nếu chặng này180.000đ/chỗ thì hai chỗ thành360.000đ. Tồn chỗ theo chặng là chỗ ít nhất còn lại trên các đoạn mà khách đi qua, vì vậy chặng khác nhau trên cùng chuyến có thể còn số chỗ khác nhau.

Chọn đúng tỉnh/địa điểm thực tế ứng với A/C trong phiếu chuẩn bị; đọc lại ngày và chiều trước bấm tìm. Mở kết quả đúng nhà xe/chuyến, không chọn chuyến đầu tiên bất kỳ. **Bản ngắn:** điền sẵn danh mục, chỉ bấm Tìm và giải thích một kết quả.

**B4 — Tạo yêu cầu WEB, xem /my-bookings.**

> Em chọn chuyến và tạo yêu cầu đặt hai chỗ. Kết quả ban đầu là PENDING, nghĩa là đã tiếp nhận yêu cầu nhưng chưa giữ chỗ. Khách chỉ xem được đơn của chính mình, và bản hiện hành giới hạn mỗi khách một đơn WEB đang PENDING hoặc CONFIRMED.
>
> Trạng thái này giúp phân biệt yêu cầu và chỗ đã được điều hành xác nhận. Kết quả tìm kiếm cũng không cam kết chỗ còn nguyên đến lúc xác nhận. Giá, owner và audit được xác định lại ở server, không tin tổng tiền hoặc userId do trình duyệt tự gửi.

Ghi mã đơn A, kiểm số chỗ/tổng/trạng thái. Nếu trước đó khách còn đơn active, dùng nhánh đã chuẩn bị; không bấm gửi nhiều lần. **Bản ngắn:** mở đơn PENDING đã chuẩn bị và nói rõ đang xem dữ liệu có sẵn.

**B5 — Chuyển operator01, mở /operations/bookings và xác nhận.**

> Điều hành mở đúng đơn và xác nhận. Trong một transaction, ứng dụng khóa/kiểm lại chuyến, đơn và các segment liên quan. Chỉ khi mọi đoạn còn đủ chỗ thì đơn mới chuyển CONFIRMED và giữ đủ hai chỗ trên từng đoạn. Nếu hai request cùng tranh chỗ cuối, chúng không được cùng xác nhận thành công.
>
> Ví dụ trước khi xác nhận AB còn5, BC còn3 thì A đến C nhận tối đa3. Xác nhận hai chỗ làm AB còn3 và BC còn1. Nếu một đoạn không đủ, cả thao tác phải rollback; không có tình trạng đơn xác nhận nhưng thiếu chỗ trên một đoạn.

So sánh tồn trước/sau bằng số thực tế đã ghi. Ví dụ5/3 chỉ để minh họa nếu dữ liệu không đúng các số đó; không nói thành kết quả vừa quan sát. **Bản ngắn:** xác nhận một đơn, chỉ trạng thái và số chỗ thay đổi.

**B6 — Ghi thu và giải thích nhánh hủy/hoàn.**

> Đây là ghi nhận thu tiền thủ công, chưa phải thanh toán ngân hàng. Với đơn360.000đ đã xác nhận, em ghi thu200.000đ rồi chỉ số đã thu và phần còn lại. Mỗi thao tác thu/hoàn có requestKey để lần gửi lặp cùng nội dung không cộng tiền thêm lần nữa; thao tác mới còn kiểm version hiện hành.
>
> Hủy đơn và hoàn tiền là hai việc riêng. Trên đơn B đã chuẩn bị, hủy đủ điều kiện sẽ trả chỗ; số tiền đã thu vẫn còn và cần ghi hoàn riêng. Ví dụ thu200.000đ, hoàn50.000đ thì thực thu ròng còn150.000đ và khoản phải hoàn của đơn đã hủy là150.000đ. Hệ thống không tự chuyển tiền cho khách khi bấm Hủy.

Đơn A giữ để chạy tiến độ; nhánh B dùng đơn khác trước khi điểm đón đã rời. Bản ngắn chỉ mở lịch sử B, bỏ thao tác hủy/hoàn; tuyệt đối không thu lại nếu chưa biết request trước có thành công hay không. Lấy snapshot báo cáo sau nhánh tiền và trước bước tài xế để việc chuyển CONFIRMED→COMPLETED không làm khó đối chiếu.

**B7 — Chuyển driver01, mở /driver/trips.**

> Tài xế chỉ thấy chuyến được phân công cho mình. Ở đây driver01 là Quách Tĩnh và được giao đúng chuyến chính. Em mở danh sách đón/trả, rồi thực hiện START, ARRIVE điểm giữa, DEPART điểm giữa, ARRIVE điểm cuối và COMPLETE.
>
> Thứ tự được kiểm trên server, kèm version và thời điểm server. START không được sớm hơn30 phút trước lịch; không thể bấm hoàn tất một điểm cuối chưa đến. Đây là xác nhận tiến độ thủ công, không phải bản đồ GPS hoặc bằng chứng tọa độ thực. Khi đến điểm trả, các đơn đã xác nhận phù hợp được hoàn tất theo luồng.

Chỉ bấm bước được phép; nhánh hai điểm dùng START → ARRIVE cuối → COMPLETE. Nếu chưa vào cửa sổ START thì xem lịch sử chuyến hoàn tất hoặc chuyển sang chuyến dự phòng hợp lệ, không sửa clock. **Bản ngắn:** chỉ một thao tác START hợp lệ và lịch sử tiến độ đã có, nói rõ phần nào là dữ liệu lịch sử.

**B8 — Chuyển lời bằng ID và số liệu.**

> Em vừa đi từ dữ liệu nguồn đến chuyến, yêu cầu đặt, xác nhận chỗ, thu tiền và tiến độ tài xế. Các thay đổi đều thuộc cùng nhà xe và được giữ lịch sử. Em bàn giao mã chuyến, mã đơn và ngày khởi hành này để bạn Hieu đối chiếu trên dashboard và báo cáo.

### Hieu — dashboard, báo cáo và kết bài (khoảng3–4 phút)

**H5 — Mở /dashboard rồi /operations/reports bằng actor có quyền.**

> Dashboard là điểm xem tổng quan theo capability của người đang đăng nhập. Báo cáo dùng dữ liệu nghiệp vụ vừa thao tác, không có bộ bảng báo cáo riêng để nhập tay. Em chọn cùng nhà xe và khoảng ngày có chuyến vừa demo, rồi bấm xem báo cáo. Khi đổi sang điều hành nhà xe khác, dữ liệu vẫn phải tuân theo scope hiện hành.
>
> Bộ lọc ngày của bản hiện tại dựa vào ngày khởi hành chuyến theo múi giờ Việt Nam. Vì vậy đây không phải sổ tiền theo ngày chuyển khoản. Ví dụ hôm nay thu cho một chuyến khởi hành ngày mai thì khoản đó thuộc tập chuyến ngày mai khi xem báo cáo này.

Chỉ tab/số liệu hiện có trên phiên đã kiểm; không hứa biểu đồ dự báo, xuất Excel hoặc cập nhật thời gian thực nếu không có chức năng đó. Đối chiếu asOf và tải lại khi dữ liệu đổi. **Bản ngắn:** mở thẳng báo cáo và chỉ nhà xe/ngày.

**H6 — Đối chiếu số và giải thích query.**

> Ba khái niệm dễ nhầm là giá trị đơn, tiền đã thu và tiền ròng. Với đơn A360.000đ đang CONFIRMED hoặc đã COMPLETED, giá trị được tính là360.000đ; nếu mới thu200.000đ thì tiền đã thu chỉ200.000đ. Riêng đơn B đã hủy, thu200.000đ và hoàn50.000đ, số ròng còn150.000đ và còn phải hoàn150.000đ. Đơn hủy không được cộng vào giá trị các đơn xác nhận/hoàn tất.
>
> Nếu chỉ xét hai đơn này thì giá trị xác nhận/hoàn tất là360.000đ, tổng thu400.000đ, tổng hoàn50.000đ, tiền ròng350.000đ và phải hoàn150.000đ. Trên DB có dữ liệu nền, em đối chiếu phần chênh lệch với số trước thao tác thay vì nói toàn bộ báo cáo bằng hai đơn này.
>
> Ở repo, tập chuyến được lọc theo scope và ngày, sau đó tổng chuyến và tổng đơn được gom riêng trong cùng một statement. Cách này tránh một chuyến có nhiều đơn bị đếm thành nhiều chuyến. Lượt chỗ đã xác nhận là tổng số chỗ theo đơn, không phải số ghế vật lý duy nhất của cả đội xe.

Mở ReportRepo.summary khi được hỏi cách tính, không đọc cả SQL. Đối chiếu từng field với đơn A/B; nếu nhánh tiền bị rút gọn thì dùng ví dụ tính trên giấy/ảnh đã ghi rõ minh họa. **Bản ngắn:** nói giá trị khác tiền thu, chỉ một tổng đối chiếu được.

**H7 — Nhật ký, chất lượng và kết bài.**

> Khi cần kiểm tra request, tài khoản có grant nhật ký có thể xem endpoint, trạng thái, thời gian và traceId cùng dữ liệu đã lọc. Journal không lưu nguyên password/JWT hoặc mọi payload, và có thể bỏ lỡ bản ghi khi pool/DB log lỗi. Lịch sử trạng thái và tiền của nghiệp vụ thì được ghi cùng transaction, lỗi lịch sử phải rollback thao tác liên quan.
>
> Nhóm kiểm lớp portable bằng H2, còn SQL và khóa/transaction bằng PostgreSQL test riêng; phần giao diện có ca kiểm tương ứng. Khi báo cáo kết quả, chúng em nêu đúng commit và số ca thực chạy, không coi CI xanh là bằng chứng chịu tải production. Hai bạn tài liệu tổng hợp Word, sơ đồ, ảnh chức năng và kết quả test thủ công.
>
> Sản phẩm hiện bao quát quản lý dữ liệu nguồn, tuyến/chuyến, đặt chỗ theo chặng, xác nhận chỗ, ghi thu–hoàn và báo cáo theo quyền. Giới hạn hiện tại là chưa có payment gateway, GPS/mobile hoặc runtime Oracle đã kiểm. Dùng thực tế còn cần đánh giá tải, bảo vệ kết nối DB và quy trình backup/phục hồi. Em xin kết thúc phần demo và sẵn sàng nhận câu hỏi.

Nếu hết giờ, bỏ mở journal và nói kết bài trong20 giây. Không trình chiếu .env, hash, token, dump DB hoặc credential trong Swagger/DevTools. Phần dư thời gian dùng một request trace đã chuẩn bị, không tạo lỗi hoặc gọi lại mutation để “có log”.

### Bản rút gọn và câu trả lời khi bị ngắt

| Thời lượng hai dev được giao | Chọn bước |
|---|---|
| 8–10 phút | H1 ngắn/H2/H3; B2 xem có sẵn/B3/B4 có sẵn/B5; H5/H6/H7 kết bài. B6/B7 chỉ giải thích bằng lịch sử/ảnh, nói rõ không thao tác mới |
| 12–15 phút | H1–H4; B1/B2 ngắn, B3–B5 thật, B6 một lần thu, B7 một mốc; H5–H7. Nhánh hủy/hoàn xem đơn B sẵn có |
| 15–18 phút | Toàn luồng; vẫn chuẩn bị trước dữ liệu nguồn/chuyến và giới hạn thao tác để còn thời gian hỏi–đáp |

Khi bị hỏi giữa thao tác, nói: “Em xin trả lời ngắn phần nguyên tắc, sau đó quay lại đúng mã đơn đang demo.” Ghi lại mã đơn/trạng thái; không chuyển nhiều role rồi quên phiên. Khi câu hỏi cần source: “Em mở method liên quan để chỉ điều kiện kiểm; phần em chưa kiểm thực tế em sẽ nêu rõ.” Nếu mất mạng: “Phiên Render đang chưa phản hồi, em chuyển sang môi trường local đã chuẩn bị; em ghi rõ đây là môi trường khác.”

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

CRUD entity dùng JPA; query viết tay, tổng hợp, giữ chỗ/khóa/CAS dùng native @Query của Spring Data. JDBC nghiệp vụ chỉ ghi batch/bulk trong CustomRepo. Hai cách cùng JpaTransactionManager/connection qua proxy nên một thao tác không bị JPA commit mà JDBC rollback riêng. JDBC không đồng nghĩa bỏ entity hoặc nhét SQL vào service.

### Q05. OOP và phân lớp thể hiện ở đâu?

Entity mô tả dữ liệu/ràng buộc, Request/DTO là hợp đồng riêng; Service đóng gói quy tắc trạng thái/transaction; Repo che cách truy cập DB, controller điều phối HTTP. Kế thừa có mục đích ở BaseController/BaseDTO/JpaRepositorySupport; constructor injection giúp thay dependency trong test. Không tạo lớp rỗng để minh họa OOP; một Request/tài nguyên vẫn validation theo operation.

### Q06. Vì sao source có 22 bảng sau khi gộp danh mục?

Auth có 8 bảng, nghiệp vụ có 12 bảng, journal và migration history mỗi phần có một bảng. `category` gom các nhóm danh mục và nối tỉnh với xã qua `parent_id`. `trip_segments` vẫn giữ tồn chỗ của từng đoạn để hai khách có thể dùng lại cùng chỗ trên hai đoạn không giao nhau. Báo cáo đọc từ các bảng nghiệp vụ.

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

Scope giới hạn nhà xe, owner giới hạn đơn của khách, driver assignment giới hạn chuyến gắn đúng account tài xế. operator01 không được đọc ID nhà xe An Phúc; custom01 không đọc đơn custom02; driver01 không được lấy chuyến driver02 chỉ vì biết ID. Thường404 khi ngoài dữ liệu được thấy,403 khi thiếu quyền endpoint.

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

Tra cứu/CRUD bind named parameter JPA; batch bind PreparedStatement. Identifier/sort/fragment chỉ whitelist phía server; SQL đặt trong Repo. Validation Request không thay bind parameter hoặc scope SQL. Count và page cùng bộ lọc, size tối đa 100; không lấy toàn bảng xuống JavaScript rồi lọc quyền. JSONB/array nhận dữ liệu bind, không ghép chuỗi SQL từ client.

### Q23. Tồn chỗ và giá theo chặng tính thế nào?

Tuyến A→B→C→D có capacity30. AB đã giữ 5, BC đã giữ 10, CD đã giữ 3 thì khách A→C thấy min(25,20)=20 chỗ. Xác nhận2 chỗ tăng reserved AB và BC thêm2, không thay CD. Nếu giá cộng dồn A=0,B=100.000,C=220.000 thì A→C là220.000/chỗ,2 chỗ440.000; server tính, client không tự gửi total đáng tin.

### Q24. PENDING có giữ chỗ chưa, hai người giành chỗ cuối thế nào?

PENDING chỉ là yêu cầu, chưa giữ chỗ nên nhiều yêu cầu có thể chờ. Lúc CONFIRMED, transaction khóa/kiểm lại sức chứa trên mọi segment và cập nhật nguyên tử. Nếu chỉ còn 1 chỗ cho hai yêu cầu, không được cả hai cùng thành công; bên không đủ nhận BOOKING_CAPACITY/409. UPDATE còn kiểm giới hạn0..capacity ngay trong SQL; lỗi history phải rollback cả chỗ. Kết quả tìm kiếm không phải lời hứa chỗ đến lúc xác nhận.

### Q25. Vì sao phải có thứ tự khóa?

Trip → booking → segments là thứ tự chung để các luồng cập nhật không giữ khóa ngược nhau và tăng nguy cơ deadlock. Ngoài khóa còn kiểm state/version/CAS và đủ số đoạn. Chỉ nói “đã dùng transaction” chưa chứng minh không bán vượt chỗ; cần query khóa và ca concurrent thật.

### Q26. History ghi lỗi thì chỗ/tiền có bị cập nhật nửa chừng không?

Booking/segments/history cùng transaction, nên lỗi ghi sự kiện làm rollback phần cập nhật liên quan. Tương tự mutation quyền có AccessChange cùng transaction. Journal là log chẩn đoán riêng, không có cùng bảo đảm đó. Chỉ ca fault injection trên DB test; không phá trigger production để chứng minh rollback.

### Q27. WEB khác PHONE thế nào, sao khách chỉ có một đơn active?

WEB gắn owner là principal khách; PHONE do staff tạo trong scope, không nhận owner/giá/audit đáng tin từ client. Cả hai ban đầu PENDING. WEB giới hạn một đơn PENDING/CONFIRMED mỗi khách để tránh ôm nhiều yêu cầu; tạo lặp cùng requestKey/actor/nội dung trả đơn cũ, khác nội dung là xung đột. Không phải tất cả endpoint đều idempotent.

### Q28. Hủy đơn/hủy chuyến có tự hoàn tiền không?

Không. Hủy đủ điều kiện đổi trạng thái và trả chỗ nếu đơn CONFIRMED; còn kiểm điểm đón đã rời chưa. Hủy chuyến DRAFT/OPEN có lý do, xử lý nhóm đơn/chỗ/history theo tập. Tiền đã thu vẫn cần ghi hoàn thực tế riêng; báo cáo giữ khoản phải hoàn. Không gọi hủy đơn là tiền đã chuyển về khách.

### Q29. Bấm thu tiền hai lần hoặc dùng lại requestKey khác nội dung thì sao?

Thu/hoàn có UUID requestKey. Cùng booking/actor/kind/amount/note đã ghi trả kết quả hiện hành, không cộng tiền lần nữa; key trùng khác nội dung bị xung đột. Thao tác mới cần version đúng. PAYMENT chỉ CONFIRMED/COMPLETED và không vượt total; REFUND chỉ CANCELLED và tổng hoàn không vượt đã thu. UPDATE tiền và CHECK ở DB chặn thu vượt giá/hoàn vượt đã thu. History lỗi rollback cả tiền và key. Đây là ghi nhận thủ công, không tích hợp ngân hàng.

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

Đã có bằng chứng nghiệm thu tại [PR157](https://github.com/hieuv526034/vietroute-web/pull/157) và [PR161 chuyển Singapore](https://github.com/hieuv526034/vietroute-web/pull/161). Lượt Singapore main7ef6025 kiểm admin docs/OpenAPI200, tài khoản thường403 và bộ chọn tỉnh→địa điểm/nhà xe với một nút tìm. Trước mỗi lần demo vẫn đối chiếu SHA Live/tiến độ/PR và môi trường thực, không lấy preview hoặc kết quả của một phiên cũ làm bằng chứng cho phiên mới.

### Q42. Local khác Render và bí mật cấu hình được giữ ở đâu?

Local dùng launcher/Run config, .env local; OS/IDE ưu tiên. Render dùng env và WAR/image, không đọc .env hoặc cài Node. DB hiện hành hieuboy/vietroute, .env không theo dõi Git. Chủ dự án cho phép .env.example giữ đầy đủ password/key khác, riêng VIETROUTE_DB_PASSWORD là XXX; không trình chiếu nội dung file. Lịch sử cũ chưa viết lại. Máy chủ DB chưa TLS, chủ dự án đã chấp thuận riêng; không quảng bá kết nối đã mã hóa.

### Q43. Đã hỗ trợ Oracle26ai đầy đủ chưa?

Chưa. Có JDBC Thin/ojdbc17, profile và dialect chuẩn bị nhưng runtime chủ động chặn trước SQL kể cả init tắt. Repo còn array/JSONB/ON CONFLICT/RETURNING/advisory lock theo PostgreSQL, chưa có migration/transaction schema hoặc regression Oracle thật. Driver nhận URL không chứng minh nghiệp vụ đã chuyển DB.

### Q44. Nếu đưa vào dùng thật, những giới hạn nào cần xử lý?

Đánh giá tải/SLA trên staging; TLS DB, backup di động/PITR và drill/RPO/RTO; quy trình vận hành, dữ liệu đơn vị và cách xử lý sự cố. Render Free có cold start; chưa có payment gateway, GPS/mobile, SSO/MFA/refresh token hoặc mail/reset. Nêu thứ cần thiết theo yêu cầu mới, không nói đã làm; không phục hồi module phụ chỉ để slide có nhiều chức năng.

### Q45. Từng thành viên làm gì, AI có tham gia thì trình bày thế nào?

Theo phân công mới02/11/2026, nhóm có hai dev: hieuv526034 phụ trách auth/common/DB/cơ chế init/CI/deploy và chức năng báo cáo/dashboard; BangPhamIT phụ trách catalog/carrier/trip/booking và UI tương ứng. anhhoang104 chủ trì báo cáo Word/nội dung/sơ đồ; hiencoi250404-design test thủ công, chụp ảnh chức năng, viết bảng kết quả và cùng hoàn thiện Word để nộp. Hai dev phụ trách code và test tự động. Nêu đầu ra và bằng chứng commit/PR/review/test thực tế của từng người, kể cả tài liệu/test. Phân công mới không biến source nền hoặc code đã có thành tác giả mới; không đổi author hoặc đếm commit thay chất lượng. Có hỗ trợ AI thì nói rõ phần hỗ trợ và trách nhiệm hiểu/kiểm/trình bày của thành viên.

Nguồn: [vận hành](../04-van-hanh/VAN_HANH.md), [schema](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md), [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md), [phân công](PHAN_CONG.md) và các regression trong [src/test](../../src/test).

## Phản biện theo phần của hai dev — Q46 đến Q55

Hai dev học chung Q01–Q06 và Q39–Q45. Hieu trả lời chính auth/common/DB/report/dashboard; Bang trả lời chính catalog/carrier/trip/booking/driver. Khi câu hỏi đi qua cả hai phần, người nhận câu hỏi nêu nguyên tắc rồi chuyển đúng người bằng ID/ví dụ đang demo, không trả lời thay bằng suy đoán.

### Q46. Hieu: Nếu em chỉ đổi role trong JWT hoặc sửa HTML để hiện nút admin thì sao?

**Có thể nói:** “Điều đó không cấp quyền. JWT bị sửa payload sẽ không qua kiểm chữ ký. Kể cả token còn hợp lệ, backend vẫn đọc account/tokenVersion và grant endpoint hiện hành từ DB; UI chỉ hỗ trợ sử dụng. Em có thể chứng minh bằng operator nhập trực tiếp /docs và bị chặn, thay vì chỉ nhìn menu bị ẩn.”

**Nếu hỏi tiếp về quyền dữ liệu:** “Qua được endpoint vẫn còn scope/owner/assignment. Endpoint đọc booking không cho khách đọc đơn người khác. all_scopes chỉ có hiệu lực khi chính role đó cũng cấp endpoint, không ghép quyền rộng từ role khác.” Chỉ EndpointAccess/UserRoleRepo và Q11–Q15; không thử sửa token/DB thật tại sân khấu.

### Q47. Bang: Sao PENDING không giữ chỗ, vậy khách đặt có bị hết chỗ không?

**Có thể nói:** “Có thể hết trước khi được xác nhận, và ứng dụng phải thông báo đúng. PENDING là yêu cầu chờ điều hành; CONFIRMED mới là chỗ đã giữ. Lúc xác nhận, hệ thống kiểm lại mọi đoạn dưới khóa trong transaction. Nếu muốn thiết kế giữ chỗ tạm thì cần thêm thời hạn, giải phóng quá hạn và ca cạnh tranh; bản này chưa triển khai cơ chế đó.”

**Nếu hỏi sao không xác nhận luôn:** “Đó là lựa chọn luồng xử lý hiện tại, không phải tính chất bắt buộc của mọi app vé xe. Demo phân biệt rõ tiếp nhận và xác nhận, không gọi PENDING là vé chắc chắn.” Chỉ BookingService.state/TripSegmentRepo và Q24–Q25.

### Q48. Bang: Khách mua chỗ số mấy, có sơ đồ ghế và vé QR không?

**Có thể nói:** “Bản này đặt số lượng chỗ theo chặng, chưa gán ghế cụ thể hoặc triển khai soát vé QR. Vì vậy tồn được quản lý bằng sức chứa và số chỗ đã xác nhận trên từng segment. Em không gọi hai chỗ là ghế số1,2. Nếu phát triển sơ đồ ghế thì phải thêm mô hình ghế, kiểm chồng theo đoạn và quy trình đổi/giữ ghế.”

**Ví dụ nối tiếp:** “Xe16 chỗ có thể chỉ còn13 trên chặng đang chọn vì đã có đơn giữ3. Khi một nhóm xuống ở điểm giữa, đoạn phía sau có thể có lượng chỗ khả dụng khác.” Chỉ BookingRequest/TripSegmentRepo và Q23; không tuyên bố đã có bàn giao ghế hoặc phát hành vé điện tử.

### Q49. Bang: Bấm xác nhận hay thu tiền xong mạng đứt thì bấm lại có bị nhân đôi không?

**Có thể nói:** “Em không khẳng định mọi API đều idempotent. Tạo đơn và ghi thu/hoàn dùng requestKey theo contract; cùng key, actor và nội dung đã được ghi thì không tạo hoặc cộng thêm lần nữa. Key cũ với nội dung khác bị từ chối. Cập nhật trạng thái còn kiểm version/state. Khi mất response, em tải lại đúng đơn và kiểm lịch sử trước khi quyết định gửi lại, không đổi key rồi bấm thu tiếp.”

**Nếu hỏi request chưa đến server:** “Cần phân biệt chưa gửi, chưa nhận response và đã commit nhưng response mất. UI giữ draft/key và không tự replay mutation; kết quả hiện hành ở server là căn cứ.” Chỉ BookingService.create/payment và Q14/Q27/Q29.

### Q50. Hieu: Báo cáo có thể dùng làm sổ kế toán hoặc đối soát ngân hàng luôn không?

**Có thể nói:** “Chưa. Báo cáo này tổng hợp tập chuyến theo ngày khởi hành và scope, cùng tiền ghi nhận thủ công của các đơn đó. Nó không đọc sao kê ngân hàng, không nhận callback cổng thanh toán và không là sổ tiền theo ngày event thu/hoàn. Giá trị đơn, đã thu, đã hoàn, tiền ròng và phải hoàn được tách để tránh nhầm.”

**Nếu yêu cầu báo cáo theo ngày nhận tiền:** “Cần thống nhất contract mới lọc PAYMENT/REFUND event theo thời gian giao dịch, có query/test và đối soát tương ứng. Không chỉ đổi nhãn bộ lọc ngày trên giao diện.” Chỉ ReportRepo.summary và Q30–Q32.

### Q51. Hieu: Vừa xác nhận xong, sao dashboard/báo cáo chưa đổi ngay?

**Có thể nói:** “Phải kiểm cùng actor, scope, nhà xe, ngày khởi hành và thời điểm snapshot. Bản này không hứa cập nhật push thời gian thực. Em chủ động tải lại và kiểm asOf. Đơn mới PENDING cũng chưa thuộc giá trị CONFIRMED/COMPLETED, nên không phải tổng nào cũng tăng sau mọi thao tác.”

**Nếu vẫn lệch:** “Em đối chiếu mã chuyến/đơn và công thức từng field với số nền. Nếu không giải thích được thì ghi lỗi, không sửa số phía trình duyệt để khớp.” Chỉ ReportSummaryResponse/ReportRepo và đơn A/B đã chuẩn bị; không suy mỗi ô dashboard đều dùng cùng định nghĩa nếu chưa đọc contract.

### Q52. Hieu: Em nói nhanh hơn sau chuyển Singapore, bằng chứng và giới hạn ở đâu?

**Có thể nói:** “Nhóm có đợt so sánh cùng code/DB và cùng tập GET tuần tự giữa Virginia và Singapore. Mẫu đo ghi nhận các API có DB ở Singapore nhanh hơn; đó là bằng chứng theo mẫu, không phải SLA hoặc kết quả kiểm tải. Khoảng cách mạng và số lượt trao đổi DB có ảnh hưởng, còn Free có cold start và vẫn có request timeout.”

**Nếu yêu cầu số chính xác:** Mở bằng chứng PERF05/PR161 đúng thời điểm đo; nếu không có số đối chiếu trước mặt thì không nhớ ước lượng thành kết quả chính thức. Nêu rõ lượt DATA02 GET carriers đã timeout dù kiểm DB đổi tên đạt. Không dùng /health200 suy mọi query nhanh, không gọi timeout là DB hỏng khi chưa tách network/CPU/pool/SQL.

### Q53. Bang: Sửa tên tài xế hoặc tuyến mẫu thì dữ liệu chuyến cũ có tự đổi hết không?

**Có thể nói:** “Không nên coi dữ liệu nguồn là lịch sử duy nhất. Chuyến có snapshot, nên sửa tuyến/tài xế hiện hành không mặc định viết lại mọi chuyến cũ. Đợt đổi24 tên tài xế giả lập vừa rồi là bảo trì riêng được chủ dự án cho phép: backup kiểm phục hồi trước, chỉ cập nhật494 nhãn trên chuyến seed xác định sẵn, giữ dữ liệu ngoài phạm vi.”

**Nếu hỏi sao không đổi code luôn:** “Code/ID đang liên kết account, scope, tuyến/chuyến. Yêu cầu là đổi tên hiển thị dễ phân biệt, nên giữ mã ổn định và liên kết; source seed mới cũng dùng cùng bộ tên.” Chỉ TripRepo/BusinessDataRepo và DATA02 trong tiến độ. Không mô tả việc bảo trì riêng là tính năng tự cập nhật snapshot khi restart.

### Q54. Hieu/Bang: Ai làm gì và hai bạn còn lại đóng góp thế nào?

**Hieu nói:** “Theo phân công hiện tại em phụ trách auth/common, DB/init/CI/deploy và báo cáo/dashboard. Bạn Bang phụ trách catalog/carrier/trip/booking và giao diện tương ứng. Hai dev chịu code và kiểm thử tự động.”

**Bang nối:** “Bạn Anh chủ trì Word, nội dung và sơ đồ; bạn Hien test thủ công, chụp ảnh chức năng và ghi bảng kết quả. Hai bạn cùng hoàn thiện báo cáo để nộp; dev cung cấp và xác nhận phần kỹ thuật. Phân công không thay tác giả source cũ; đóng góp thực tế đối chiếu bằng bản nộp, commit/PR và kết quả kiểm.”

Nếu hỏi dùng AI, nói đúng công cụ đã dùng và phạm vi hỗ trợ; chỉ ra phần thành viên đã đọc, sửa, kiểm và có thể giải thích. Không nhận phần chưa làm hoặc nói “AI làm hết nên em không rõ”. Câu chưa rõ thì ghi nhận cần kiểm source, không bịa quy tắc nghiệp vụ.

### Q55. Hai dev: Nếu thầy/cô yêu cầu một chức năng chưa có ngay trong demo?

**Có thể nói:** “Em xin phân biệt chức năng hiện có và đề xuất mở rộng. Phần hiện có em có thể mở màn/contract và chỉ ca kiểm. Với phần chưa có, em nêu dữ liệu, luồng và điều kiện cần bổ sung; em không dùng ảnh thiết kế để khẳng định đã chạy.”

Ba ví dụ trả lời: thanh toán online cần provider/callback/xác thực/idempotence và đối soát; GPS cần nguồn vị trí/quyền truy cập/quy trình vận hành riêng; Oracle cần migration/SQL/transaction/regression thật, hiện mới chuẩn bị cấu hình và đang chặn runtime. Kết bằng việc có thể làm tiếp sau khi chốt yêu cầu; không hứa thời gian chưa ước lượng hoặc tự mở lại các module đã loại khỏi phạm vi.

### Chỗ mở source khi được hỏi sâu

| Người và nhóm câu | Điểm mở ngắn |
|---|---|
| Hieu — login/quyền | [UserService](../../src/main/java/vn/vietroute/auth/service/UserService.java), [EndpointAccess](../../src/main/java/vn/vietroute/auth/security/EndpointAccess.java), [UserRoleRepo](../../src/main/java/vn/vietroute/auth/repo/UserRoleRepo.java) |
| Hieu — transaction/migration | [JdbcTransactions](../../src/main/java/vn/vietroute/common/jdbc/JdbcTransactions.java), [DatabaseInitializer](../../src/main/java/vn/vietroute/common/initializer/DatabaseInitializer.java), [manifest](../../src/main/resources/db/migration/postgresql/migrations.list) |
| Hieu — báo cáo | [ReportRepo](../../src/main/java/vn/vietroute/business/report/repo/ReportRepo.java), [ReportSummaryResponse](../../src/main/java/vn/vietroute/business/report/dto/response/ReportSummaryResponse.java) |
| Bang — tạo/xác nhận/thu–hoàn | [BookingService](../../src/main/java/vn/vietroute/business/booking/service/BookingService.java), [BookingRepo](../../src/main/java/vn/vietroute/business/booking/repo/BookingRepo.java), [TripSegmentRepo](../../src/main/java/vn/vietroute/business/trip/repo/TripSegmentRepo.java) |
| Bang — snapshot/tìm/tiến độ | [TripRepo](../../src/main/java/vn/vietroute/business/trip/repo/TripRepo.java), [JourneyRepo](../../src/main/java/vn/vietroute/business/trip/repo/JourneyRepo.java), [DriverTripService](../../src/main/java/vn/vietroute/business/trip/service/DriverTripService.java) |
| Hai dev — bằng chứng | [BusinessIntegrationTest](../../src/test/java/vn/vietroute/BusinessIntegrationTest.java), [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) và PR đúng commit, không đọc số ca của bản khác thành số hiện tại |

## Báo cáo sản phẩm theo mẫu Công nghệ Java

Ngày 31/10/2026, chủ dự án chỉ định [mẫu Word Công nghệ Java](mau/Mau_BTL_Cong_nghe_Java.docx) làm format cho tài liệu sản phẩm VietRoute và yêu cầu lưu file mẫu gốc trong Git để dùng chung khi đổi máy. Giữ nguyên byte của bản mẫu; SHA-256 là `bd8fc0707ebadb9373d988f28bc7d4b036542f6bb8a662191228f410ca5089c6`. Mẫu theo repository tại `docs/05-ke-hoach/mau/`, không dùng đường dẫn máy cá nhân làm nguồn thay thế khi bàn giao. Đây là báo cáo bài tập lớn học phần Công nghệ Java, khác với hướng dẫn vận hành/API hiện có.

Mẫu gốc đã được chọn làm chuẩn bố cục; hai dev bàn giao nội dung kỹ thuật và phiên bản để nhóm tài liệu hoàn thiện bản nộp. Báo cáo thuộc **Nhóm 10**, gồm bốn thành viên đã xác định tại [phân công](PHAN_CONG.md#thông-tin-thành-viên): `anhhoang104`, `BangPhamIT`, `hiencoi250404-design`, `hieuv526034`. **Lớp, khóa và giảng viên để trống**, bổ sung sau theo thông tin chủ dự án cung cấp. Họ tên/MSSV dùng dữ liệu đã được xác nhận khi soạn; không suy từ username hoặc tự tạo danh tính. Theo phân công mới02/11/2026, Anh và Hien phụ trách hoàn thiện Word để nộp, thu thập sơ đồ/ảnh và test thủ công; trạng thái bản Word thực tế phải ghi trong tiến độ, không coi phân công là đã có bản nộp.

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

Đã dựng [báo cáo Word Nhóm10](bao-cao/Bao_cao_VietRoute_Nhom_10.docx) theo lệnh soạn đầy đủ của chủ dự án, kèm [sơ đồ SVG và ảnh gốc](bao-cao/Tu_lieu_VietRoute_Nhom_10.zip). Giữ ba chương, hai bìa, mục lục/danh mục, kết luận, tham khảo và phụ lục schema/demo; chỉ các thông tin chưa xác nhận trên bìa để ô [[...]]. Mẫu gốc giữ nguyên checksum. Bản này dùng dữ liệu local giả lập, không ảnh production hoặc minh chứng giao dịch thương mại.

Field TOC/SEQ/PAGE được cập nhật bằng Microsoft Word cài sẵn rồi xuất PDF để rà trang; renderer đóng gói đã được chẩn đoán thiếu LibreOffice, không dùng lỗi đó để bỏ kiểm bố cục. Phạm vi source/test/ảnh, kết quả kiểm và giới hạn ở tài liệu tiến độ. Khi chỉnh nội dung hoặc thay ảnh, cập nhật mục lục/danh mục/số trang và kiểm lại bản in, không giữ số trang cũ.

## Trình bày danh mục chung

BangPhamIT mở Danh mục chung, chọn tab Tỉnh/thành và xã/phường. Nói “Dữ liệu được quản lý theo nhóm trong một bảng Category. Màn hình chỉ đọc tỉnh trước, mở tỉnh mới lấy xã/phường, nên không tải hàng nghìn dòng lúc mở trang.” Mở Hải Phòng rồi thu gọn. Nhập mã xã/phường 26824 và tìm. Nói “Kết quả có cả tỉnh cha và xã khớp. SQL recursive dựng đường dẫn để người dùng biết mục thuộc đâu.” Xóa tìm, chuyển Loại xe để chỉ số chỗ; chuyển Địa điểm để mở theo tỉnh, xã và chọn ghim trong modal.

Nếu hỏi tại sao cây không phân trang, trả lời “Mỗi nhánh là một cấp trực tiếp. Phân trang có thể làm người dùng tưởng cha thiếu con. Chúng em tải theo cha, còn tìm toàn cây chạy ở DB và chỉ trả kết quả cùng tổ tiên.” Nếu hỏi khác biệt với select, trả lời “Select nghiệp vụ vẫn có lookup phân trang và lọc theo cha, chỉ trả trường công khai. Bảng quản trị dùng chế độ tree với grant riêng.” Nếu hỏi tìm không dấu, trả lời “Chuỗi tìm và dữ liệu được chuẩn hóa chữ thường và dấu tiếng Việt, mã vẫn tìm được. Tham số được bind, các ký tự wildcard do người dùng nhập được escape.”
