# ApplicationConstants và cấu trúc web

Phụ trách: hieuv526034. Lượt này quy hoạch source để nhóm tìm đúng nơi sửa; giữ nguyên tên key Environment, URL/API, mã lỗi, schema và cơ chế phân quyền đang dùng. Không có migration hoặc thay đổi Neon production.

## Hằng số đặt ở đâu

`common/constant/ApplicationConstants.java` thay lớp `Constants` cũ và chia thành các nhóm:

| Nhóm | Trách nhiệm | Ví dụ sử dụng |
|---|---|---|
| `Env` | Enum tên key môi trường, không chứa giá trị/secret | `values.flag(Env.MEDIA_ENABLED, false)` |
| `Routes` | Route đầy đủ, phần con và matcher compile-time | `@RequestMapping(Routes.MEDIA)` |
| `Regex` | Pattern kiểm mã, username, schema, trace, dedup, ảnh | `@Pattern(regexp = Regex.USERNAME)` |
| `Http` | Cookie/header riêng, Bearer prefix, media type có charset | `Http.AUTH_COOKIE`, `Http.LOCALE_HEADER` |
| `DatabaseProperties` | Key của file mặc định database.properties | `DatabaseProperties.SCHEMA` |
| `LocaleCode`, `CatalogType` | Enum giá trị dùng trong hợp đồng hiện hành | `CatalogType.VEHICLE_TYPE` |

Header có sẵn trong Spring dùng `HttpHeaders.AUTHORIZATION`, `CONTENT_LANGUAGE`, `RETRY_AFTER`... Không tạo thêm tên khác cho header chuẩn. Mã lỗi/thành công/permission vẫn ở enum chuyên trách `ErrorCode`, `SuccessCode`, `Permission`; không gom các trách nhiệm nghiệp vụ khác nhau thành một enum khổng lồ.

`Env` lưu tên key thực bằng `key()`. Các Settings nhận `ConfigValues`, chỉ truyền enum; không dùng chuỗi tùy ý hoặc đọc `System.getenv` rải rác trong service/controller. Giá trị mặc định, giới hạn và điều kiện bắt buộc vẫn thuộc Settings của module. Điều này giữ mỗi module có trách nhiệm kiểm cấu hình riêng. `ConfigValues` chỉ đưa tên key vào lỗi, không đưa giá trị bị từ chối hoặc secret vào log.

Ví dụ:

```java
import vn.vietroute.common.config.ConfigValues;
import vn.vietroute.common.constant.ApplicationConstants.Env;

ConfigValues values = new ConfigValues(System::getenv);
boolean enabled = values.flag(Env.WORKER_ENABLED, false);
```

Route/regex phải là `static final String` compile-time để dùng được trong annotation; không thay bằng `enum.name()` hoặc hàm getter vì Java không cho gọi hàm trong giá trị annotation. Các route con ghép từ cùng gốc để controller, matcher và Swagger dùng chung. Regex được tách theo nghĩa, không dùng pattern mã nghiệp vụ để kiểm tên người/địa chỉ.

## Chính sách matcher

`common/web/routing/WebRoutePolicy.java` gom phân loại route cho Security/JWT/quota/OpenAPI. Các chính sách phục vụ mục đích khác nhau: được mở công khai không tự đồng nghĩa bỏ JWT hoặc miễn quota. Catalog công khai chỉ GET; mutation vẫn kiểm quyền ở service. Khai báo thêm route vào constants không tự đưa vào permitAll. Không đổi matcher thành `/**` để làm endpoint mới chạy được.

## Thư mục sau quy hoạch

```text
common/
  constant/ApplicationConstants.java
  config/                      Settings, ConfigValues, RootConfig, WebConfig
  web/
    controller/HomeController
    response/                  ApiResponse, FieldViolation, ApiResponses, ApiErrorWriter
    filter/RequestTraceFilter
    locale/SupportedLocaleResolver
    advice/PageModelAdvice
    openapi/                   OpenApiController, OpenApiModels
    routing/WebRoutePolicy
  monitoring/
    controller/                SystemStatusController, MonitoringPageController
    dto/response/              SystemStatusResponse, RuntimeStatusResponse
    filter/                    HTTP metrics và token scrape
    repository/                Tổng hợp DB/queue có timeout
    service/                   SystemStatusService, ApplicationMetrics, DatabaseProbe
  exception/                   BusinessException, mapper và MVC exception advice
```

`web.response` quản lý envelope và việc xuất phản hồi HTTP. `web.openapi` giữ controller và helper model cùng package để tránh trùng tên annotation/model thư viện. `web.advice` là model giao diện; exception advice ở `common.exception` tiếp tục dùng cùng mapper với BaseController. Monitoring là tính năng hạ tầng có controller/DTO/service, không đặt service vào thư mục web. Component scan vẫn quét common/auth/business và tách root/MVC để không tạo bean trùng.

Job/mail được chia theo trách nhiệm như [tổng quan hệ thống](TONG_QUAN_HE_THONG.md); RootConfig ghép DatabaseConfig/IntegrationConfig/TrafficConfig/MonitoringConfig. Chỉ gom tên key/route vào ApplicationConstants, không gom logic giám sát hoặc enum nghiệp vụ tương lai vào lớp này.

## Khi thêm hoặc đổi cấu hình

1. Thêm key trong `ApplicationConstants.Env`, dùng tại Settings qua ConfigValues.
2. Thêm placeholder/hướng dẫn vào `.env.example`; cấu hình key tương ứng trong Render/IDE nếu sử dụng. Không commit giá trị thật.
3. Thêm route/regex đúng nhóm; controller/filter dùng constant. Chỉ sửa WebRoutePolicy khi chủ động thay chính sách truy cập.
4. Kiểm API/Swagger, locale, quyền thiếu/đủ và luồng liên quan; chạy format trước PR.

Java constants không tự đổi tên Environment trên Render, script shell hoặc URL trong client JavaScript. Đây là các hợp đồng bên ngoài Java: đổi tên cần đồng bộ và triển khai có kế hoạch. Kiểm thử đối chiếu registry với `.env.example` và key dùng trong Render/IntelliJ giúp phát hiện thiếu/sai key; không đọc `.env` thật. Không đổi hàng loạt tên key hiện hành chỉ vì đổi tên class.

Không tạo lớp Constants/web cũ làm bản sao chuyển tiếp; code nội bộ phải cập nhật import sang cấu trúc mới. Giữ lịch sử SQL migration nguyên trạng.

Sau khi lấy source đổi package lần đầu, dừng server, chọn Maven → Lifecycle → **clean**, Reload Maven rồi **Build → Rebuild Project** trước khi Run. Bước này loại class cũ trong thư mục build để Spring không nhìn thấy cả package cũ lẫn mới. Không cần gõ lệnh terminal trong IntelliJ.
