package vn.vietroute.common.mail.model;

/** Nội dung outbox nội bộ, không log hoặc trả payload qua API quản trị. Chỉ hỗ trợ plain text để tránh HTML không tin cậy. */
public record MailPayload(String to, String subject, String text) {}
