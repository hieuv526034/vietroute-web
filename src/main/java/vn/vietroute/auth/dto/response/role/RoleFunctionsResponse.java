package vn.vietroute.auth.dto.response.role;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Tập chức năng hiện tại của role và version mapping */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Tập chức năng hiện tại của role và version mapping")
public class RoleFunctionsResponse {

    @Schema(description = "Mã role")
    private String role;

    @Schema(description = "Mã chức năng đã gán")
    private Set<String> functionCodes;

    @Schema(description = "Version mapping role")
    private long version;
}
