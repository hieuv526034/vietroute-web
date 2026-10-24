package vn.vietroute.common.mail.transport;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import vn.vietroute.common.mail.model.MailPayload;

/** Dựng MIME bằng thư viện Jakarta Mail, không nối header từ chuỗi người dùng. */
public final class MailMessages {

    private MailMessages() {}

    public static MimeMessage create(Session session, String from, MailPayload payload, UUID id)
        throws MessagingException {
        MimeMessage message = new StableMessage(session, id);
        message.setFrom(new InternetAddress(from, true));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(payload.to(), true));
        message.setSubject(payload.subject(), StandardCharsets.UTF_8.name());
        if (payload.html() == null || payload.html().isBlank()) {
            message.setText(payload.text(), StandardCharsets.UTF_8.name());
        } else {
            // Plain text đứng trước HTML theo multipart/alternative; cả Gmail API và SMTP dùng cùng MIME.
            MimeBodyPart plain = new MimeBodyPart();
            plain.setText(payload.text(), StandardCharsets.UTF_8.name());
            MimeBodyPart html = new MimeBodyPart();
            html.setText(payload.html(), StandardCharsets.UTF_8.name(), "html");
            MimeMultipart alternatives = new MimeMultipart("alternative");
            alternatives.addBodyPart(plain);
            alternatives.addBodyPart(html);
            message.setContent(alternatives);
        }
        message.saveChanges();
        return message;
    }

    /** Giữ Message-ID qua saveChanges và không dùng DNS/hostname để tự sinh ID trên mỗi email. */
    private static final class StableMessage extends MimeMessage {

        private final UUID jobId;

        private StableMessage(Session session, UUID jobId) {
            super(session);
            this.jobId = jobId;
        }

        @Override
        protected void updateMessageID() throws MessagingException {
            setHeader("Message-ID", "<" + jobId + "@vietroute.local>");
        }
    }
}
