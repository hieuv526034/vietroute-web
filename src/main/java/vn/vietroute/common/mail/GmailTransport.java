package vn.vietroute.common.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import vn.vietroute.common.config.MailSettings;
import vn.vietroute.common.job.JobFailure;

/** Gmail OAuth2 qua HTTPS: chỉ scope gmail.send, cache access token trong RAM, refresh token ở Environment. */
public final class GmailTransport implements MailTransport {

    private final MailSettings settings;

    private final ObjectMapper json;

    private final HttpClient http;

    private final Clock clock;

    private String token;

    private Instant expires = Instant.EPOCH;

    public GmailTransport(MailSettings settings, ObjectMapper json, HttpClient http, Clock clock) {
        this.settings = settings;
        this.json = json;
        this.http = http;
        this.clock = clock;
    }

    /** Mỗi cuộc gọi mạng có timeout; không tự retry trong provider để tránh nhân số lần gửi ngoài worker. */
    @Override
    public void send(MailPayload payload, UUID jobId) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        MailMessages.create(Session.getInstance(new Properties()), settings.getFrom(), payload, jobId).writeTo(output);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(output.toByteArray());
        HttpRequest request = HttpRequest.newBuilder(
            URI.create("https://gmail.googleapis.com/gmail/v1/users/me/messages/send")
        )
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer " + accessToken())
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("raw", raw))))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status == 401) {
            invalidate();
        }
        if (status < 200 || status >= 300) {
            throw new JobFailure(
                "GMAIL_HTTP_" + status,
                status == 401 || status == 429 || status >= 500 || quotaExceeded(response)
            );
        }
    }

    /** Gmail cũng dùng 403 cho rate limit; chỉ đọc reason whitelist, không lưu body provider vào lỗi. */
    private boolean quotaExceeded(HttpResponse<String> response) {
        if (response.statusCode() != 403) {
            return false;
        }
        try {
            for (JsonNode error : json.readTree(response.body()).path("error").path("errors")) {
                String reason = error.path("reason").asText();
                if (
                    reason.equals("rateLimitExceeded") ||
                    reason.equals("userRateLimitExceeded") ||
                    reason.equals("dailyLimitExceeded")
                ) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            /* Không log response có thể chứa thông tin tài khoản. */
        }
        return false;
    }

    /** Đồng bộ refresh để các worker không cùng đổi token; không lưu hoặc log body phản hồi OAuth. */
    private synchronized String accessToken() throws Exception {
        if (token != null && Instant.now(clock).isBefore(expires)) {
            return token;
        }
        String form =
            "grant_type=refresh_token&client_id=" +
            encode(settings.getClientId()) +
            "&client_secret=" +
            encode(settings.getClientSecret()) +
            "&refresh_token=" +
            encode(settings.getRefreshToken());
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://oauth2.googleapis.com/token"))
            .timeout(Duration.ofSeconds(20))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(form))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new JobFailure("GMAIL_OAUTH_FAILED", response.statusCode() == 429 || response.statusCode() >= 500);
        }
        JsonNode body = json.readTree(response.body());
        String access = body.path("access_token").asText();
        if (access.isBlank()) {
            throw new JobFailure("GMAIL_OAUTH_INVALID", false);
        }
        token = access;
        expires = Instant.now(clock).plusSeconds(Math.max(1, body.path("expires_in").asLong(3600) - 60));
        return token;
    }

    private synchronized void invalidate() {
        token = null;
        expires = Instant.EPOCH;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
