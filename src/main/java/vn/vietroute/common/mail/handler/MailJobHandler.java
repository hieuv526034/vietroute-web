package vn.vietroute.common.mail.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.job.handler.JobHandler;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.mail.transport.MailTransport;

/** Handler duy nhất gửi mail; API chỉ enqueue, không gọi SMTP/Gmail trực tiếp trong transaction. */
@Component
@RequiredArgsConstructor
public class MailJobHandler implements JobHandler {

    private final MailSettings settings;

    private final ObjectMapper json;

    private final MailTransport transport;

    @Override
    public String type() {
        return "MAIL_SEND";
    }

    @Override
    public void handle(Job job) throws Exception {
        if (!settings.isEnabled()) {
            throw new JobFailure("MAIL_DISABLED", true);
        }
        MailPayload payload = json.readValue(job.payload(), MailPayload.class);
        if (
            !MailSettings.validAddress(payload.to()) ||
            payload.subject() == null ||
            payload.subject().contains("\r") ||
            payload.subject().contains("\n") ||
            payload.text() == null
        ) {
            throw new JobFailure("MAIL_PAYLOAD_INVALID", false);
        }
        transport.send(payload, job.id());
    }
}
