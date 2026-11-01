package vn.vietroute.auth.dto.response.session;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hợp đồng CSRF tường minh để Swagger hiển thị đúng hai trường client cần dùng. */
@Schema(description = "Token chống CSRF đi kèm cookie XSRF-TOKEN cùng origin")
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@Setter
public class CsrfResponse {

    @Schema(description = "Tên header gửi token trong mutation", example = "X-XSRF-TOKEN")
    private String headerName;

    @Schema(description = "Token hiện hành, không ghi log hoặc đưa vào URL")
    private String token;

    public CsrfResponse(String headerName, String token) {
        this.headerName = headerName;
        this.token = token;
    }

    public String headerName() {
        return headerName;
    }

    public String token() {
        return token;
    }
}
