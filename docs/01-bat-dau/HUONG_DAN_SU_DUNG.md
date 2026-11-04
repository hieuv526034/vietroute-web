# Hướng dẫn sử dụng VietRoute từ A đến Z

Tài liệu dành cho người lần đầu sử dụng VietRoute, từ đăng nhập đến hoàn tất một hành trình và đối soát kết quả. Người dùng làm theo phần dành cho vai trò được cấp. Khi thực hành tạo, xác nhận, hủy đơn hoặc ghi tiền, dùng môi trường học đã được quản trị chuẩn bị vì các nút Lưu có tác động đến dữ liệu.

## 1. Chuẩn bị và mở ứng dụng

1. Nhận địa chỉ ứng dụng, username, mật khẩu và tên nhà xe/phạm vi được cấp từ quản trị. Địa chỉ nghiệm thu là [VietRoute Singapore](https://vietroute-web-sg.onrender.com/journeys?lang=vi). Người chạy source làm theo [Chạy local](CHAY_LOCAL.md) trước, rồi mở địa chỉ local được launcher thông báo.
2. Mở bằng trình duyệt hiện đại, bật JavaScript và cookie. Trên gói Render Free, lần đầu sau thời gian không dùng có thể phải chờ ứng dụng khởi động. Chờ trang tải xong trước khi gửi thao tác tiếp theo.
3. Chọn tiếng Việt nếu cần. Mặc định giao diện dùng tiếng Việt, có thể chuyển tiếng Anh qua lựa chọn ngôn ngữ. Giờ nghiệp vụ nhập và xem theo giờ Việt Nam, số tiền theo VND.
4. Nếu cần học bằng dữ liệu có sẵn, dùng tài khoản theo bảng dưới. Mật khẩu nhận riêng từ quản trị. Mật khẩu khởi tạo có thể đã được đổi, không suy từ username.

| Nhu cầu | Username mẫu | Phần học |
|---|---|---|
| Quản trị hệ thống | admin | Tài khoản, phân quyền, danh mục, nhật ký và toàn bộ nghiệp vụ theo grant |
| Khách hàng | custom01 hoặc customduylong | Tìm chuyến, đặt chỗ, theo dõi và hủy đơn của mình |
| Điều hành nhà xe | operator01 hoặc operatorduylong | Tài nguyên, tuyến, chuyến, nhận/xác nhận đơn và ghi nhận thu/hoàn trong phạm vi |
| Tài xế | driver01 hoặc driverduylong | Chuyến được phân công, danh sách đón/trả và xác nhận tiến độ |

Danh sách đủ tài khoản, tên nhà xe và key cấu hình khởi tạo ở [Tài khoản dùng thử](CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo). Duy Long là một nhà xe trong bộ giả lập nhiều nhà xe. Ngày của chuyến seed được neo khi khởi tạo lần đầu nên hãy chọn ngày còn chuyến trong môi trường đang học.

## 2. Đăng nhập và quản lý phiên

1. Bấm Đăng nhập hoặc mở `/login`. Nhập username và mật khẩu. Email là thông tin liên hệ của tài khoản, màn đăng nhập sử dụng username.
2. Bấm nút đăng nhập một lần và chờ phản hồi. Khi thành công, ứng dụng mở trang phù hợp với quyền của bạn. Kiểm tên hiển thị ở góc trên trước khi thao tác.
3. Nếu ứng dụng yêu cầu đổi mật khẩu, hoàn thành ở trang tài khoản trước khi tiếp tục. Nhập mật khẩu hiện tại, mật khẩu mới và phần xác nhận theo form. Đọc quy tắc ngay trên màn hình, đặt mật khẩu mới khác mật khẩu cũ.
4. Khi không dùng nữa, mở menu tài khoản và chọn Đăng xuất. Trên máy dùng chung, đóng tab sau khi đăng xuất. Để minh họa hai vai trò cùng lúc, dùng hai profile trình duyệt hoặc cửa sổ riêng tư riêng biệt.

Nhập sai liên tiếp 5 lần có thể làm tài khoản bị khóa 15 phút. Khi đã thấy báo sai, kiểm username, Caps Lock và ngôn ngữ bàn phím trước lần thử tiếp. Nếu mất mật khẩu hoặc bị vô hiệu hóa, liên hệ quản trị để được hỗ trợ. Sản phẩm hiện chưa có khôi phục mật khẩu tự động qua email.

## 3. Cách dùng menu, bảng và form

1. Bấm Menu ở header để mở các nhóm chức năng. Chỉ những menu đã được cấp mới xuất hiện. Chọn một mục để vào workspace, dùng breadcrumb để biết mình đang ở đâu.
2. Với màn có tab, chọn tab đúng tài nguyên trước. Ô tìm kiếm/bộ lọc chỉ áp dụng khi bấm nút tìm của màn đó. Bảng kết quả thông thường có phân trang ở phần bảng, dùng số trang hoặc điều hướng khi có nhiều trang.
3. Bấm Thêm mới để mở form. Biểu tượng bút dùng sửa, biểu tượng ngừng/xóa dùng thao tác được nhãn nút xác nhận. Có thể rê chuột lên biểu tượng để xem tên. Đọc hộp xác nhận trước khi đồng ý.
4. Các trường có danh sách phải chọn giá trị. Chọn nhà xe/tỉnh trước rồi chọn tuyến, xe, tài xế hoặc xã/phường phía sau. Khi đổi cha, lựa chọn con có thể bị xóa và cần chọn lại.
5. Bấm Lưu một lần. Khi đang gửi, nút tạm khóa để tránh gửi lặp. Nếu lỗi, đọc thông báo, sửa trường liên quan trong form còn giữ bản nháp rồi gửi lại khi đã xác định đúng.
6. Dùng Tab để chuyển trường, Enter để gửi form khi phù hợp, Escape hoặc nút đóng để đóng modal. Với bản nháp chưa lưu, kiểm nội dung trước khi đóng để tránh phải nhập lại.

Quyền menu và quyền thao tác được kiểm riêng. Thấy màn danh sách không đồng nghĩa có quyền thêm/sửa/gán. Nếu thiếu menu hoặc nút, liên hệ quản trị và nêu chức năng cần dùng, username và nhà xe.

## 4. Khách hàng tìm chuyến

1. Mở Tìm chuyến tại `/journeys`. Chọn tỉnh/thành đi và tỉnh/thành đến khác nhau.
2. Nếu biết điểm cụ thể, chọn xã/phường rồi điểm đón/trả sau khi danh sách con tải xong. Có thể tìm rộng theo tỉnh khi chưa chọn địa điểm chi tiết.
3. Chọn ngày đón tại điểm của bạn và số chỗ. Ngày đón có thể khác ngày xuất phát từ đầu tuyến với hành trình qua đêm.
4. Chọn thêm nhà xe, loại xe hoặc khoảng giá nếu màn hình cung cấp và bạn muốn thu hẹp kết quả. Bấm Tìm kiếm một lần.
5. Đọc từng kết quả gồm nhà xe, giờ đón/trả, giá chặng và chỗ còn. Các trang kết quả dùng cùng bộ lọc đã áp dụng. Đổi lựa chọn trên form rồi bấm tìm để áp tiêu chí mới.
6. Mở chi tiết chuyến hoặc báo giá. Kiểm lại đúng hai điểm và số chỗ trước khi đặt. Giá/chỗ được server kiểm lại ở bước đặt và xác nhận, có thể thay đổi khi người khác đặt cùng lúc.

Không thấy chuyến thì kiểm chiều đi, ngày còn lịch, số chỗ, bộ lọc nhà xe và địa điểm con. Thử tìm theo tỉnh trước rồi thu hẹp. Chuyến phải còn trạng thái bán phù hợp và chưa rời điểm đón. Khởi động lại ứng dụng không tự dịch các chuyến seed sang ngày mới.

## 5. Khách đặt chỗ và theo dõi đơn

1. Đăng nhập bằng tài khoản khách. Chọn kết quả phù hợp và mở báo giá/đặt chỗ.
2. Kiểm nhà xe, ngày/giờ đón, điểm đón, điểm trả, số chỗ, đơn giá và tổng tiền. Nhập tên và điện thoại liên hệ chính xác.
3. Gửi yêu cầu một lần. Khi thành công, ghi lại mã đơn và mở Đơn của tôi tại `/my-bookings`.
4. Đơn mới có trạng thái Chờ xử lý (`PENDING`). Liên hệ nhà xe nếu cần xác nhận sớm. Chỗ chỉ được giữ sau khi điều hành xác nhận thành `CONFIRMED`.
5. Mở chi tiết để xem trạng thái và lịch sử, số tiền đã thu/hoàn. Tìm lại bằng mã đơn khi cần hỗ trợ.
6. Nếu cần hủy, mở đúng đơn, chọn Hủy khi còn được phép, nhập lý do và xác nhận. Nếu điểm đón đã rời hoặc trạng thái không phù hợp, hệ thống từ chối và bạn cần liên hệ điều hành.

Đơn WEB tuân theo giới hạn đơn đang hoạt động của khách. Khi bị báo đã có đơn hoạt động, xử lý hoặc xem đơn hiện có trước khi đặt thêm. Hủy đơn đã xác nhận trả lại chỗ, còn tiền hoàn được điều hành ghi nhận riêng. Hệ thống hiện ghi nhận thu/hoàn thủ công, cần thực hiện thanh toán với nhà xe theo thỏa thuận.

## 6. Điều hành nhận và xử lý đơn

1. Đăng nhập tài khoản điều hành, kiểm đúng nhà xe/phạm vi. Mở quản lý đơn tại `/operations/bookings`.
2. Lọc bằng mã, trạng thái hoặc tiêu chí trên màn rồi bấm tìm. Chọn một đơn để xem chặng, liên hệ, số chỗ và lịch sử.
3. Với đơn Chờ xử lý, kiểm lịch xe, điểm đón/trả và nhu cầu khách rồi bấm Xác nhận. Sau thành công, trạng thái là Đã xác nhận và chỗ trên các đoạn liên quan đã được giữ.
4. Nếu không đáp ứng, dùng Từ chối khi còn được phép và nhập ghi chú/lý do theo form. Nếu không đủ chỗ khi xác nhận, tải lại đơn/chuyến để kiểm tình trạng mới rồi trao đổi với khách.
5. Với khách gọi điện, dùng Thêm mới, chọn nhà xe/chuyến, hai điểm đón/trả, số chỗ và thông tin liên hệ. Đơn nhân viên nhập có nguồn PHONE, vẫn cần bước xác nhận theo trạng thái và kiểm chỗ hiện hành.
6. Khi hủy theo yêu cầu khách, chọn đúng đơn và nhập lý do. Kiểm lại trạng thái cùng số tiền đã thu để xử lý tiền còn phải hoàn.

Chỉ thao tác đơn thuộc phạm vi được gán. Khi nhiều điều hành cùng sửa, lỗi dữ liệu cũ yêu cầu tải lại và đối chiếu lịch sử. Nếu mạng ngắt sau bấm Lưu, tìm lại mã đơn/lịch sử trước khi nhập một đơn mới vì thao tác trước có thể đã thành công.

## 7. Điều hành ghi nhận thu tiền và hoàn tiền

1. Mở chi tiết đơn và xem Tổng giá trị, Đã thu, Đã hoàn. Chỉ ghi nhận khoản tiền thực tế đã thu hoặc đã hoàn.
2. Mở thao tác thu/hoàn của đơn. Chọn loại, nhập số tiền của lần này và ghi chú để đối soát. Đây là số tiền phát sinh, không nhập lại tổng lũy kế đã ghi.
3. Kiểm đúng loại và bấm lưu một lần. Sau thành công, kiểm lịch sử có sự kiện mới và tổng tiền tương ứng đã tăng.
4. Tổng thu không được vượt tổng giá trị đơn, tổng hoàn không được vượt tổng thu. Nếu bị báo vượt, đối chiếu số cũ và số thực thu/hoàn trước khi sửa số nhập.
5. Với đơn hủy đã thu tiền, thực hiện hoàn thực tế theo quy trình nhà xe rồi ghi nhận REFUND. Kiểm Đã hoàn và khoản còn cần hoàn để tránh bỏ sót.

Ví dụ đơn hai chỗ, 150.000 đồng/chỗ có tổng 300.000 đồng. Thu lần đầu 100.000 đồng, lần sau 200.000 đồng thì Đã thu là 300.000 đồng. Khi hủy và hoàn đủ 300.000 đồng, ghi một hoặc nhiều lần hoàn có tổng 300.000 đồng. Hệ thống ghi nhận các khoản này để đối soát, không tự chuyển tiền ngân hàng.

## 8. Quản trị danh mục chung

1. Đăng nhập tài khoản có quyền quản trị danh mục, mở `/operations/categories`. Chọn tab Loại xe, Nguồn đặt chỗ, Địa điểm hoặc Tỉnh/thành và xã/phường.
2. Với cây hành chính, trang đầu chỉ hiện tỉnh/thành. Bấm mũi tên của một tỉnh để tải và xổ xã/phường ngay bên dưới. Thu gọn khi muốn bớt dòng. Bảng cây không chia trang.
3. Để tìm một xã, nhập mã hoặc tên có dấu/không dấu rồi bấm tìm. Ví dụ mã `26824` trả Phường Thủ Đức kèm tỉnh/thành cha. Hệ thống mở đường dẫn và tô nổi mục khớp. Bấm Bỏ tìm kiếm để về các nút gốc.
4. Với Địa điểm, mở tỉnh rồi xã/phường và các điểm thuộc xã đó. Bấm biểu tượng dấu cộng ở đúng dòng cha. Các nút thêm, sửa và ngừng sử dụng có nhãn khi rê chuột. Điền mã, tên, mô tả, địa chỉ và các trường form yêu cầu.
5. Bấm **Chọn trên bản đồ**. Nhập tên địa điểm hoặc địa chỉ công cộng, ví dụ `Bến xe Gia Lâm, Hà Nội`, rồi bấm **Tìm kiếm** hoặc Enter. Chọn kết quả để bản đồ phóng tới đó và điền tọa độ. Bấm hoặc kéo ghim để chỉnh đúng điểm đón/trả. Có thể di chuyển bằng bàn phím rồi chọn vị trí ở tâm. Kiểm lại địa chỉ và tọa độ trước khi lưu. Nếu tra cứu lỗi, vẫn có thể ghim trực tiếp.
6. Với Loại xe, nhập số chỗ gợi ý phù hợp. Xe cụ thể vẫn có trường sức chứa riêng, ví dụ loại Limousine có thể gợi ý một giá trị nhưng xe thực tế phải khai đúng số chỗ của xe.
7. Để sửa, mở bút ở đúng mục, chỉnh rồi lưu. Khi ngừng sử dụng, kiểm các dữ liệu con và nghiệp vụ liên quan trước xác nhận. Danh mục đã dùng trong lịch sử vẫn được lưu để đối chiếu.
8. Thao tác Tạo nhóm/Sửa nhóm dành cho người được giao quản lý cấu trúc danh mục. Code nhóm xác định tập dữ liệu, các thuộc tính và nhóm cha cần đúng cấu hình. Người mới nên thao tác giá trị trong nhóm có sẵn và trao đổi quản trị trước khi đổi cấu trúc.

Đổi tỉnh trong form làm xóa xã/phường và địa điểm đã chọn trước đó. Danh sách con rỗng có thể do chưa chọn cha, mục ngừng sử dụng hoặc không có dữ liệu. Khi chưa tải được danh sách hay bản đồ, kiểm mạng và giữ lại nội dung form để bổ sung sau.

## 9. Chuẩn bị nhà xe, xe và tài xế

1. Người có quyền mở `/operations/carriers` để tạo nhà xe, điền mã, tên, liên hệ, mô tả và thông tin phạm vi theo form. Người điều hành chỉ dùng các nhà xe được gán.
2. Mở `/operations/vehicles`, chọn nhà xe rồi thêm xe. Điền mã xe, biển số, loại xe từ danh mục và số chỗ thực tế. Kiểm biển số trùng hoặc định dạng không hợp lệ theo thông báo.
3. Mở `/operations/drivers`, chọn nhà xe rồi nhập hồ sơ tài xế gồm mã, tên và điện thoại. Gắn tài khoản tài xế bằng thao tác quản lý tài khoản khi có quyền.
4. Quản trị kiểm role DRIVER và gán phạm vi phù hợp cho tài khoản được liên kết. Hồ sơ có tên tài xế chưa đủ để người đó đăng nhập và xem chuyến nếu chưa có account/assignment hợp lệ.
5. Kiểm nhà xe, xe và tài xế đang sử dụng trước khi phân công. Xe hoặc tài xế trùng lịch sẽ bị từ chối khi lưu chuyến.

## 10. Tạo mẫu tuyến và các điểm dừng

1. Mở `/operations/routes`, chọn nhà xe và tạo mẫu tuyến một chiều với mã, tên và mô tả.
2. Mở điểm dừng của tuyến. Chọn tỉnh → xã/phường → địa điểm cho từng điểm, đặt thứ tự từ đầu đến cuối.
3. Điền số phút đến/rời tính từ giờ khởi hành đầu tuyến. Điểm sau phải có mốc phù hợp thứ tự hành trình. Chọn cho phép đón/trả theo hoạt động của điểm.
4. Điền giá cộng dồn từ đầu tuyến. Giá chặng bằng chênh lệch giá cộng dồn hai đầu. Ví dụ A có giá 0, B 100.000 đồng và C 220.000 đồng thì A→C là 220.000 đồng/chỗ, B→C là 120.000 đồng/chỗ.
5. Lưu và kiểm lại toàn bộ thứ tự, giờ, giá, đón/trả. Tạo mẫu riêng cho chiều về vì thứ tự điểm và giờ có thể khác.

Ví dụ để học chiều đi Hải Phòng→Hà Nội lúc 05:00–09:00, điểm đầu có offset 0 và điểm cuối có offset đến 240 phút. Khi tạo chiều về, chọn giờ khởi hành riêng và các offset đúng lịch dự kiến. Đổi mẫu tuyến không tự sửa các chuyến đã tạo snapshot trước đó.

## 11. Tạo chuyến, phân công và mở bán

1. Mở `/operations/trips`, thêm chuyến từ mẫu tuyến hợp lệ. Chọn nhà xe, tuyến, ngày/giờ khởi hành theo giờ Việt Nam.
2. Chọn xe và tài xế thuộc nhà xe, kiểm sức chứa. Danh sách phụ thuộc tải lại khi đổi nhà xe hoặc tuyến.
3. Lưu chuyến nháp (`DRAFT`). Mở chi tiết xem snapshot các điểm, giờ đón/trả và giá, biển số/tài xế.
4. Khi đủ điều kiện, bấm Mở bán để chuyển `OPEN`. Chuyến nháp chưa xuất hiện trong kết quả tìm chuyến công khai.
5. Nếu sửa giờ, thực hiện khi chuyến còn nháp. Chuyến mở bán có thể đổi phân công/số chỗ trong điều kiện hệ thống cho phép, phải bảo đảm lịch và số chỗ đã xác nhận.
6. Khi cần hủy chuyến trước lúc chạy, nhập lý do và kiểm ảnh hưởng tới đơn theo phản hồi của hệ thống. Đối chiếu đơn liên quan và khoản cần hoàn riêng.

Mỗi dòng là một chuyến một chiều. Lịch 05:00–09:00 chiều đi, một lượt quay về và hai lượt chiều là bốn chuyến riêng. Với một xe/tài xế, giờ quay đầu phải nối được với điểm cuối và không chồng lịch. Khi lỗi, sửa đúng dữ liệu phân công thay vì bỏ qua cảnh báo.

## 12. Tài xế thực hiện chuyến

1. Đăng nhập tài khoản tài xế và mở Chuyến được giao tại `/driver/trips`. Chọn ngày/trạng thái theo màn để tìm chuyến của mình.
2. Mở chuyến, kiểm ngày giờ, xe, các điểm và danh sách hành khách đón/trả. Nếu không thấy chuyến, liên hệ điều hành kiểm liên kết account với hồ sơ và phân công của chuyến.
3. Khi thực tế bắt đầu, chọn Xuất phát theo nút được gợi ý. Chuyến chuyển từ mở bán sang đang chạy.
4. Đến từng điểm theo thứ tự, xác nhận Đến điểm rồi Rời điểm khi thực tế đã thực hiện. Kiểm khách cần đón/trả và thông tin liên hệ theo quyền được cấp.
5. Ở điểm cuối, thực hiện bước hoàn thành theo nút hiển thị. Kiểm trạng thái chuyến đã hoàn thành và lịch sử có mốc cuối.

Các nút tiến độ chỉ cho bước tiếp theo hợp lệ. Nếu người khác đã thao tác và màn báo dữ liệu cũ, tải lại trước khi tiếp tục. Xác nhận tiến độ ghi thời điểm thực tế thao tác và ảnh hưởng vòng đời đơn phù hợp, vì vậy chỉ thực hiện trên chuyến thực hành khi đang học. Sản phẩm không thu vị trí GPS trực tiếp từ điện thoại.

## 13. Xem dashboard và đối soát báo cáo

1. Mở Dashboard hoặc `/operations/reports` khi có quyền. Chọn khoảng ngày và nhà xe cần xem. Ngày báo cáo tính theo ngày khởi hành của chuyến tại Việt Nam.
2. Bấm Xem báo cáo để áp dụng bộ lọc. Đổi nhà xe trên form chỉ chuẩn bị tiêu chí mới, cần bấm xem để tính lại số liệu.
3. Chuyển các tab tổng hợp/chi tiết đang có trên màn. Đọc tên chỉ tiêu và phạm vi trước khi so sánh.
4. Đối chiếu giá trị đơn, tiền đã thu, tiền đã hoàn và tiền ròng. Tiền ròng bằng đã thu trừ đã hoàn, khác giá trị tất cả đơn. “Lượt chỗ” có thể cộng qua nhiều chặng/đơn, khác số ghế vật lý của xe.
5. Khi số liệu khác kỳ vọng, dùng cùng nhà xe, ngày khởi hành và trạng thái để mở lại đơn/chuyến cụ thể. Kiểm các sự kiện thu/hoàn và hủy trước khi kết luận sai báo cáo.

## 14. Quản trị tài khoản và quyền

1. Mở `/operations/accounts`, tìm username, mở chi tiết để kiểm thông tin, trạng thái và quyền hiệu lực.
2. Tạo tài khoản bằng form quản trị, nhập username, tên, email liên hệ và các trường bắt buộc. Tài khoản mới theo quy trình quản trị có thể bị yêu cầu đổi mật khẩu khi đăng nhập.
3. Mở `/operations/access` để xem vai trò và chức năng. Luồng cấp quyền là người dùng → vai trò → chức năng → menu/API. Chọn role, gán chức năng rồi kiểm các tài nguyên của chức năng đó.
4. Ở phần tài nguyên `/operations/access/resources`, xem endpoint thu thập từ ứng dụng. Một endpoint mới được nhận diện chưa tự trở thành quyền. Chỉ gán method/route đúng nhu cầu vào chức năng được phép quản lý.
5. Gán vai trò toàn cục và phạm vi nhà xe cho tài khoản theo chức năng. Phạm vi scoped cần gán global của cùng role; dùng bộ chọn trên màn để tránh sai liên kết.
6. Kiểm lại bằng phiên riêng của tài khoản đích. Xem đúng menu, đúng nhà xe, đúng thao tác và trường hợp bị từ chối. Khi thu hồi, tải lại màn theo phiên hiện hành để kiểm quyền vừa thay.

Quản trị chỉ cấp được trong trần quyền của mình. “Mọi phạm vi” thuộc vai trò đã cấp endpoint, không phải cách bỏ kiểm quyền. Khi chưa rõ, giữ nguyên bộ quyền nền và nhờ người phụ trách auth rà trước khi lưu. Thay mật khẩu, vô hiệu hóa tài khoản hoặc thu hồi quyền có thể làm phiên đang dùng mất hiệu lực.

## 15. Dùng tài liệu API và nhật ký để hỗ trợ

1. Tài khoản có grant quản trị tài liệu mở Tài liệu API tại `/docs`. Chọn nhóm API, đọc input/output và lỗi. Nếu dùng Try it out, chọn môi trường học vì API ghi cũng làm thay đổi dữ liệu.
2. Người có quyền nhật ký mở `/operations/api-journal`, lọc thời gian, endpoint, trạng thái hoặc traceId theo form rồi tìm.
3. Mở một request để xem thời gian xử lý, người dùng đã xác thực nếu có và snapshot đã lọc. Nhật ký không hiển thị toàn bộ nội dung gốc hoặc credential.
4. Dùng Copy cURL/Postman để lấy mẫu request khi hỗ trợ kỹ thuật. Token/body nhạy cảm là placeholder, người thử cần tự điền trong môi trường được phép. Kiểm endpoint và method trước chạy, đặc biệt các lệnh ghi.
5. Nếu cần gửi lỗi cho nhóm, cung cấp thời điểm, username, màn hình, thao tác, mã lỗi/traceId và ảnh đã che thông tin nhạy cảm. Ghi rõ kết quả mong đợi và thực tế, không gửi mật khẩu/cookie/token.

## 16. Tình huống lỗi và cách xử lý

| Hiện tượng | Cách xử lý |
|---|---|
| Đăng nhập sai | Kiểm username, Caps Lock và mật khẩu được cấp. Ngừng thử liên tiếp khi báo khóa, chờ hạn hoặc liên hệ quản trị |
| Mất phiên hoặc 401 | Lưu lại nội dung đang nhập ở nơi riêng phù hợp, đăng nhập lại và mở lại dữ liệu hiện hành trước thao tác |
| Không có quyền hoặc 403 | Kiểm đang dùng đúng tài khoản/nhà xe. Nhờ quản trị đối chiếu menu, API và scope, nêu mã lỗi/traceId |
| Không tìm thấy bản ghi | Kiểm mã, phạm vi và trạng thái. Bản ghi ngoài quyền cũng có thể được trả như không tồn tại |
| Dữ liệu cũ hoặc 409 | Tải lại bản ghi, đọc thay đổi/lịch sử rồi quyết định thao tác lại. Không bấm lưu lặp liên tục |
| Không đủ chỗ | Tải lại chuyến, giảm số chỗ hoặc chọn chuyến khác sau khi trao đổi khách. Chờ xử lý chưa bảo đảm giữ chỗ |
| Xe/tài xế trùng lịch | Kiểm giờ đầu/cuối và chuyến đã phân công, chọn tài nguyên hoặc lịch khác phù hợp |
| Select con rỗng | Chọn cha trước, chờ tải xong, kiểm trạng thái sử dụng. Đổi cha cần chọn lại các trường con |
| Mất mạng sau khi lưu | Tìm lại bản ghi/mã đơn và lịch sử để xác định lần trước đã thành công chưa, rồi mới quyết định gửi lại |
| Không tải tile bản đồ | Kiểm mạng, giữ địa chỉ/draft, thử lại khi kết nối ổn định. Kiểm tọa độ thực tế trước lưu |
| 429 hoặc 503 | Chờ rồi thực hiện lại thao tác đọc. Với thao tác ghi, kiểm lịch sử trước; báo quản trị nếu lặp lại |
| Báo cáo bằng 0 | Kiểm kỳ ngày, nhà xe và trạng thái. Dùng cùng tiêu chí với danh sách đơn/chuyến để đối soát |
| Không thấy nhật ký request | Kiểm bộ lọc và thời gian lưu. Nhật ký có retention và có thể mất dòng khi DB/pool lỗi |

## 17. Bài thực hành trọn luồng cho người mới

Chuẩn bị môi trường học có dữ liệu, một chuyến tương lai còn chỗ và ba phiên đăng nhập khách/điều hành/tài xế. Ghi lại nhà xe, ngày, mã chuyến và mã đơn để mọi người đối chiếu cùng dữ liệu.

1. Khách tìm đúng hai tỉnh, chọn ngày còn lịch và hai chỗ. Mở báo giá, kiểm các điểm, nhập liên hệ giả lập được cho phép và tạo đơn. Kết quả cần có mã đơn PENDING trong Đơn của tôi.
2. Điều hành đúng nhà xe tìm mã đó, kiểm liên hệ và xác nhận. Kết quả là CONFIRMED. Khách tải lại thấy đã xác nhận, chỗ trên chặng giảm tương ứng.
3. Điều hành ghi nhận một khoản thu sau khi giả lập giao nhận tiền theo bài học. Mở lại đơn kiểm tổng thu và một sự kiện PAYMENT tương ứng.
4. Tài xế mở đúng chuyến, kiểm danh sách đón/trả. Khi thực hành giờ phù hợp, thực hiện tiến độ đúng thứ tự tới điểm trả. Kiểm lịch sử và trạng thái sau khi hoàn tất chặng/chuyến.
5. Người phụ trách báo cáo chọn đúng ngày khởi hành và nhà xe, bấm xem. Đối soát đơn và khoản thu vừa ghi với chỉ tiêu tương ứng.
6. Với một đơn thực hành khác, thử hủy trước khi điểm đón rời. Kiểm trả chỗ, rồi giả lập hoàn và ghi REFUND nếu đơn đã thu tiền. Đối soát khoản hoàn còn thiếu.
7. Cuối bài, ghi kết quả và lỗi theo mẫu ở mục 15. Đăng xuất các phiên. Người quản trị xử lý dữ liệu thực hành theo quy trình môi trường học, không xóa lịch sử giao dịch tùy ý.

## 18. Tra cứu tiếp

- [Chạy local và danh sách tài khoản](CHAY_LOCAL.md) dành cho người chuẩn bị môi trường.
- [Tổng quan hệ thống](TONG_QUAN_HE_THONG.md) giải thích các khối nghiệp vụ.
- [Từ điển bảng và cột](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md#từ-điển-bảng-và-cột) giúp hiểu dữ liệu phía sau các màn hình.
- [Kịch bản trình bày và hỏi đáp](../05-ke-hoach/DEMO_VA_HOI_DAP.md) dành cho buổi demo đồ án.
- [Trạng thái nghiệm thu](../05-ke-hoach/TIEN_DO_VA_KE_HOACH_NHOM.md) ghi phạm vi và các kiểm tra đã thực hiện.
