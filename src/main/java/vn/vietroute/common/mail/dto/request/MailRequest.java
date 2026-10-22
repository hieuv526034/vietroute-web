package vn.vietroute.common.mail.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import vn.vietroute.common.base.BaseRequest;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

@Getter
@Setter
@Schema(description = "Yêu cầu email plain text; cần quyền MAIL_SEND, không dùng làm API gửi thư công khai")
public class MailRequest extends BaseRequest {

    @NotBlank(message = "{validation.required}")
    @Size(max = 254, message = "{validation.length}")
    @Schema(description = "Một địa chỉ người nhận", example = "recipient@example.com")
    private String to;

    @NotBlank(message = "{validation.required}")
    @Size(max = 160, message = "{validation.length}")
    @Pattern(regexp = Regex.SINGLE_LINE, message = "{validation.invalid}")
    @Schema(description = "Tiêu đề không chứa xuống dòng", example = "Thông báo VietRoute")
    private String subject;

    @NotBlank(message = "{validation.required}")
    @Size(max = 8000, message = "{validation.length}")
    @Schema(description = "Nội dung văn bản thuần, tối đa 8000 ký tự")
    private String text;
}
