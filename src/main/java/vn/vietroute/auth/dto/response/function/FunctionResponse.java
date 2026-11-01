package vn.vietroute.auth.dto.response.function;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Chức năng và Menu/API được gán, không có credential */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Chức năng và Menu/API được gán, không có credential")
public class FunctionResponse {

    @Schema(description = "Mã chức năng")
    private String code;

    @Schema(description = "Tên tiếng Việt")
    private String nameVi;

    @Schema(description = "Tên tiếng Anh")
    private String nameEn;

    @Schema(description = "Đang bật")
    private boolean active;

    @Schema(description = "Binding do source quản lý, chỉ đổi tên")
    private boolean sourceManaged;

    @Schema(description = "Menu/API thuộc chức năng")
    private Set<String> targets;

    @Schema(description = "Version CAS")
    private long version;

    @Schema(description = "Thời điểm cập nhật UTC")
    private Instant updatedAt;

    @Schema(description = "Actor cập nhật, 0 là system")
    private long updatedBy;
}
