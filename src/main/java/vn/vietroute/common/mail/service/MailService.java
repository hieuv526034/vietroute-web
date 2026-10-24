package vn.vietroute.common.mail.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.security.AccessRules;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.dto.response.JobResponse;
import vn.vietroute.common.job.repository.JobRepository;
import vn.vietroute.common.mail.dto.request.MailRequest;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.traffic.RateLimitStore;

/** Outbox cùng PostgreSQL giúp enqueue và thay đổi nghiệp vụ commit/rollback đồng thời. */
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailSettings settings;

    private final ObjectMapper json;

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    private final RateLimitStore limits;

    /** API quản trị yêu cầu quyền; actor và idempotency key được gắn server-side. */
    @PreAuthorize(AccessRules.MAIL_SEND)
    public JobResponse queue(MailRequest request, String key, long actor) {
        try {
            if (!limits.consume("mail-user:" + actor, 10, 60).allowed()) {
                throw new BusinessException(ErrorCode.RATE_LIMITED);
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.SERVICE_BUSY);
        }
        UUID id = transactions.execute(connection ->
            enqueue(
                connection,
                new MailPayload(request.getTo(), request.getSubject(), request.getText()),
                "mail:" + actor + ":" + key,
                actor
            )
        );
        return jobs.find(id);
    }

    /** Module sau gọi trong transaction nghiệp vụ, không mở transaction lồng và không gửi mạng trước commit. */
    public UUID enqueue(Connection connection, MailPayload payload, String dedup, Long actor) throws SQLException {
        if (!settings.isEnabled()) {
            throw new BusinessException(ErrorCode.MAIL_DISABLED);
        }
        if (
            !MailSettings.validAddress(payload.to()) ||
            payload.subject() == null ||
            payload.subject().isBlank() ||
            payload.subject().length() > 160 ||
            payload.subject().contains("\r") ||
            payload.subject().contains("\n") ||
            payload.text() == null ||
            payload.text().isBlank() ||
            payload.text().length() > 8000 ||
            dedup == null ||
            !dedup.matches(Regex.JOB_DEDUP_KEY)
        ) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        try {
            return jobs.enqueue(connection, "MAIL_SEND", dedup, json.writeValueAsString(payload), actor);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }
}
