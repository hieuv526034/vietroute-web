package vn.vietroute.common.mail;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.common.base.BaseController;
import vn.vietroute.common.constant.SuccessCode;
import vn.vietroute.common.exception.ApiExceptionMapper;
import vn.vietroute.common.job.JobResponse;
import vn.vietroute.common.web.ApiResponse;
import vn.vietroute.common.web.ApiResponses;

@RestController
@RequestMapping("/api/v1/admin/mail")
@Tag(name = "Email", description = "Gửi mail qua outbox; không phải endpoint gửi thư công khai")
public class MailController extends BaseController {

    private final MailService mail;

    public MailController(MailService mail, ApiResponses responses, ApiExceptionMapper errors) {
        super(responses, errors);
        this.mail = mail;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Xếp email vào hàng đợi",
        description = "Yêu cầu MAIL_SEND. Idempotency-Key 8–64 chữ/số/_/-. HTTP 202 chỉ xác nhận lưu việc; xem job để biết gửi thành công."
    )
    public ResponseEntity<ApiResponse<JobResponse>> queue(
        @Valid @RequestBody MailRequest body,
        @RequestHeader("Idempotency-Key") @Pattern(
            regexp = "[A-Za-z0-9_-]{8,64}",
            message = "{validation.invalid}"
        ) String key,
        @AuthenticationPrincipal UserPrincipal actor,
        HttpServletRequest request
    ) {
        return execute(HttpStatus.ACCEPTED, SuccessCode.ACCEPTED, request, () -> mail.queue(body, key, actor.id()));
    }
}
