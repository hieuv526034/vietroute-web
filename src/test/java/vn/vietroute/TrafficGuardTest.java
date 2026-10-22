package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.vietroute.common.config.RootConfig;
import vn.vietroute.common.config.TrafficSettings;
import vn.vietroute.common.traffic.ClientAddressResolver;
import vn.vietroute.common.traffic.MemoryRateLimitStore;
import vn.vietroute.common.traffic.RateLimitStore;
import vn.vietroute.common.traffic.TrafficGuardFilter;
import vn.vietroute.common.web.ApiErrorWriter;
import vn.vietroute.common.web.ApiResponses;
import vn.vietroute.common.web.SupportedLocaleResolver;

/** Kiểm quota nguyên tử và chống giả proxy trước khi JWT/DAO được gọi; không cần database. */
class TrafficGuardTest {

    private final RootConfig config = new RootConfig();

    private final ObjectMapper json = config.objectMapper();

    private TrafficGuardFilter filter(Map<String, String> values, RateLimitStore store) {
        return new TrafficGuardFilter(
            new TrafficSettings(values::get),
            store,
            new ApiErrorWriter(
                json,
                new ApiResponses(config.messageSource(), new SupportedLocaleResolver(), Clock.systemUTC())
            )
        );
    }

    @Test
    void concurrentRequestsCannotExceedOneQuota() throws Exception {
        var store = new MemoryRateLimitStore(Clock.systemUTC(), 100);
        var pool = Executors.newFixedThreadPool(8);
        AtomicInteger allowed = new AtomicInteger();
        try {
            for (int index = 0; index < 100; index++) {
                pool.submit(() -> {
                    if (store.consume("same", 7, 60).allowed()) {
                        allowed.incrementAndGet();
                    }
                });
            }
        } finally {
            pool.shutdown();
        }
        assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS));
        assertEquals(7, allowed.get());
    }

    @Test
    void boundedStoreDoesNotEvictActiveQuotaAndExpiredKeysCanBeReused() {
        var clock = new MutableClock();
        var store = new MemoryRateLimitStore(clock, 1);
        assertTrue(store.consume("one", 1, 60).allowed());
        assertFalse(store.consume("two", 1, 60).allowed());
        assertFalse(store.consume("one", 1, 60).allowed());
        clock.now = clock.now.plusSeconds(61);
        assertTrue(store.consume("two", 1, 60).allowed());
    }

    @Test
    void forwardedHeaderIsOnlyTrustedBehindConfiguredPeer() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("203.0.113.7");
        request.addHeader("X-Forwarded-For", "192.0.2.8");
        assertEquals("203.0.113.7", new ClientAddressResolver("10.0.0.0/8").resolve(request));
        request.setRemoteAddr("10.1.2.3");
        assertEquals("192.0.2.8", new ClientAddressResolver("10.0.0.0/8").resolve(request));
        request.removeHeader("X-Forwarded-For");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 192.0.2.8");
        assertEquals("192.0.2.8", new ClientAddressResolver("10.0.0.0/8").resolve(request));
    }

    @Test
    void rateLimitCannotBeBypassedBySpoofedForwardedHeader() throws Exception {
        var filter = filter(Map.of("VIETROUTE_RATE_REQUESTS", "1"), new MemoryRateLimitStore(Clock.systemUTC(), 100));
        AtomicInteger calls = new AtomicInteger();
        for (int index = 0; index < 2; index++) {
            var request = new MockHttpServletRequest("GET", "/api/test");
            request.addHeader("X-Forwarded-For", "192.0.2." + index);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> calls.incrementAndGet());
            if (index == 1) {
                assertEquals(429, response.getStatus());
                assertNotNull(response.getHeader("Retry-After"));
                assertEquals("COM012", json.readTree(response.getContentAsByteArray()).get("code").asText());
            }
        }
        assertEquals(1, calls.get());
    }

    @Test
    void chunkedOversizeBodyIsRejectedBeforeController() throws Exception {
        var filter = filter(
            Map.of("VIETROUTE_HTTP_MAX_BODY_BYTES", "1024"),
            new MemoryRateLimitStore(Clock.systemUTC(), 100)
        );
        var request = new MockHttpServletRequest("POST", "/api/test") {
            @Override
            public long getContentLengthLong() {
                return -1;
            }
        };
        request.setContent(new byte[1025]);
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> fail("Must not reach controller"));
        assertEquals(413, response.getStatus());
        assertEquals("COM014", json.readTree(response.getContentAsByteArray()).get("code").asText());
    }

    @Test
    void redisFailureStopsTrafficAndPreservesSanitizedEnvelope() throws Exception {
        RateLimitStore failed = (key, limit, seconds) -> {
            throw new IllegalStateException("redis-secret-never-echo");
        };
        var response = new MockHttpServletResponse();
        filter(Map.of(), failed).doFilter(new MockHttpServletRequest("GET", "/api/test"), response, (req, res) ->
            fail("Must not bypass failed shared quota")
        );
        assertEquals(503, response.getStatus());
        assertFalse(response.getContentAsString().contains("redis-secret"));
    }

    @Test
    void inFlightLimitReturnsBusyAndReleasesSlotAfterRequest() throws Exception {
        var guard = filter(Map.of("VIETROUTE_HTTP_IN_FLIGHT", "1"), new MemoryRateLimitStore(Clock.systemUTC(), 100));
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        Thread thread = new Thread(() -> {
            try {
                guard.doFilter(
                    new MockHttpServletRequest("GET", "/api/test"),
                    new MockHttpServletResponse(),
                    (req, res) -> {
                        entered.countDown();
                        try {
                            if (!release.await(5, TimeUnit.SECONDS)) {
                                fail("Timeout");
                            }
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                        }
                    }
                );
            } catch (Throwable exception) {
                failure.set(exception);
            }
        });
        thread.start();
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var busy = new MockHttpServletResponse();
            guard.doFilter(new MockHttpServletRequest("GET", "/api/test"), busy, (req, res) -> fail("No slot"));
            assertEquals(503, busy.getStatus());
        } finally {
            release.countDown();
            thread.join(5000);
        }
        assertNull(failure.get());
        var available = new MockHttpServletResponse();
        guard.doFilter(new MockHttpServletRequest("GET", "/api/test"), available, (req, res) -> {});
        assertEquals(200, available.getStatus());
    }

    @Test
    void imageMultipartKeepsOriginalStreamAndUsesDedicatedByteLimit() throws Exception {
        var guard = filter(Map.of(), new MemoryRateLimitStore(Clock.systemUTC(), 100));
        var multipart = new MockHttpServletRequest("POST", "/api/v1/media");
        multipart.setContentType("multipart/form-data; boundary=test");
        byte[] content = new byte[70000];
        multipart.setContent(content);
        var response = new MockHttpServletResponse();
        guard.doFilter(multipart, response, (request, output) -> {
            assertTrue(request == multipart);
            assertEquals(content.length, request.getInputStream().readAllBytes().length);
        });
        assertEquals(200, response.getStatus());
        var oversized = new MockHttpServletRequest("POST", "/api/v1/media");
        oversized.setContentType("multipart/form-data; boundary=test");
        oversized.setContent(new byte[5308417]);
        response = new MockHttpServletResponse();
        guard.doFilter(oversized, response, (request, output) -> fail("Oversized multipart must be rejected"));
        assertEquals(413, response.getStatus());
    }

    private static final class MutableClock extends Clock {

        private Instant now = Instant.parse("2026-10-22T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
