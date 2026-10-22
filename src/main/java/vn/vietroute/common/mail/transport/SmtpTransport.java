package vn.vietroute.common.mail.transport;

import jakarta.mail.Session;
import jakarta.mail.Transport;
import java.util.Properties;
import java.util.UUID;
import vn.vietroute.common.config.MailSettings;
import vn.vietroute.common.mail.model.MailPayload;

/** SMTP STARTTLS bắt buộc và kiểm hostname; Gmail dùng App Password khi chạy local/hạ tầng cho phép SMTP. */
public final class SmtpTransport implements MailTransport {

    private final MailSettings settings;

    public SmtpTransport(MailSettings settings) {
        this.settings = settings;
    }

    @Override
    public void send(MailPayload payload, UUID jobId) throws Exception {
        Properties properties = new Properties();
        properties.setProperty("mail.smtp.auth", "true");
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.smtp.starttls.required", "true");
        properties.setProperty("mail.smtp.ssl.checkserveridentity", "true");
        properties.setProperty("mail.smtp.ssl.protocols", "TLSv1.3 TLSv1.2");
        properties.setProperty("mail.smtp.connectiontimeout", "10000");
        properties.setProperty("mail.smtp.timeout", "15000");
        properties.setProperty("mail.smtp.writetimeout", "15000");
        Session session = Session.getInstance(properties);
        try (Transport transport = session.getTransport("smtp")) {
            transport.connect(
                settings.getSmtpHost(),
                settings.getSmtpPort(),
                settings.getSmtpUser(),
                settings.getSmtpPassword()
            );
            var message = MailMessages.create(session, settings.getFrom(), payload, jobId);
            transport.sendMessage(message, message.getAllRecipients());
        }
    }
}
