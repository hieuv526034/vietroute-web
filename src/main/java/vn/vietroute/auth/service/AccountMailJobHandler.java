package vn.vietroute.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.security.AccountSecrets;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.job.handler.JobHandler;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.mail.transport.MailTransport;

/** Worker dùng transport mail chung; payload auth chỉ giải mã tại lúc gửi, không đưa vào API/log quản trị. */
@Component
@RequiredArgsConstructor
public class AccountMailJobHandler implements JobHandler {

    private final AccountSecrets secrets;

    private final ObjectMapper json;

    private final MailSettings settings;

    private final MailTransport transport;

    private final Clock clock;

    @Override
    public String type() {
        return AccountMailPayload.TYPE;
    }

    @Override
    public void handle(Job job) throws Exception {
        if (!settings.isEnabled()) throw new JobFailure("MAIL_DISABLED", true);
        AccountMailPayload envelope;
        MailPayload payload;
        try {
            envelope = json.readValue(job.payload(), AccountMailPayload.class);
            if (envelope.expiresAt() == null || !envelope.expiresAt().isAfter(Instant.now(clock))) {
                throw new JobFailure("ACCOUNT_MAIL_EXPIRED", false);
            }
            payload = json.readValue(secrets.decrypt(envelope.encrypted()), MailPayload.class);
            if (!MailSettings.validAddress(payload.to())) throw new IllegalArgumentException();
        } catch (JobFailure error) {
            throw error;
        } catch (Exception error) {
            throw new JobFailure("ACCOUNT_MAIL_INVALID", false);
        }
        transport.send(payload, job.id());
    }
}
