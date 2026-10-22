package vn.vietroute.common.traffic;

/** Quota dùng cửa sổ bắt đầu ở request đầu tiên; adapter có thể là memory hoặc Redis dùng chung. */
public interface RateLimitStore extends AutoCloseable {
    Decision consume(String key, int limit, int seconds);

    @Override
    default void close() {}

    record Decision(boolean allowed, int retryAfterSeconds) {}
}
