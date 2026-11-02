# Tổng quan VietRoute cho thành viên mới

## Hệ thống hiện hành

Java 17+, Spring MVC 6/Security, Thymeleaf, PostgreSQL, JPA/Hibernate và JDBC; đóng WAR cho Tomcat 10.1, không Spring Boot. Auth quản lý tài khoản/role/function/endpoint và scope; business gồm danh mục, địa điểm, nhà xe/xe/tài xế, tuyến, chuyến, đặt chỗ, báo cáo và tiến độ chuyến theo mốc tài xế.

Auth có 40 file Java/tám bảng; business 121 file/14 bảng; common 61 file/chỉ một bảng nhật ký, gồm cấu hình JDBC Oracle 26ai theo ORACLECFG01. Cộng history là 24 bảng. PostgreSQL chạy ứng dụng hiện hành; Oracle chưa có migration/query nghiệp vụ. Thống kê source không tự xác nhận đã merge main/đưa production.

## Tìm code ở đâu

| Package | Trách nhiệm |
|---|---|
| auth | Entity/repo/service/controller/API/DTO/security/bootstrap tài khoản và quyền |
| business/catalog, carrier | Danh mục/tỉnh/địa điểm; nhà xe/xe/tài xế |
| business/trip, booking | Tuyến/chuyến/tìm chuyến/tồn chỗ; đặt chỗ/thu-hoàn |
| business/report | Báo cáo và dashboard; không có bảng riêng |
| business/bootstrap | Khởi tạo nền opt-in, initializer riêng ngang service |
| common/config, initializer, jdbc | Cấu hình MVC/JPA/DB, startup migration và transaction/schema |
| common/base, validation, constant, exception | Hợp đồng và quy tắc dùng chung |
| common/web | Layout/menu/locale, trace/HTTP guard, Swagger, response/lỗi |
| common/api_journal | User/request/thời gian/snapshot đã lọc; cURL/Postman trên giao diện |

Không còn common.job/mail/media/monitoring/traffic, provider gửi thư/upload, Redis hay collector metrics. Hồ sơ user chỉ sửa tên hiển thị; không avatar/logo/photo. GPS01 đã bỏ phần vị trí thiết bị; vẫn giữ scope/owner và tiến độ đến/rời điểm.

## Khởi động và request

Theo [chạy local](CHAY_LOCAL.md); production nhận env, không tự đọc `.env`. Migration chỉ chạy startup theo cờ, không chạy lúc Maven đóng gói. Database mới dùng 8 SQL theo auth/catalog/carrier/trip/booking/common/foundation, chỉ tạo bảng hiện hành. Database khác baseline cần bảo trì history có kiểm cấu trúc trước khi dùng source mới. [Schema/quyền](../02-phat-trien/SCHEMA_VA_PHAN_QUYEN.md) mô tả phụ thuộc, backup và giới hạn nâng cấp.

Request qua encoding/trace, nhật ký, HTTP body guard, CSRF/JWT rồi EndpointAccess từ snapshot DB trước handler. Controller → service nghiệp vụ/transaction → repo JPA hoặc JDBC; scope/owner được bind trong SQL. ApiResponses/ApiExceptionMapper tạo cùng envelope và lỗi locale/traceId. Giữ root/servlet context riêng, method security ở cả hai; menu không thay guard backend.

## Transaction, log và mở rộng

CRUD JPA và JDBC batch/khóa nguyên tử dùng cùng transaction; history lỗi rollback mutation. Journal ghi trực tiếp PostgreSQL bằng pool riêng một connection/node, bounded snapshot/backoff; có thể bỏ trace khi DB/slot lỗi. Không có lịch sử RAM queue hoặc worker nền. Xem [luồng base](../02-phat-trien/LUONG_XU_LY_BASE.md), [cấu trúc common](../02-phat-trien/CONSTANTS_VA_CAU_TRUC_WEB.md), [nhật ký](../04-van-hanh/NHAT_KY_API_VA_DEBUG.md).

Khi thêm nghiệp vụ, theo module hiện hành, dùng một Request/tài nguyên và DTO class, Search trong dto/search. Query trong Repo, service giữ nghiệp vụ/transaction, constant tách riêng, initializer ngang service. Mỗi class dưới 1000 dòng, không record/var. Chỉ thêm lớp khi có trách nhiệm thật; chạy formatter và regression phù hợp. Quy trình nhánh thành viên → dev, chỉ main/release khi chủ dự án có lệnh mới.
