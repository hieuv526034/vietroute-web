package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.mail.Session;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.integration.MailSettings;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.job.worker.JobWorker;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.mail.transport.GmailTransport;
import vn.vietroute.common.mail.transport.MailMessages;

/** Giả HttpClient thay vì gửi mail thật; kiểm OAuth/token cache/retry classification và MIME tiếng Việt. */
class MailFoundationTest {

    private final ObjectMapper json = new RootConfig().objectMapper();

    private MailSettings settings() {
        return new MailSettings(
            Map.of(
                "VIETROUTE_MAIL_ENABLED",
                "true",
                "VIETROUTE_MAIL_FROM",
                "sender@example.com",
                "VIETROUTE_GMAIL_CLIENT_ID",
                "test-id",
                "VIETROUTE_GMAIL_CLIENT_SECRET",
                "test-secret",
                "VIETROUTE_GMAIL_REFRESH_TOKEN",
                "test-refresh"
            )::get
        );
    }

    @Test
    void configRequiresCredentialsAndRejectsMultipleAddressesOrHeaderInjection() {
        assertThrows(IllegalStateException.class, () ->
            new MailSettings(Map.of("VIETROUTE_MAIL_ENABLED", "true")::get)
        );
        assertFalse(MailSettings.validAddress("a@example.com,b@example.com"));
        assertFalse(MailSettings.validAddress("a@example.com\r\nBcc: victim@example.com"));
        assertTrue(MailSettings.validAddress("a@example.com"));
    }

    @Test
    void mimePreservesVietnameseAndStableMessageId() throws Exception {
        UUID id = UUID.randomUUID();
        var message = MailMessages.create(
            Session.getInstance(new Properties()),
            "sender@example.com",
            new MailPayload("receiver@example.com", "Thông báo hành trình", "Nội dung tiếng Việt"),
            id
        );
        assertEquals("Thông báo hành trình", message.getSubject());
        assertEquals("<" + id + "@vietroute.local>", message.getMessageID());
        message.saveChanges();
        assertEquals("<" + id + "@vietroute.local>", message.getMessageID());
        assertTrue(message.getContent().toString().contains("Nội dung tiếng Việt"));
    }

    @Test
    void gmailRefreshesOnceAndSendsOverHttpsWithoutRedirects() throws Exception {
        var http = new StubHttp();
        http.add(200, "{\"access_token\":\"test-access\",\"expires_in\":3600}");
        http.add(200, "{\"id\":\"mail-1\"}");
        http.add(200, "{\"id\":\"mail-2\"}");
        var gmail = new GmailTransport(settings(), json, http, Clock.systemUTC());
        var payload = new MailPayload("receiver@example.com", "Tiêu đề", "Nội dung");
        gmail.send(payload, UUID.randomUUID());
        gmail.send(payload, UUID.randomUUID());
        assertEquals(3, http.requests.size());
        assertEquals("oauth2.googleapis.com", http.requests.get(0).uri().getHost());
        assertEquals("gmail.googleapis.com", http.requests.get(1).uri().getHost());
        assertEquals("Bearer test-access", http.requests.get(1).headers().firstValue("Authorization").orElseThrow());
        assertTrue(http.requests.stream().allMatch(request -> request.uri().getScheme().equals("https")));
        assertTrue(http.requests.stream().allMatch(request -> request.timeout().orElseThrow().toSeconds() <= 20));
    }

    @Test
    void gmailQuotaCanRetryButInvalidOauthRequiresConfigurationFix() throws Exception {
        var http = new StubHttp();
        http.add(200, "{\"access_token\":\"test-access\",\"expires_in\":3600}");
        http.add(429, "provider-secret-never-echo");
        var gmail = new GmailTransport(settings(), json, http, Clock.systemUTC());
        var error = assertThrows(JobFailure.class, () ->
            gmail.send(new MailPayload("to@example.com", "Test", "Body"), UUID.randomUUID())
        );
        assertEquals("GMAIL_HTTP_429", error.code());
        assertTrue(error.retryable());
        assertFalse(error.getMessage().contains("provider-secret"));
        var invalid = new StubHttp();
        invalid.add(400, "secret-never-echo");
        var auth = assertThrows(JobFailure.class, () ->
            new GmailTransport(settings(), json, invalid, Clock.systemUTC()).send(
                new MailPayload("to@example.com", "Test", "Body"),
                UUID.randomUUID()
            )
        );
        assertFalse(auth.retryable());
    }

    @Test
    void retryBackoffIsBoundedAndGrowsAcrossAttempts() {
        assertTrue(JobWorker.retryDelay(1) >= 5 && JobWorker.retryDelay(1) <= 9);
        assertTrue(JobWorker.retryDelay(5) >= 80 && JobWorker.retryDelay(5) <= 84);
        assertTrue(JobWorker.retryDelay(100) <= 3604);
    }

    @Test
    void gmailQuota403RetriesWhilePermission403IsPermanent() throws Exception {
        for (boolean quota : new boolean[] { true, false }) {
            var http = new StubHttp();
            http.add(200, "{\"access_token\":\"test-access\",\"expires_in\":3600}");
            http.add(
                403,
                quota
                    ? "{\"error\":{\"errors\":[{\"reason\":\"userRateLimitExceeded\"}]}}"
                    : "{\"error\":{\"errors\":[{\"reason\":\"forbidden\"}]}}"
            );
            var error = assertThrows(JobFailure.class, () ->
                new GmailTransport(settings(), json, http, Clock.systemUTC()).send(
                    new MailPayload("to@example.com", "Test", "Body"),
                    UUID.randomUUID()
                )
            );
            assertEquals(quota, error.retryable());
        }
    }

    /** Stub nhỏ chỉ trả response đã xếp trước; mọi sendAsync bị từ chối để tránh gọi mạng ngoài ý muốn. */
    private static final class StubHttp extends HttpClient {

        private final ArrayDeque<Reply> replies = new ArrayDeque<>();
        private final List<HttpRequest> requests = new ArrayList<>();

        void add(int status, String body) {
            replies.add(new Reply(status, body));
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            requests.add(request);
            Reply reply = replies.removeFirst();
            return (HttpResponse<T>) new HttpResponse<String>() {
                @Override
                public int statusCode() {
                    return reply.status();
                }

                @Override
                public String body() {
                    return reply.body();
                }

                @Override
                public HttpRequest request() {
                    return request;
                }

                @Override
                public Optional<HttpResponse<String>> previousResponse() {
                    return Optional.empty();
                }

                @Override
                public HttpHeaders headers() {
                    return HttpHeaders.of(Map.of(), (name, value) -> true);
                }

                @Override
                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                @Override
                public URI uri() {
                    return request.uri();
                }

                @Override
                public Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
            HttpRequest request,
            HttpResponse.BodyHandler<T> handler,
            HttpResponse.PushPromiseHandler<T> push
        ) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.of(Duration.ofSeconds(10));
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        private record Reply(int status, String body) {}
    }
}
