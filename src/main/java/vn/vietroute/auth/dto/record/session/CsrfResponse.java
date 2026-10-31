package vn.vietroute.auth.dto.record.session;

import io.swagger.v3.oas.annotations.media.Schema;

/** Hợp đồng CSRF tường minh để Swagger hiển thị đúng hai trường client cần dùng. */
@Schema(description = "Token chống CSRF đi kèm cookie XSRF-TOKEN cùng origin")
public record CsrfResponse(
    @Schema(description = "Tên header gửi token trong mutation", example = "X-XSRF-TOKEN") String headerName,
    @Schema(description = "Token hiện hành, không ghi log hoặc đưa vào URL") String token
) {}
