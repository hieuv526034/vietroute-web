package vn.vietroute.common.mail.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

/** Nội dung outbox nội bộ; HTML chỉ do template server tạo, không nhận từ API hoặc xuất qua nhật ký quản trị. */
public record MailPayload(String to, String subject, String text, @JsonInclude(Include.NON_NULL) String html) {
    /** Giữ tương thích module và job plain text đã có trước template HTML. */
    public MailPayload(String to, String subject, String text) {
        this(to, subject, text, null);
    }
}
