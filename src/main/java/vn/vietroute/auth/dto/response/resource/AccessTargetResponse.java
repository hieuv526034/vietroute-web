package vn.vietroute.auth.dto.response.resource;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Schema(description = "Menu/endpoint thực có trong ứng dụng, dùng chọn chính sách")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class AccessTargetResponse {

    @Schema(description = "MENU hoặc ENDPOINT")
    private String kind;

    @Schema(description = "Phương thức + template path đã đăng ký")
    private String target;

    @Schema(description = "Controller xử lý, không chứa payload hoặc secret")
    private String handler;

    public AccessTargetResponse(String kind, String target, String handler) {
        this.kind = kind;
        this.target = target;
        this.handler = handler;
    }

    public String kind() {
        return kind;
    }

    public String target() {
        return target;
    }

    public String handler() {
        return handler;
    }
}
