package vn.vietroute.auth.dto.response.role;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Tập menu/API được cấp cho một role")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class RoleResourcesResponse {

    @Schema(description = "Mã role")
    private String role;

    @Schema(description = "Target được cấp chính xác, không bao gồm public API")
    private Set<String> targets;

    @Schema(description = "Version mapping, dùng chống ghi đè")
    private long version;

    public RoleResourcesResponse(String role, Set<String> targets, long version) {
        this.role = role;
        this.targets = targets;
        this.version = version;
    }

    public String role() {
        return role;
    }

    public Set<String> targets() {
        return targets;
    }

    public long version() {
        return version;
    }
}
