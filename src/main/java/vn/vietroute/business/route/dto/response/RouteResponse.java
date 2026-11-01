package vn.vietroute.business.route.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Response không có tenant/audit do client tự gán; điểm chi tiết chỉ nạp ở GET theo ID. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RouteResponse {

    @Schema(description = "ID tuyến")
    private long id;

    @Schema(description = "ID nhà xe")
    private long carrierId;

    @Schema(description = "ID scope từ DB")
    private long scopeId;

    @Schema(description = "Tên nhà xe")
    private String carrierName;

    @Schema(description = "Mã tuyến bất biến")
    private String code;

    @Schema(description = "Tên Việt")
    private String nameVi;

    @Schema(description = "Tên Anh")
    private String nameEn;

    @Schema(description = "Mô tả")
    private String description;

    @Schema(description = "Đang sử dụng")
    private boolean active;

    @Schema(description = "Số điểm")
    private int stopCount;

    @Schema(description = "Version chống ghi đè")
    private long version;

    @Schema(description = "Thời điểm tạo UTC")
    private Instant createdAt;

    @Schema(description = "Thời điểm sửa UTC")
    private Instant updatedAt;

    @Schema(description = "Actor tạo")
    private long createdBy;

    @Schema(description = "Actor sửa")
    private long updatedBy;

    @Schema(description = "Có quyền UPDATE trong scope; backend luôn kiểm lại")
    private boolean canUpdate;

    @Schema(description = "Danh sách điểm khi đọc chi tiết, rỗng trên trang danh sách")
    private List<RouteStopResponse> stops;
}
