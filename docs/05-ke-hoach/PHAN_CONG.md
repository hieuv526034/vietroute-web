# Phân công VietRoute — nền tảng và nghiệp vụ

Cập nhật ngày 02/11/2026 theo yêu cầu chủ dự án, áp dụng cách gom package BUSINESSPKG01. Bảng này phân công trách nhiệm bảo trì/nghiệm thu/phát triển tiếp; không ghi lại tác giả source đã làm. Phân công mới thay bảng ngày 22/10 và các dòng chủ trì dự kiến cũ trong tiến độ khi khác nhau. [Tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md) giữ bằng chứng đã làm, [quy hoạch package](../02-phat-trien/QUY_UOC_PHAT_TRIEN.md#package-theo-nhóm-nghiệp-vụ) giữ cấu trúc đích.

## Thông tin thành viên

| Username GitHub | Email tác giả commit |
|---|---|
| `anhhoang104` | `hanh104204@gmail.com` |
| `BangPhamIT` | `bangpham.it@gmail.com` |
| `hiencoi250404-design` | `hiencoi250404@gmail.com` |
| `hieuv526034` | `hieuv526034@gmail.com` |

Dùng đúng bảng này khi tham chiếu thành viên, chuẩn bị phân công/PR và hướng dẫn cấu hình Git. Email cần được chính thành viên thêm/xác minh trong tài khoản GitHub để liên kết commit. Không suy diễn email từ username hoặc tên nhánh; không đổi tác giả các commit cũ theo bảng này.

GPS01 (02/11/2026): đã bỏ theo dõi vị trí thiết bị; BangPhamIT giữ tuyến/chuyến/đặt chỗ và các mốc đến/rời điểm, không nhận GPS/Maps/consent hoặc lịch sử tọa độ. Business còn 14 bảng. Mỗi username vẫn nhận trọn khối, gồm backend/UI/test.

Username GitHub ở bảng trên dùng cho commit/nhánh và phân công, không phải tài khoản ứng dụng. Tài khoản đăng nhập, key mật khẩu, nhà xe/phạm vi và kịch bản thao tác được liệt kê riêng tại [chạy local và bộ dữ liệu](../01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo). Bảng trọn khối dưới đây đã ghi rõ mỗi người làm gì; chỉ ghi hoàn thành khi có bằng chứng commit/PR.

## Phân công hiện hành

| Thành viên | Trách nhiệm chính | Đầu ra chịu trách nhiệm |
|---|---|---|
| hieuv526034 | Auth, common, database, bootstrap và deploy | Tài khoản/quyền/scope, JWT/CSRF, nền MVC/JPA/JDBC/journal; schema/index/FK/migration/seed; CI/build/Render và review tích hợp |
| anhhoang104 | catalog và carrier trọn luồng | Danh mục, tỉnh/địa điểm, nhà xe/xe/hồ sơ tài xế; entity/repo/service/DTO/API/page/UI và test của hai nhóm |
| BangPhamIT | Khối tuyến/chuyến và đặt chỗ: trip + booking | Tuyến/điểm dừng, chuyến/phân công, snapshot, tồn chỗ, tìm chuyến; đặt chỗ WEB/PHONE, trạng thái/thu-hoàn tiền/history; chịu trọn entity/repo/service/DTO/API/PageController/UI và test |
| hiencoi250404-design | Khối báo cáo và tổng quan: report + dashboard | Chỉ tiêu chuyến/đặt chỗ/thu-hoàn, bộ lọc và số liệu theo scope; chịu trọn query/repo/service/DTO/API/PageController/UI, vi/en và test của khối báo cáo |

Mỗi người sở hữu trọn khối nghiệp vụ của mình: PageController/API → DTO → service → repo/entity → UI và kiểm thử. Không chia backend cho một thành viên và UI cùng chức năng cho thành viên khác. Catalog/carrier thuộc anhhoang104; trip/booking thuộc BangPhamIT; report/dashboard thuộc hiencoi250404-design. Hieuv526034 giữ auth/common/DB/bootstrap/deploy và review phần nền; query nghiệp vụ vẫn nằm trong repo do chủ trì khối viết.

## Tài khoản gợi ý để nghiệm thu phần được giao

| Thành viên | Tài khoản ứng dụng dùng thử | Luồng cần đối chiếu |
|---|---|---|
| hieuv526034 | admin, thêm operator01/custom01 để kiểm thiếu quyền | Auth/grant/scope, bootstrap, nhật ký và nền DB; kiểm cả người không có quyền |
| anhhoang104 | admin cho danh mục toàn cục; operator01/operator02 cho hai nhà xe | Danh mục, địa điểm, nhà xe/xe/tài xế; ngoài scope không được đọc/sửa |
| BangPhamIT | operator01 + driver01 + custom01; operator02/driver02 để đối chiếu scope | Lập/điều hành chuyến, đặt/xác nhận/thu-hoàn, tiến độ và owner/assignment |
| hiencoi250404-design | operator03 và admin | Báo cáo nhà xe Miền Trung so với toàn hệ thống, đối soát tiền thực thu/hoàn và dashboard |

Đây là gợi ý kiểm trên bộ dữ liệu giả lập, không gán danh tính Git cho tài khoản ứng dụng hoặc thay ownership source. Không dùng admin để kết luận luồng operator/driver/customer đã được bảo vệ đúng. Mật khẩu khởi tạo và đầy đủ 43 tài khoản xem [hướng dẫn đăng nhập](../01-bat-dau/CHAY_LOCAL.md#tài-khoản-và-dữ-liệu-khởi-tạo); không sao chép credential vào bảng phân công.

## Phạm vi package và bảng

| Package đích | Khối nghiệp vụ | Người chịu trọn khối |
|---|---|---|
| auth | Tài khoản, đăng nhập, role/function/menu/API, scope | hieuv526034 |
| common | Nền MVC/JPA/JDBC, HTTP guard, nhật ký request và component dùng chung | hieuv526034 |
| business/catalog | Danh mục dùng chung, tỉnh/thành, địa điểm | anhhoang104 |
| business/carrier | Nhà xe, phương tiện, hồ sơ tài xế | anhhoang104 |
| business/trip | Tuyến, điểm dừng, chuyến, phân công, tồn chỗ, tìm chuyến | BangPhamIT |
| business/booking | Đặt chỗ WEB/PHONE, xác nhận/hủy, thu/hoàn và lịch sử đơn | BangPhamIT |
| business/report | Báo cáo, số liệu và dashboard | hiencoi250404-design |
| business/bootstrap | Khởi tạo dữ liệu nền xuyên khối | hieuv526034 |

TripSegmentRepo về cùng nhóm trip với entity TripSegment; BookingService gọi cơ chế giữ/trả chỗ trong transaction hiện hành. Mọi thay đổi schema/FK/index/SQL migration do Hieu chốt, nhưng member vẫn chịu trách nhiệm thiết kế nghiệp vụ, query trong repo và ca kiểm của module. Thay package không tự thay URL/grant/schema hoặc chuyển dữ liệu.

## Ranh giới phối hợp

- Auth sở hữu credential, role/function/grant và principal; hồ sơ tài xế thuộc carrier. Anh không tự ghi password hash/role từ form hồ sơ. Luồng cấp account tài xế do auth phối hợp carrier trong đúng scope/transaction.
- anhhoang104 cung cấp dữ liệu danh mục/địa điểm/nhà xe/xe/tài xế. BangPhamIT dùng ID/trạng thái từ DB để lập tuyến/chuyến và thực hiện đặt chỗ, giữ cả UI của khối đó. hiencoi250404-design đọc số liệu nghiệp vụ qua contract/report query đã kiểm scope, đối soát cách tính với BangPhamIT; mọi UI không tính lại tồn chỗ/quyền bằng JavaScript.
- Người chủ trì khối sửa cả PageController/controller/api, template/JavaScript và lớp dữ liệu của chức năng đó. Các file/component dùng chung phối hợp với hieuv526034 bằng PR riêng; không tạo ownership UI chồng lên khối của người khác.
- Common component, Root/Web/Security config, journal, locale/envelope và workflow là phần nền của Hieu. Member đề xuất chỉnh sửa nền qua PR nhỏ, không tự sửa formatter/checker hoặc đưa logic business vào common để giảm số file.
- Hieu phụ trách deploy nhưng chỉ thực hiện dev → main/build production/deploy khi chủ dự án có lệnh phát hành mới. Bảng phân công này không cấp quyền phát hành.

## Thứ tự làm từng mục

1. Source đã gom theo BUSINESSPKG01: năm nhóm nghiệp vụ và bootstrap, 121 file/14 bảng. Kết quả kiểm và giới hạn ở tiến độ; phân công này không xác nhận đã tích hợp dev/main hoặc thành viên đã viết phần được giao.
2. anhhoang104 nghiệm thu catalog/carrier trọn luồng; BangPhamIT nghiệm thu trip/booking trọn luồng; hiencoi250404-design nghiệm thu report/dashboard trọn luồng. Không viết lại bộ CRUD/tồn chỗ đã có chỉ để tạo đóng góp mới.
3. Hieu review schema/security/transaction, Bang + Hien đối soát báo cáo với booking thật trong DB test; mỗi người cung cấp ca lỗi/scope/version/rollback của phần mình.
4. PR theo phần việc vào dev sau checks/review. Nghiệm thu tích hợp đủ luồng trước khi đề nghị phát hành; không lấy kế hoạch hoặc source cục bộ làm bằng chứng đã deploy.

## Nhánh theo người phụ trách

| Người phụ trách | Ví dụ nhánh mới từ dev | Phần việc |
|---|---|---|
| hieuv526034 | hieuv526034/auth-db-deploy | Auth/common/schema/bootstrap/CI và vận hành |
| anhhoang104 | anhhoang104/catalog-carrier | Danh mục/địa điểm/nhà xe/xe/tài xế |
| BangPhamIT | BangPhamIT/trip-booking | Trọn khối chuyến/tồn chỗ/đặt chỗ, gồm UI/API/test |
| hiencoi250404-design | hiencoi250404-design/report-dashboard | Trọn khối báo cáo/dashboard, gồm UI/API/test |

Các tên trên là ví dụ cho công việc tiếp theo, chưa tạo/chuyển nhánh hoặc assign Issue GitHub trong lượt phân công. Nhánh hiện tại hieuv526034/businessdb01-chuan-hoa-nghiep-vu tiếp tục giữ thay đổi chưa commit; không đổi author hoặc tự di chuyển việc đang dở của người khác. Nhánh bangpham.it/trip-booking là nhãn lịch sử, không phải username; nhánh mới dùng đúng BangPhamIT/.

Mỗi người dùng nhánh riêng, PR → CI/review → merge commit vào dev; không commit trực tiếp dev/main, không tự merge các nhánh cũ. Main/deploy chỉ theo lệnh phát hành mới, xem [CONTRIBUTING](../../CONTRIBUTING.md#tích-hợp-dev-và-phát-hành-main-từ-31102026).

## Quy tắc bàn giao và commit

- Mỗi nhóm dùng controller/ cho page, controller/api/ cho REST, entity/, repo/, service/, dto/request/, dto/response/, dto/search/. Chỉ có constant/config/initializer khi cần; initializer ngang service. Class dưới 1000 dòng, không record/var production, một Request/tài nguyên.
- Member chịu cả hợp đồng Swagger, validation, vi/en, quyền/scope/owner và test cho phần mình. Repo giữ query, service giữ nghiệp vụ/transaction; JPA CRUD và JDBC batch/query phức tạp cùng rollback. Không query/save trong vòng lặp dữ liệu.
- Ghi rõ đã làm/còn dở/test/giới hạn và commit đối chiếu trong PR/tiến độ. Commit/PR tiếng Việt, identity đúng người; không sửa lịch sử author để khớp bảng phân công.
- Báo cáo/slide chỉ ghi đóng góp có commit/PR/bằng chứng; mỗi người cung cấp luồng, bảng, SQL, ảnh và ca kiểm của phần mình. Việc nhận bảo trì không biến source đã có thành phần mình viết.

## Phần nền tảng đã có trên main

hieuv526034 đã triển khai nền tảng tại mốc b1f3cde sau PR #13 và các đợt nghiệp vụ tiếp theo theo PR trong tiến độ. Catalog/carrier (PR #109), địa điểm (PR #102) và các module đã có vẫn giữ tác giả commit thực tế. Không gán lại phần Hieu đã viết cho Anh/Bang/Hien theo trách nhiệm mới. Các chức năng job/mail/media/metrics/activity ở mốc lịch sử đã được COMMON01 loại bỏ trong source hiện hành trên nhánh; không phục hồi theo backlog cũ.

Phân công này áp dụng cho nghiên cứu/nghiệm thu/phát triển tiếp; không xác nhận các thành viên đã hoàn thành phần được giao hoặc main/Render đã nhận source mới. Trạng thái, bằng chứng và phần chưa tích hợp nằm ở [tiến độ](TIEN_DO_VA_KE_HOACH_NHOM.md).
