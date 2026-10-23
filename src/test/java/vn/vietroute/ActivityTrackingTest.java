package vn.vietroute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.monitoring.ActivitySettings;
import vn.vietroute.common.config.traffic.TrafficSettings;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.monitoring.dto.response.ActivitySnapshot;
import vn.vietroute.common.monitoring.filter.ActivityFilter;
import vn.vietroute.common.monitoring.repository.ActivityRepository;
import vn.vietroute.common.monitoring.service.ActivityService;

/** Kiểm bảo vệ quan sát: không nhận actor giả, băm định danh, lỗi DB không làm hỏng request chính. */
class ActivityTrackingTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);

    private final AppSettings app = new AppSettings(
        Map.of("VIETROUTE_JWT_SECRET", "unit-test-key-isolated-0123456789abcdef")::get
    );
    private final List<String> recorded = new ArrayList<>();

    private boolean fail;

    private int cleanups;

    private ActivityService service() {
        var repository = new ActivityRepository(null, clock) {
            @Override
            public boolean record(String visitor, String subject, Long user, int status) {
                if (fail) throw new IllegalStateException("db unavailable");
                recorded.add(visitor + ":" + subject + ":" + user + ":" + status);
                return true;
            }

            @Override
            public void cleanup() {
                cleanups++;
            }

            @Override
            public ActivitySnapshot snapshot(long missed) {
                return new ActivitySnapshot(true, 0, 0, 0, 0, 0, 0, 0, 0, missed, clock.instant());
            }
        };
        return new ActivityService(
            new ActivitySettings(Map.of("VIETROUTE_ACTIVITY_ENABLED", "true")::get),
            repository,
            app,
            clock
        );
    }

    @Test
    void visitorSignaturesCannotBeAlteredAndHashesUseDomainSeparation() {
        var activity = service();
        String id = UUID.randomUUID().toString(),
            cookie = id + "." + activity.hash("cookie:" + id);
        assertTrue(activity.validCookie(cookie));
        assertFalse(activity.validCookie(UUID.randomUUID() + cookie.substring(36)));
        assertFalse(activity.validCookie("raw-ip"));
        assertEquals(64, activity.hash("visitor:" + id).length());
        assertFalse(activity.hash("visitor:" + id).equals(activity.hash("source:" + id)));
    }

    @Test
    void filterCapturesRejectionsButOnlyTrustsVerifiedJwtAttribute() throws Exception {
        var activity = service();
        var filter = new ActivityFilter(activity, app, new TrafficSettings(key -> null));
        var request = new MockHttpServletRequest("GET", "/api/v1/media");
        request.setRemoteAddr("192.0.2.8");
        request.addHeader("X-User-Id", "999");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> response.setStatus(429));
        assertEquals(1, recorded.size());
        assertTrue(recorded.get(0).endsWith(":null:429"));
        assertFalse(recorded.get(0).contains("192.0.2.8"));
        assertTrue(response.getHeaders("Set-Cookie").get(0).contains("HttpOnly"));
        var verified = new MockHttpServletRequest("GET", "/api/v1/media");
        verified.setAttribute(Http.VERIFIED_USER_ATTRIBUTE, 8L);
        filter.doFilter(verified, new MockHttpServletResponse(), (req, res) -> {});
        assertTrue(recorded.get(1).endsWith(":8:200"));
        assertEquals(1, cleanups);
    }

    @Test
    void storageFailureBacksOffWithoutKeepingAQueueOrFailingBusinessResponse() throws Exception {
        var activity = service();
        fail = true;
        var filter = new ActivityFilter(activity, app, new TrafficSettings(key -> null));
        var response = new MockHttpServletResponse();
        filter.doFilter(new MockHttpServletRequest("GET", "/account"), response, (req, res) -> response.setStatus(200));
        assertEquals(200, response.getStatus());
        fail = false;
        activity.record("visitor", "source", null, 200);
        assertTrue(recorded.isEmpty());
        assertEquals(2, activity.snapshot().missedWrites());
    }
}
