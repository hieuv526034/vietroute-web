package vn.vietroute.auth.dto.response.capability;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Quyền giao diện hiệu lực theo DB, guard HTTP và policy; không thay kiểm quyền backend")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessCapabilityResponse {

    @Schema(description = "Mã chức năng")
    private String function;

    @Schema(description = "Được mở trang; nhóm không có trang trả false")
    private boolean view;

    @Schema(description = "Hành động được phép trên các endpoint hiện có")
    private Set<String> actions;

    @Schema(description = "Endpoint được phép, kể cả policy siết riêng từng route")
    private Set<String> targets;

    public AccessCapabilityResponse(String function, boolean view, Set<String> actions, Set<String> targets) {
        this.function = function;
        this.view = view;
        this.actions = actions;
        this.targets = targets;
    }

    public String function() {
        return function;
    }

    public boolean view() {
        return view;
    }

    public Set<String> actions() {
        return actions;
    }

    public Set<String> targets() {
        return targets;
    }
}
