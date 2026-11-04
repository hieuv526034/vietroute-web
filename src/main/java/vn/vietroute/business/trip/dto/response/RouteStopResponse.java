package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Projection tường minh của điểm mẫu; địa chỉ/tọa độ đọc từ danh mục server. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RouteStopResponse {

    @Schema(description = "Thứ tự từ 1")
    private int stopOrder;

    @Schema(description = "ID địa điểm")
    private long locationId;

    @Schema(description = "Tên Việt")
    private String nameVi;

    @Schema(description = "Tên Anh")
    private String nameEn;

    @Schema(description = "Địa chỉ")
    private String address;

    @Schema(description = "Vĩ độ")
    private BigDecimal latitude;

    @Schema(description = "Kinh độ")
    private BigDecimal longitude;

    @Schema(description = "Độ lệch phút đến")
    private int arrivalOffsetMinutes;

    @Schema(description = "Độ lệch phút rời")
    private int departureOffsetMinutes;

    @Schema(description = "Giá cộng dồn VND")
    private BigDecimal cumulativeFare;

    @Schema(description = "Cho phép đón")
    private boolean pickupAllowed;

    @Schema(description = "Cho phép trả")
    private boolean dropoffAllowed;

    @Schema(description = "ID tỉnh/thành hiện hành của địa điểm")
    private long provinceId;

    @Schema(description = "Tên tỉnh/thành của địa điểm")
    private String provinceName;

    @Schema(description = "ID xã/phường trực tiếp chứa địa điểm")
    private long wardId;

    @Schema(description = "Tên xã/phường chứa địa điểm")
    private String wardName;
}
