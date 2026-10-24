package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.vietroute.auth.security.AccountSecrets;
import vn.vietroute.auth.service.AccountMailComposer;
import vn.vietroute.auth.service.AccountMailJobHandler;
import vn.vietroute.auth.service.AccountMailPayload;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.constant.ApplicationConstants.Env;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.mail.dto.request.MailRequest;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.mail.transport.MailMessages;

/** Template/MIME/outbox được kiểm trong bộ nhớ, không kết nối provider, đọc .env hoặc ghi database. */
class AccountMailTemplateTest {

    private final RootConfig config = new RootConfig();

    private final ObjectMapper json = config.objectMapper();

    private final AccountMailComposer composer = new AccountMailComposer(config.messageSource());

    private final String token = "DEMO_ONLY_0123456789abcdefghijklmnopqrstuvwxy";

    @Test
    void activationAndResetRenderBothLanguagesWithMatchingPlainTextCodesAndExpiry() {
        for (Locale locale : List.of(Locale.forLanguageTag("vi"), Locale.ENGLISH)) {
            for (String purpose : List.of("ACTIVATE", "RESET")) {
                MailPayload payload = composer.compose("fixture@example.invalid", purpose, token, locale);
                boolean reset = "RESET".equals(purpose);
                boolean english = Locale.ENGLISH.equals(locale);
                assertEquals("fixture@example.invalid", payload.to());
                assertTrue(payload.text().contains(token));
                assertTrue(payload.html().contains(token));
                assertTrue(payload.html().contains("lang=\"" + locale.getLanguage() + "\""));
                String expiry = reset ? (english ? "15 minutes" : "15 phút") : english ? "24 hours" : "24 giờ";
                assertTrue(payload.text().contains(expiry));
                assertTrue(payload.html().contains(expiry));
                assertTrue(payload.html().contains(english ? "Never share this code" : "Không chia sẻ mã"));
                assertFalse(payload.html().contains("th:text"));
                assertFalse(payload.html().contains("<script"));
                assertFalse(payload.html().contains("src=\"http"));
                assertFalse(payload.html().contains("href="));
            }
        }
    }

    @Test
    void templateEscapesDynamicContentAndNeverTurnsItIntoMarkupOrTrackingLinks() {
        String injected = "<img src=x onerror=alert(1)> & \"code\"";
        MailPayload payload = composer.compose("fixture@example.invalid", "ACTIVATE", injected, Locale.ENGLISH);
        assertTrue(payload.html().contains("&lt;img"));
        assertTrue(payload.html().contains("&amp;"));
        assertFalse(payload.html().contains("<img"));
        assertTrue(payload.text().contains(injected));
    }

    @Test
    void unsupportedLocaleUsesVietnameseWithoutDependingOnHostLocale() {
        MailPayload fallback = composer.compose("fixture@example.invalid", "RESET", token, Locale.JAPANESE);
        assertTrue(fallback.subject().contains("Đặt lại mật khẩu"));
        assertTrue(fallback.html().contains("lang=\"vi\""));
    }

    @Test
    void mimeRoundTripPreservesPlainHtmlVietnameseAndStableMessageId() throws Exception {
        MailPayload payload = composer.compose(
            "fixture@example.invalid",
            "ACTIVATE",
            token,
            Locale.forLanguageTag("vi")
        );
        UUID id = UUID.randomUUID();
        Session session = Session.getInstance(new Properties());
        MimeMessage message = MailMessages.create(session, "sender@example.invalid", payload, id);
        message.saveChanges();
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        message.writeTo(wire);
        MimeMessage decoded = new MimeMessage(session, new ByteArrayInputStream(wire.toByteArray()));
        assertEquals(payload.subject(), decoded.getSubject());
        assertEquals("<" + id + "@vietroute.local>", decoded.getMessageID());
        assertTrue(decoded.isMimeType("multipart/alternative"));
        MimeMultipart alternatives = (MimeMultipart) decoded.getContent();
        assertEquals(2, alternatives.getCount());
        assertTrue(alternatives.getBodyPart(0).isMimeType("text/plain"));
        assertTrue(alternatives.getBodyPart(1).isMimeType("text/html"));
        // MIME chuẩn hóa xuống dòng thành CRLF khi ghi wire; so nội dung sau chuẩn hóa tương đương.
        assertEquals(
            payload.text().replace("\r\n", "\n"),
            alternatives.getBodyPart(0).getContent().toString().replace("\r\n", "\n")
        );
        assertEquals(
            payload.html().replace("\r\n", "\n"),
            alternatives.getBodyPart(1).getContent().toString().replace("\r\n", "\n")
        );
    }

    @Test
    void oldOutboxRemainsPlainAndPublicRequestCannotSupplyHtml() throws Exception {
        MailPayload legacy = json.readValue(
            "{\"to\":\"fixture@example.invalid\",\"subject\":\"Legacy\",\"text\":\"Old message\"}",
            MailPayload.class
        );
        assertNull(legacy.html());
        assertFalse(json.writeValueAsString(legacy).contains("\"html\""));
        MimeMessage message = MailMessages.create(
            Session.getInstance(new Properties()),
            "sender@example.invalid",
            legacy,
            UUID.randomUUID()
        );
        assertTrue(message.isMimeType("text/plain"));
        assertEquals("Old message", message.getContent());
        assertThrows(UnrecognizedPropertyException.class, () ->
            json.readValue(
                "{\"to\":\"fixture@example.invalid\",\"subject\":\"Test\",\"text\":\"Body\",\"html\":\"<img>\"}",
                MailRequest.class
            )
        );
    }

    @Test
    void encryptedOutboxKeepsHtmlSecretAndExpiredMailNeverReachesTransport() throws Exception {
        AccountSecrets secrets = new AccountSecrets(new AppSettings(Map.of(Env.JWT_SECRET.key(), "A".repeat(48))::get));
        Instant now = Instant.parse("2026-10-24T01:00:00Z");
        MailSettings settings = new MailSettings(
            Map.of(
                Env.MAIL_ENABLED.key(),
                "true",
                Env.MAIL_PROVIDER.key(),
                "smtp",
                Env.MAIL_FROM.key(),
                "sender@example.invalid",
                Env.SMTP_USER.key(),
                "fixture",
                Env.SMTP_PASSWORD.key(),
                "fixture-only"
            )::get
        );
        List<MailPayload> sent = new ArrayList<>();
        AccountMailJobHandler handler = new AccountMailJobHandler(
            secrets,
            json,
            settings,
            (payload, id) -> sent.add(payload),
            Clock.fixed(now, ZoneOffset.UTC)
        );
        MailPayload payload = composer.compose("fixture@example.invalid", "RESET", token, Locale.ENGLISH);
        String encrypted = secrets.encrypt(json.writeValueAsString(payload));
        String envelope = json.writeValueAsString(new AccountMailPayload(encrypted, now.plusSeconds(30)));
        assertFalse(envelope.contains(token));
        assertFalse(envelope.contains("fixture@example.invalid"));
        assertFalse(envelope.contains("<html"));
        handler.handle(job(envelope));
        assertEquals(List.of(payload), sent);
        String expired = json.writeValueAsString(new AccountMailPayload(encrypted, now));
        JobFailure error = assertThrows(JobFailure.class, () -> handler.handle(job(expired)));
        assertEquals("ACCOUNT_MAIL_EXPIRED", error.code());
        assertFalse(error.retryable());
        assertEquals(1, sent.size());
    }

    private Job job(String payload) {
        return new Job(UUID.randomUUID(), AccountMailPayload.TYPE, payload, 1, 5, UUID.randomUUID(), Instant.MAX);
    }
}
