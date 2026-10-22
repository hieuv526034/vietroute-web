package vn.vietroute.common.traffic;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

/** Bộ đếm một instance có giới hạn bộ nhớ; không đẩy key còn hạn ra ngoài vì sẽ làm mất quota. */
public final class MemoryRateLimitStore implements RateLimitStore {

    private final Clock clock;

    private final int maxKeys;

    private final Map<String, Window> windows = new HashMap<>();

    public MemoryRateLimitStore(Clock clock, int maxKeys) {
        this.clock = clock;
        this.maxKeys = maxKeys;
    }

    @Override
    public synchronized Decision consume(String key, int limit, int seconds) {
        long now = clock.millis();
        Window window = windows.get(key);
        if (window == null || window.expiresAt <= now) {
            if (window == null && windows.size() >= maxKeys) {
                windows.values().removeIf(value -> value.expiresAt <= now);
                if (windows.size() >= maxKeys) {
                    return new Decision(false, seconds);
                }
            }
            window = new Window(now + seconds * 1000L);
            windows.put(key, window);
        }
        int retry = (int) Math.max(1, (window.expiresAt - now + 999) / 1000);
        if (window.count >= limit) {
            return new Decision(false, retry);
        }
        window.count++;
        return new Decision(true, 0);
    }

    private static final class Window {

        private final long expiresAt;
        private int count;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}
