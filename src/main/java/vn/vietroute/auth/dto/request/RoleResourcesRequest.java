package vn.vietroute.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

/** Client chỉ gửi target của inventory server và version, không gửi quyền suy diễn hoặc actor. */
@Schema(description = "Thay toàn bộ menu/API của role; tập rỗng thu hồi quyền")
public record RoleResourcesRequest(
    @Schema(description = "Target METHOD + route template từ inventory hiện tại")
    @NotNull(message = "{validation.required}")
    @Size(max = 2048, message = "{validation.length}")
    Set<@NotBlank @Size(max = 240) String> targets,
    @Schema(description = "Version mapping role hiện tại", minimum = "0")
    @NotNull(message = "{validation.required}")
    @Min(value = 0, message = "{validation.invalid}")
    Long version
) {}
