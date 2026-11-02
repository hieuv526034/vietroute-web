package vn.vietroute.business.bootstrap.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chỉ trả số lượng, không trả credentials/account hoặc cấu hình môi trường. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BusinessDataResponse {

    @Schema(description = "Số bản ghi mới tạo trong lần chạy")
    private int created;

    @Schema(description = "Số bản ghi trong bộ nền đã có, giữ nguyên")
    private int reused;

    @Schema(description = "Số mục danh mục trong bộ dữ liệu")
    private int catalogs;

    @Schema(description = "Số tỉnh/thành trong bộ nền")
    private int provinces;

    @Schema(description = "Số địa điểm trong bộ nền")
    private int locations;

    @Schema(description = "Số nhà xe trong bộ nền")
    private int carriers;

    @Schema(description = "Số xe trong bộ nền")
    private int vehicles;

    @Schema(description = "Số hồ sơ tài xế trong bộ nền; API không tạo tài khoản")
    private int drivers;
}
