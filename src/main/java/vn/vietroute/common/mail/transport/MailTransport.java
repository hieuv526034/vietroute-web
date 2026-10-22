package vn.vietroute.common.mail.transport;

import java.util.UUID;
import vn.vietroute.common.mail.model.MailPayload;

/** Thay provider không đổi API/outbox/worker; Message-ID ổn định hỗ trợ đối chiếu, không bảo đảm provider dedup. */
public interface MailTransport {
    void send(MailPayload payload, UUID jobId) throws Exception;
}
