package vn.vietroute.business.demo.dto.response;

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
public class DemoSeedResponse {

    @Schema(description = "Số bản ghi mới tạo trong lần chạy")
    private int created;

    @Schema(description = "Số bản ghi DEMO đã có, giữ nguyên")
    private int reused;

    @Schema(description = "Số mục danh mục trong bộ dữ liệu")
    private int catalogs;

    @Schema(description = "Số tỉnh minh họa")
    private int provinces;

    @Schema(description = "Số địa điểm minh họa")
    private int locations;

    @Schema(description = "Số nhà xe minh họa")
    private int carriers;

    @Schema(description = "Số xe minh họa")
    private int vehicles;

    @Schema(description = "Số hồ sơ tài xế minh họa, chưa tạo tài khoản")
    private int drivers;
}
