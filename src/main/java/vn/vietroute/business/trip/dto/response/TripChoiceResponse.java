package vn.vietroute.business.trip.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Projection tối thiểu, không lộ email/credential hay dữ liệu từ nhà xe ngoài quyền. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TripChoiceResponse {

    @Schema(description = "ID lựa chọn")
    private long id;

    @Schema(description = "Mã")
    private String code;

    @Schema(description = "Tên hiển thị")
    private String name;

    @Schema(description = "Số chỗ xe, null với lựa chọn khác")
    private Integer capacity;
}
