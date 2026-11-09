# Test case nghiệm thu VietRoute

Bộ này có **40 kịch bản** cho tài khoản, tổng quan, lịch, liên hệ và luồng sản phẩm. Ngày cập nhật 09/11/2026, đối chiếu source DAILY02/BOOKINGCONTACT01 đã phát hành tại `2d87633`, tài liệu nghiệm thu tại `9c82e04`. Số test tự động gần nhất là **222 Java, gồm 85 PostgreSQL, và 80 Node**. Số test tự động và số kịch bản thủ công là hai tập khác nhau.

## Cách chuẩn bị và ghi kết quả

1. Dùng PostgreSQL demo/test riêng đã áp dụng 11 migration và bộ init đầy đủ theo [chạy local](../01-bat-dau/CHAY_LOCAL.md). Thao tác tạo/sửa đơn, tiền, tiến độ, grant hoặc account thực hiện trên môi trường được phép ghi.
2. Ghi commit ứng dụng, tên môi trường, trình duyệt, kích thước màn hình, locale, thời điểm Việt Nam, người kiểm và mã dữ liệu. Mỗi tác nhân dùng phiên/profile riêng để tránh chung cookie.
3. Chuẩn bị hai nhà xe A/B, hai khách C/D, điều hành của A/B và hai tài xế có assignment riêng. Dữ liệu liên hệ dùng tên/số minh họa. Nhận password riêng theo hướng dẫn local, không đưa vào biên bản.
4. Với luồng đặt, chọn chuyến OPEN tương lai còn chỗ và điểm đón chưa rời. Để kiểm tiến độ, chuẩn bị chuyến trong cửa sổ START 30 phút. Ca đồng thời, lỗi DB và thời gian dùng regression/fixture test, không đổi đồng hồ production.
5. Sau mỗi thao tác ghi, đọc lại state/version, tồn chỗ từng đoạn, tổng tiền và history. Sau lỗi, kiểm cả dữ liệu không bị ghi một phần. Dùng đơn khác cho nhánh hủy/hoàn và nhánh đi hết chuyến.
6. Điền **Đạt**, **Không đạt**, **Chưa chạy** hoặc **Bị chặn** kèm kết quả thực tế. Một mục có regression đạt không tự trở thành toàn bộ ca UI đạt. Tài liệu này không ký nghiệm thu thay người kiểm.

| Ô biên bản | Nội dung cần điền |
|---|---|
| Commit / môi trường | ____________________ |
| Người kiểm / thời điểm Việt Nam | ____________________ |
| Trình duyệt / viewport / locale | ____________________ |
| Mã ca / mã chuyến / mã đơn | ____________________ |
| Kết quả thực tế / trạng thái | ____________________ |
| Bằng chứng / traceId / lỗi / lần kiểm lại | ____________________ |

## Kịch bản chi tiết

Các bước dưới đây là quy trình để nghiệm thu. Kết quả đã thực sự quan sát nằm trong mục bằng chứng ở cuối. Trước khi chạy một ca, đọc [hướng dẫn sử dụng](../01-bat-dau/HUONG_DAN_SU_DUNG.md) để chọn đúng màn và tác nhân.


## Tài khoản và đăng nhập

### TK-01 — Tài khoản mới và email liên hệ

Điều kiện: DB demo riêng; quản trị có quyền tài khoản, username mới chưa sử dụng và một phiên riêng của tài khoản đích.

1. Tạo username mới với email liên hệ hợp lệ và mật khẩu tạm.
2. Đăng nhập bằng username ở phiên đích, đổi mật khẩu rồi mở một chức năng đã cấp.

Kết quả mong đợi: Sau khi đổi mật khẩu, mở được chức năng đã cấp. Email vẫn hiển thị trong hồ sơ và không có bước xác minh

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TK-02 — Account active chưa xác minh email

Điều kiện: DB demo riêng; quản trị có quyền tài khoản, username mới chưa sử dụng và một phiên riêng của tài khoản đích.

1. Dùng fixture test có email_verified_at rỗng, account active và mật khẩu đã đổi.
2. Đăng nhập, mở chức năng đã cấp và gọi một endpoint thiếu grant ở cả hai Spring context.

Kết quả mong đợi: Đăng nhập và truy cập API/menu được cấp. Truy cập endpoint thiếu quyền vẫn nhận 403

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TK-03 — Email hợp lệ và bỏ trống

Điều kiện: DB demo riêng; quản trị có quyền tài khoản, username mới chưa sử dụng và một phiên riêng của tài khoản đích.

1. Mở form tạo account, nhập email sai định dạng và gửi.
2. Sửa thành email hợp lệ rồi lưu. Tạo account khác với email bỏ trống.

Kết quả mong đợi: Báo lỗi đúng trường nếu sai. Email hợp lệ hoặc bỏ trống đều tạo được tài khoản và không chuyển đến màn xác minh

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TK-04 — Account tài xế và phân công

Điều kiện: DB demo riêng; quản trị có quyền tài khoản, username mới chưa sử dụng và một phiên riêng của tài khoản đích.

1. Tạo account tài xế, hoàn tất đổi mật khẩu tạm, gắn hồ sơ và scope.
2. Phân công một chuyến hợp lệ rồi đăng nhập tài xế và mở Chuyến được giao.

Kết quả mong đợi: Tài xế dùng được tài khoản và thấy đúng chuyến đã giao, không phải xác minh email

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

## Hai màn tổng quan

### TQ-01 — Dashboard theo vai trò

Điều kiện: Bốn phiên riêng admin, điều hành, tài xế và khách; đã ghi nhà xe, đơn và chuyến thuộc từng người.

1. Lần lượt mở /dashboard bằng từng tác nhân, đổi vi/en và bấm Làm mới.
2. So sánh chỉ tiêu và lối tắt với đơn/chuyến trong phạm vi của từng người.

Kết quả mong đợi: Thẻ, lối tắt và số liệu tương ứng với quyền. Tải lại và xử lý lỗi API hiển thị rõ

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TQ-02 — Tổng quan quản trị và URL trực tiếp

Điều kiện: Bốn phiên riêng admin, điều hành, tài xế và khách; đã ghi nhà xe, đơn và chuyến thuộc từng người.

1. Mở /admin bằng người có grant và kiểm các nhóm tác vụ.
2. Ở phiên thiếu grant, nhập trực tiếp /admin và API tương ứng.

Kết quả mong đợi: Nhóm tác vụ hiển thị rõ theo quyền. Phiên thiếu grant bị từ chối

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TQ-03 — Bố cục và focus trên hai màn tổng quan

Điều kiện: Bốn phiên riêng admin, điều hành, tài xế và khách; đã ghi nhà xe, đơn và chuyến thuộc từng người.

1. Mở /dashboard và /admin ở desktop rồi 390 px.
2. Dùng Tab và Enter qua các nút, kiểm focus và cuộn trang.

Kết quả mong đợi: Các thẻ tự xuống hàng, focus rõ, không cuộn ngang toàn trang

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

## Tìm chuyến và lịch hằng ngày

### LC-01 — Tìm chuyến không nhập bộ lọc

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Mở /journeys khi chưa đăng nhập, bỏ trống tuyến/ngày/giờ.
2. Chuyển trang kết quả, đổi số dòng rồi mở chi tiết một kết quả.

Kết quả mong đợi: Danh sách tự hiện các chặng còn nhận đặt trong 31 ngày. Các trang giữ cùng bộ lọc

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-02 — Tìm đúng ngày và giờ

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Chọn Hà Nội, Hải Phòng, ngày mai, 1 chỗ và Duy Long.
2. Lọc 09:00–10:00, mở lượt 09:30 và báo giá.

Kết quả mong đợi: Có lượt 09:30 nếu chưa tới giờ, chưa hủy và còn đủ chỗ. Mở chi tiết thấy còn chỗ, có thể gửi đơn

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-03 — Lọc sai giờ và đổi chiều

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Giữ ngày và chiều LC-02, đổi khung 10:00–11:00.
2. Đảo chiều Hải Phòng–Hà Nội, chọn khung 11:30–12:30 và tìm lại.

Kết quả mong đợi: Không thấy lượt 09:30 ở bộ lọc sai giờ hoặc sai chiều. Hải Phòng → Hà Nội có lượt 05:00 và 12:00 khi lọc giờ phù hợp

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-04 — Ngày xa và ID theo ngày

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Chọn ngày thứ 45 và khung 15:30. Tìm lặp hoặc gửi hai tìm kiếm đồng thời.
2. Ghi tripId, đổi sang ngày kế tiếp và so sánh ID, giờ và tồn chỗ.

Kết quả mong đợi: Cùng một ID chuyến cho khung giờ đó. Ngày khác có ID, chỗ và đơn riêng

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-05 — Chuyến hủy sau lần tìm

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Mở chi tiết một chuyến OPEN còn chỗ, giữ màn khách.
2. Điều hành hủy chuyến trên DB demo, khách bấm Làm mới và thử API gửi đơn với ID cũ.

Kết quả mong đợi: Báo chặng không nhận đặt, ẩn nút đặt. Gửi trực tiếp yêu cầu đặt bị từ chối

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-06 — Bật lịch và xung đột tài nguyên

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Tạo, mở bán và bật lịch cho một chuyến dưới 24 giờ, có grant schedule và scope.
2. Thử bật một nguồn khác trùng xe hoặc tài xế cùng khoảng giờ, rồi thử bằng người thiếu quyền.

Kết quả mong đợi: Lịch hợp lệ sinh chuyến, lịch chồng báo xung đột. URL bật lịch yêu cầu grant và scope tương ứng

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-07 — Dừng lịch và chuyến con đã hủy

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Hủy một chuyến con đã sinh rồi tìm lại đúng ngày đó.
2. Tắt lịch nguồn, tìm ngày chưa sinh và kiểm lịch sử những lượt đã công bố.

Kết quả mong đợi: Chuyến đã hủy không xuất hiện lại. Tắt lịch không tạo ngày mới và các chuyến đã công bố vẫn có lịch sử riêng

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-08 — Tồn chỗ độc lập từng ngày

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Ghi tồn chỗ ngày A/B của cùng nguồn. Tạo và xác nhận 2 chỗ ở ngày A.
2. Đọc lại tồn từng đoạn và lịch sử đơn ở cả hai ngày.

Kết quả mong đợi: Tồn chỗ giảm đúng các đoạn của chuyến A. Chuyến B không bị trừ chỗ

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-09 — Khởi tạo lại và bảo toàn thay đổi

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Trên DB test, chạy init đầy đủ rồi ghi mã/ID, giờ, số đơn, hash mật khẩu và mapping.
2. Chạy lại init hai lần, kiểm bốn nguồn Duy Long và các dữ liệu đã đổi hoặc thu hồi.

Kết quả mong đợi: Chỉ có một nguồn cho mỗi khung giờ 05:00, 09:30, 12:00, 15:30. Không tạo lặp chuyến, đơn hoặc cấp lại quyền đã thu hồi

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LC-10 — Bảy ngày của bảy nhà xe

Điều kiện: DB demo có 26 nguồn lịch dưới 24 giờ và bảy nhà xe. Dùng ngày mai hoặc ngày thứ 60 theo đồng hồ Việt Nam. Chuyến thử phải có đủ chỗ và điểm đón chưa rời.

1. Trên DB mới đã init, tìm từng ngày từ ngày thứ 60 đến ngày thứ 66.
2. Tìm lại giữ bộ lọc. Đếm 26 lượt theo code nguồn, đủ 7 nhà xe. Báo giá 1 lượt của mỗi nhà xe ngày đầu.

Kết quả mong đợi: Mỗi ngày có 26 lượt cố định thuộc đủ bảy nhà xe. Tìm lại giữ ID. Mở báo giá một lượt của từng nhà xe và kiểm còn chỗ, nhận đặt. Hai mẫu tuyến 34 giờ cần ca xe/tài xế luân phiên

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

## Liên hệ tài xế từ đơn của khách

### LH-01 — Liên hệ ở đơn đã xác nhận

Điều kiện: Hai khách A/B, điều hành và tài xế đúng nhà xe; đơn của A, chuyến OPEN và hai phân công hợp lệ không chồng lịch. Dùng tên và điện thoại minh họa.

1. A mở chi tiết đơn WEB PENDING rồi điều hành xác nhận.
2. A đóng/mở lại chi tiết khi chuyến OPEN, kiểm tên, số điện thoại, biển số và href tel:.

Kết quả mong đợi: PENDING không có liên hệ tài xế. Sau xác nhận, chi tiết có tên, SĐT và biển số, bấm Gọi tài xế dùng `tel:`

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LH-02 — Chủ đơn và dữ liệu công khai

Điều kiện: Hai khách A/B, điều hành và tài xế đúng nhà xe; đơn của A, chuyến OPEN và hai phân công hợp lệ không chồng lịch. Dùng tên và điện thoại minh họa.

1. B mở ID đơn CONFIRMED của A qua API chi tiết riêng.
2. Kiểm JSON danh sách đơn, tìm chuyến công khai và chi tiết staff đối với ba field liên hệ mới.

Kết quả mong đợi: Ngoài chủ đơn trả 404; danh sách/tìm công khai không trả liên hệ tài xế

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LH-03 — Liên hệ sau đổi phân công

Điều kiện: Hai khách A/B, điều hành và tài xế đúng nhà xe; đơn của A, chuyến OPEN và hai phân công hợp lệ không chồng lịch. Dùng tên và điện thoại minh họa.

1. Điều hành đổi xe/tài xế bằng chức năng phân công hợp lệ.
2. A đóng/mở lại chi tiết đơn. So sánh liên hệ và biển số với snapshot hiện hành của chuyến.

Kết quả mong đợi: Liên hệ lấy từ snapshot phân công hiện hành, không giữ dữ liệu cũ trên modal

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LH-04 — Ẩn liên hệ theo vòng đời

Điều kiện: Hai khách A/B, điều hành và tài xế đúng nhà xe; đơn của A, chuyến OPEN và hai phân công hợp lệ không chồng lịch. Dùng tên và điện thoại minh họa.

1. Dùng các đơn riêng cho nhánh hủy, từ chối, hoàn tất và các chuyến kết thúc/hủy.
2. Đọc lại chi tiết. Mở liên tiếp modal đơn có/không có liên hệ, kiểm dữ liệu cũ đã được dọn.

Kết quả mong đợi: Không trả liên hệ tài xế. SĐT nhà xe vẫn dùng để hỗ trợ

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### LH-05 — Tài xế xem liên hệ khách đúng chuyến

Điều kiện: Tài xế X đang được gán chuyến T, tài xế Y thuộc chuyến khác. T có đơn PENDING, CONFIRMED và COMPLETED.

1. X mở chi tiết T và danh sách khách đón/trả, đối chiếu số chỗ và liên hệ minh họa.
2. Y mở cùng ID. Đổi assignment hoặc thu hồi scope của X rồi đọc lại.

Kết quả mong đợi: X thấy tên, điện thoại và điểm đón/trả của khách CONFIRMED/COMPLETED đúng chuyến. Khách PENDING không xuất hiện trong danh sách phục vụ. Người không còn assignment/scope bị từ chối.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

## Luồng sản phẩm và các trường hợp lỗi

### AT-01 — Đăng nhập sai và khóa tạm thời

Điều kiện: Account test active riêng, không dùng account đang vận hành.

1. Nhập sai password 5 lần và ghi phản hồi từng lần.
2. Thử password đúng khi đang khóa. Đối chiếu thời hạn khóa bằng regression có Clock test.

Kết quả mong đợi: Account khóa 15 phút sau 5 lần sai. Bộ đếm tồn tại dù request trả lỗi. Hết khóa có thể đăng nhập đúng theo trạng thái hiện hành.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### AT-02 — Vòng đời phiên và CSRF

Điều kiện: Account test có hai phiên, endpoint mutation dùng cookie.

1. Gọi mutation thiếu CSRF, sau đó dùng CSRF hợp lệ.
2. Đăng xuất hoặc đổi password ở phiên A, dùng lại token cũ ở phiên B.

Kết quả mong đợi: Mutation thiếu/sai CSRF bị từ chối. TokenVersion cũ mất hiệu lực theo vòng đời. UI có đường đăng nhập lại khi nhận 401.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### PQ-01 — Giới hạn nhà xe và chủ đơn

Điều kiện: Điều hành A/B thuộc hai nhà xe, khách C/D có đơn riêng.

1. A đọc/sửa chuyến hoặc đơn nhà xe B bằng ID trực tiếp.
2. C đọc đơn của D. So sánh count và page của người chỉ có scope A.

Kết quả mong đợi: Dữ liệu ngoài phạm vi không xuất hiện trong count/page. API trực tiếp từ chối theo contract. Hành vi sở hữu đơn độc lập với tên account.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### PQ-02 — Thu hồi quyền và endpoint chưa cấp

Điều kiện: DB demo có quản trị đủ trần ủy quyền và user phụ; biết route page/API chính xác.

1. Thu hồi grant rồi gọi lại request mới từ phiên đang mở.
2. Thử endpoint chưa gán, role inactive và nhập URL trực tiếp.

Kết quả mong đợi: Request tiếp theo phản ánh quyền hiện hành. Endpoint chưa gán mặc định từ chối. Menu ẩn không thay phép kiểm backend. Mỗi Spring context đều được kiểm.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DM-01 — Cây danh mục và tìm tổ tiên

Điều kiện: Đủ tỉnh/xã/địa điểm mẫu, user có quyền danh mục.

1. Mở tab hành chính, xổ một tỉnh, tìm mã xã 26824 rồi bỏ tìm kiếm.
2. Đổi tỉnh trong form địa điểm và quan sát lựa chọn xã/điểm con.

Kết quả mong đợi: Tải con đúng cha, kết quả tìm có tổ tiên và mục khớp, bảng cây không phân trang. Đổi cha dọn lựa chọn con cũ.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DM-02 — Validation danh mục và giữ bản nháp

Điều kiện: DB demo, một mã danh mục đã tồn tại.

1. Mở modal thêm/sửa, nhập mã trùng hoặc sai thuộc tính, bấm Lưu.
2. Sửa trường lỗi rồi gửi lại. Thử lỗi mạng khi đang gửi.

Kết quả mong đợi: Lỗi chỉ đúng trường, giữ nội dung và modal. Sửa hợp lệ lưu một lần. Nút đang gửi bị khóa, không tự replay mutation.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### NG-01 — Xe, tài xế và tài khoản được gắn

Điều kiện: Nhà xe có loại xe active, tài xế có hồ sơ/account và scope hợp lệ.

1. Tạo hoặc sửa xe với capacity hợp lệ, thử biển số trùng.
2. Gắn account tài xế rồi thử chọn xe/tài xế khác nhà xe hoặc ngừng sử dụng.

Kết quả mong đợi: Dữ liệu hợp lệ thuộc đúng nhà xe. Biển số trùng, tài nguyên inactive hoặc sai quan hệ bị từ chối. Hồ sơ tài xế và account là hai quan hệ cần đủ.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TC-01 — Tuyến nhiều điểm và snapshot chuyến

Điều kiện: Tuyến demo A–B–C, offset và giá cộng dồn hợp lệ.

1. Tạo chuyến từ tuyến rồi ghi điểm/giờ/giá snapshot.
2. Sửa mẫu tuyến, đọc lại chuyến đã công bố; thử offset đảo thứ tự hoặc giá giảm.

Kết quả mong đợi: Chuyến đã tạo giữ snapshot. Mẫu không hợp lệ bị từ chối. Giá A–C bằng chênh lệch giá cộng dồn, không cộng trùng giá từng cặp.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DC-01 — Tạo WEB và một đơn đang hoạt động

Điều kiện: Khách không còn WEB PENDING/CONFIRMED, chuyến OPEN còn 2 chỗ, liên hệ minh họa.

1. Từ tìm chuyến mở form, ghi quote rồi gửi 2 chỗ một lần.
2. Mở Đơn của tôi và thử gửi một WEB active khác.

Kết quả mong đợi: Có một đơn PENDING của đúng khách, giá do server chốt, chưa tăng reserved_seats. WEB active thứ hai bị từ chối. Xử lý/hủy hợp lệ đơn cũ trước lần tiếp.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DC-02 — Tiếp nhận khách đặt qua điện thoại

Điều kiện: Điều hành có scope, chuyến còn chỗ, tên và số điện thoại minh họa.

1. Trong Quản lý đơn, chọn Thêm mới và điền chuyến, điểm, số chỗ, liên hệ.
2. Lưu rồi xác nhận đơn theo luồng. Kiểm nguồn và tồn chỗ.

Kết quả mong đợi: Đơn có nguồn PHONE, không yêu cầu khách có account. Liên hệ vẫn tuân validation hiện hành. Chỉ khi CONFIRMED mới giữ chỗ.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DC-03 — Cạnh tranh chỗ và gửi lặp

Điều kiện: DB test riêng, chặng còn 1 chỗ, nhiều đơn PENDING và công cụ regression.

1. Xác nhận đồng thời nhiều đơn tranh chỗ cuối.
2. Replay tạo đơn cùng requestKey/nội dung, rồi đổi nội dung với cùng key.

Kết quả mong đợi: Không vượt sức chứa trên bất kỳ đoạn nào. Request lặp hợp lệ không tạo đơn thứ hai. Key dùng lại cho nội dung khác gây xung đột. Lỗi rollback cả chỗ, state và history.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### DC-04 — Hủy đơn, trả chỗ và giữ lịch sử tiền

Điều kiện: Hai đơn demo riêng, một PENDING và một CONFIRMED đã thu một phần, điểm đón chưa rời.

1. Hủy từng đơn và ghi lý do. Đọc tồn chỗ trước/sau.
2. Gửi lại thao tác theo contract, rồi thử hủy sau khi điểm đón đã rời.

Kết quả mong đợi: PENDING không có chỗ để trả. CONFIRMED trả chỗ đúng một lần. Tiền đã thu giữ lịch sử, hoàn là bước riêng. Quá điều kiện hủy bị từ chối.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TT-01 — Giới hạn thu và hoàn thủ công

Điều kiện: Đơn CONFIRMED tổng 360.000đ, DB demo và đơn riêng cho nhánh hủy.

1. Ghi thu 200.000đ rồi 160.000đ; thử thu vượt tổng và replay cùng requestKey.
2. Hủy hợp lệ, ghi hoàn 200.000đ rồi 160.000đ; thử hoàn vượt đã thu.

Kết quả mong đợi: Tổng thu tối đa 360.000đ, replay không cộng tiền lần hai. Tổng hoàn tối đa số đã thu. REFUND áp dụng đơn CANCELLED, PAYMENT áp dụng CONFIRMED/COMPLETED. History và tổng tiền thống nhất.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### TX-01 — Tiến độ tài xế và hoàn tất khách

Điều kiện: Tài xế đúng assignment, chuyến nhiều điểm, Clock test hoặc chuyến vào cửa sổ START.

1. Thử START sớm quá 30 phút, sai version và ARRIVE/DEPART sai thứ tự.
2. Thực hiện START, ARRIVE, DEPART đúng thứ tự tới điểm trả rồi COMPLETE.

Kết quả mong đợi: Bước sai bị từ chối. Mốc dùng giờ server. Đơn CONFIRMED hoàn tất ở điểm trả phù hợp; PENDING lỡ điểm đón được xử lý theo luồng. Trạng thái và history cùng transaction.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### BC-01 — Đối soát báo cáo và dashboard

Điều kiện: Ghi danh sách chuyến/đơn/thu/hoàn nền trong cùng scope và kỳ ngày khởi hành Việt Nam.

1. Lọc cùng kỳ/nhà xe ở báo cáo, so sánh số chuyến, đơn, confirmedSeats và tiền.
2. Đổi sang kỳ không dữ liệu và phiên scope khác.

Kết quả mong đợi: Không nhân đôi chuyến khi join đơn. Giá trị đơn, đã thu, đã hoàn, ròng và nợ hoàn có định nghĩa riêng. Kỳ rỗng trả 0, actor chỉ thấy phạm vi được cấp.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### NK-01 — Nhật ký và mẫu debug đã lọc

Điều kiện: User có grant page/API journal, một request có traceId và user thiếu grant.

1. Lọc request, mở chi tiết, xuất cURL/Postman và kiểm placeholder.
2. Kiểm JSON không có password/token/cookie/liên hệ; thử đọc bằng user thiếu quyền.

Kết quả mong đợi: Chỉ projection whitelist xuất hiện. Origin/context đúng môi trường, credential là placeholder. Quyền page/API đều kiểm. Counter missed-write công bố giới hạn khi ghi log thất bại.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### UX-01 — Lỗi, ngôn ngữ và thao tác bàn phím

Điều kiện: Hai locale vi/en, desktop/390 px, modal có nội dung đang nhập.

1. Gây lỗi validation và lỗi API trong DB demo, dùng Tab/Enter/Escape.
2. Kiểm thông báo, focus, bản nháp và đường login khi nhận 401.

Kết quả mong đợi: Thông báo đúng locale, không mất draft, focus rõ và không cuộn ngang toàn trang. Modal/nút không tự gửi lại khi lỗi. Trạng thái đang gửi ngăn thao tác lặp.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

### VH-01 — Init, migration và khôi phục

Điều kiện: PostgreSQL test mới và DB phục hồi riêng, cấu hình init opt-in, backup fixture đã chuẩn bị.

1. Chạy init, ghi số bảng/history, ID và hash; chạy lại sau khi sửa một ghi chú/thu hồi mapping.
2. Khôi phục backup sang DB mới và đối chiếu số dòng, digest cùng UTC. Thử checksum/history lạ trên test.

Kết quả mong đợi: Có 22 bảng, 340 cột vật lý và 11 migration hiện hành. Init lặp giữ dữ liệu/hash/mapping đã thu hồi. Runtime từ chối history/checksum lạ. Backup chỉ được ghi đạt khi phục hồi và đối chiếu thành công.

Thực tế / trạng thái / bằng chứng / người kiểm: ____________________

## Kết quả đã đối chiếu ngày 09/11/2026

Mốc DAILY01 dùng source `93883c0` và deployment `dep-dboautvavr4c73b853o0`. DAILY02 dùng source `2d87633`, đối chiếu bản mới bằng nội dung HTTP và API trên Render. SHA của DAILY02 chưa được đọc trực tiếp từ bảng Render. Các ca thay đổi dữ liệu dùng PostgreSQL riêng hoặc bản phục hồi, không tạo đơn/thu/hoàn thử trên production. Mốc DAILY01 có 16 ca. Bộ hiện tại có 40 kịch bản; bảng bằng chứng bên dưới chỉ ghi phần đã kiểm.

| Phần đã kiểm | Kết quả thực tế | Phạm vi |
|---|---|---|
| TK-02 và grant độc lập | `assignedAdminCanUseGrantedFunctionsWithoutVerifiedEmail` cùng nhóm security đạt | JUnit/MVC, cả hai Spring context |
| TQ-01, TQ-02, phần bố cục TQ-03 | Khách đăng nhập thấy 2 đơn, quản trị có các nhóm tác vụ; hai màn ở desktop/390 px không cuộn ngang | Trình duyệt vi trên DB phục hồi, khách đăng nhập lại trên production |
| LC-01 | Trang tự tải, API trả 574 chặng còn nhận đặt tại thời điểm kiểm | Production, không chọn tuyến/ngày |
| LC-02 | Hà Nội → Hải Phòng ngày 10/11, 09:00–10:00 có `DLG1R26D20261110` lúc 09:30, còn 13 chỗ; quote `accepting=true` | Trình duyệt và API production |
| LC-03 | Khung 10:00–11:00 trả 0; chiều Hải Phòng → Hà Nội ngày 10/11 có lượt 12:00, còn 13 chỗ và quote khả dụng | Trình duyệt/API production |
| LC-04, phần tìm và chống tạo lặp khi gửi lại | Ngày 24/12 sau 45 ngày có `D1559T20261224` lúc 09:30, còn 16 chỗ; gửi lại tìm giữ ID, quote khả dụng | Trình duyệt/API production; ca tạo đơn trên DB test |
| Nâng cấp DB cũ và init | Regression nâng cấp giữ số chuyến/đơn, nhận đủ 4 nguồn. Bổ sung 24 lượt ở production sau backup; trên phục hồi chạy lại thêm 0 | PostgreSQL riêng, sau đó thao tác nâng cấp đã được chủ dự án cho phép |
| Dữ liệu các nhóm màn | Admin, điều hành, tài xế, khách đều đọc được dữ liệu đúng vai trò; các API đã rà trả HTTP 200 | Số lượng và kỳ báo cáo ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md#tài-khoản-tổng-quan-và-lịch-hằng-ngày) |
| LH-01/LH-04, phần hiển thị và dọn modal | Đơn CONFIRMED có tên, điện thoại, biển số minh họa và link `tel:` đúng. Mở tiếp đơn COMPLETED không còn liên hệ từ đơn trước | Trình duyệt Codex vi/en, desktop và 390 px, DB phục hồi riêng; không gọi điện |
| LH-01 đến LH-04, regression API | Kiểm owner/khách khác, trạng thái đơn/chuyến, đổi phân công, không lộ liên hệ mới qua danh sách/public/staff | PostgreSQL test riêng trong bộ 221 ca Java; hai ca client thuộc bộ 80 test Node. Không thay thế nghiệm thu thủ công toàn bộ |
| LC-10 | Từng ngày 08/01–14/01/2027 có đủ 26 lượt cố định thuộc bảy nhà xe. Báo giá một lượt của từng nhà xe ngày 08/01 đều nhận đặt | PostgreSQL test riêng, diễn tập trên bản phục hồi và API công khai Render sau khi bật lịch mẫu |

Các ca còn lại cần người nghiệm thu ghi thao tác, mã dữ liệu và kết quả riêng, đặc biệt bàn phím, đầy đủ vai trò/tiếng Anh, xung đột lịch, tắt lịch và nhánh hủy chuyến con. Regression Java hiện có kiểm quyền, rollback, trạng thái, tồn chỗ và đồng thời không thay thế việc ký nghiệm thu thủ công từng ca. Tài khoản `customduylong` đang có một WEB PENDING cũ; muốn tạo yêu cầu mới phải xử lý/hủy đơn cũ theo luồng của ứng dụng. Đây là quy tắc một đơn WEB active, không phải kết quả tìm chuyến bị sai giờ. Không sửa SQL hoặc tự hủy đơn production để vượt quy tắc.


## Kiểm sau phát hành và giới hạn cần nêu khi thi

- Phiên khách `custom01` trên trình duyệt Render đã tìm Hà Nội–Hải Phòng ngày 10/11/2026, mở lượt Duy Long 09:30 và vào form có giá 180.000đ/chỗ, còn 13 chỗ, nút Gửi yêu cầu. Lượt kiểm này dừng ở form. Tạo/xác nhận và các ca ghi đã kiểm trên PostgreSQL riêng.
- API sau DAILY02 tìm 26 lượt nguồn của bảy nhà xe trong từng ngày 08/01–14/01/2027. Đây là dữ liệu mẫu có lịch lặp. Hai tuyến Bắc–Nam 34 giờ cần ca xe/tài xế luân phiên để khởi hành mỗi ngày.
- UI liên hệ đã kiểm vi/en, desktop/390 px trên bản phục hồi bằng tên/số minh họa. Production kiểm owner, trường bị ẩn và contract Swagger. Tài khoản kiểm ở thời điểm đó chưa có đơn đủ điều kiện hiển thị liên hệ tài xế.
- Gọi tài xế mở liên kết `tel:` và tùy ứng dụng gọi trên thiết bị. Lượt nghiệm thu không thực hiện cuộc gọi. Tài xế chỉ dùng liên hệ trong chuyến được giao và đơn phục vụ phù hợp.
- Danh sách thủ công đủ vai trò, bàn phím, lỗi mạng và các ca mở rộng AT/PQ/DM/NG/TC/DC/TT/TX/BC/NK/UX/VH cần người kiểm điền biên bản thực tế. Trạng thái thiếu bằng chứng giữ Chưa chạy. [Tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) giữ kết quả tự động và PR đối chiếu.
