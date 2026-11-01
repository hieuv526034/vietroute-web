package vn.vietroute.auth.dto.request.function;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/** Giữ input như tạo và thêm version CAS bắt buộc khi sửa. */
@Getter
@Setter
@Schema(description = "Sửa chức năng; code phải khớp path, version cũ trả 409")
public class FunctionUpdateRequest extends FunctionCreateRequest {

    @Min(value = 0, message = "{validation.invalid}")
    @NotNull(message = "{validation.required}")
    @Schema(description = "Version đọc gần nhất")
    private Long version;
}
