package vn.vietroute.common.traffic;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.resource.DefaultClientResources;
import java.time.Duration;

/** Lua cập nhật quota/TTL nguyên tử giữa các instance; Redis lỗi phải chặn thay vì bỏ qua bảo vệ. */
public final class RedisRateLimitStore implements RateLimitStore {

    private static final String SCRIPT = """
    local count = tonumber(redis.call('GET', KEYS[1]) or '0')
    if count >= tonumber(ARGV[1]) then return math.max(1, redis.call('PTTL', KEYS[1])) end
    local value = redis.call('INCR', KEYS[1])
    if value == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[2]) end
    return 0
    """;
    private final RedisURI uri;

    private DefaultClientResources resources;

    private RedisClient client;

    private StatefulRedisConnection<String, String> connection;

    public RedisRateLimitStore(String url) {
        uri = RedisURI.create(url);
        uri.setTimeout(Duration.ofSeconds(2));
    }

    /** Kết nối tạo lười; Netty chỉ dùng số luồng nhỏ, không theo số CPU của host Render. */
    private synchronized StatefulRedisConnection<String, String> connection() {
        if (client == null) {
            resources = DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
            client = RedisClient.create(resources, uri);
        }
        if (connection == null || !connection.isOpen()) {
            connection = client.connect();
        }
        return connection;
    }

    @Override
    public Decision consume(String key, int limit, int seconds) {
        Number retry = connection()
            .sync()
            .eval(
                SCRIPT,
                ScriptOutputType.INTEGER,
                new String[] { "vietroute:rate:" + key },
                Integer.toString(limit),
                Long.toString(seconds * 1000L)
            );
        return new Decision(retry.longValue() == 0, (int) Math.max(1, (retry.longValue() + 999) / 1000));
    }

    @Override
    public synchronized void close() {
        if (connection != null) {
            connection.close();
        }
        if (client != null) {
            client.shutdown();
        }
        if (resources != null) {
            resources.shutdown();
        }
    }
}
