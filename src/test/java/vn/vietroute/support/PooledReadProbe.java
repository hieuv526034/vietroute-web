package vn.vietroute.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.sql.DataSource;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.auth.security.DatabaseAccessGuard;
import vn.vietroute.common.jdbc.BaseRepository.SqlWork;
import vn.vietroute.common.jdbc.JdbcConnectionFactory;
import vn.vietroute.common.jdbc.JdbcSchema;
import vn.vietroute.common.jdbc.JdbcTransactions;

/** Hai node pool logic trong JVM test: dữ liệu/DAO/guard thật, chỉ DB loopback test, không phải tải HTTP/Render. */
public final class PooledReadProbe {

    private PooledReadProbe() {}

    public static void run(String url, String user, String schema, Authentication actor, SqlWork<?> read)
        throws Exception {
        if (
            !url.matches("jdbc:postgresql://127[.]0[.]0[.]1:[0-9]+/vietroute_business_test")
        ) throw new IllegalArgumentException("Chỉ DB test loopback");
        try (
            var first = new Node(url, user, schema, "perf03-node-1");
            var second = new Node(url, user, schema, "perf03-node-2")
        ) {
            var nodes = List.of(first, second);
            for (var node : nodes) {
                SecurityContextHolder.getContext().setAuthentication(actor);
                node.transactions.execute(read);
            }
            for (int concurrency : new int[] { 2, 4, 8 }) {
                var executor = Executors.newFixedThreadPool(concurrency);
                var start = new CountDownLatch(1);
                var results = new ArrayList<Future<List<Long>>>();
                var samples = new ArrayList<Long>();
                long began = System.nanoTime();
                try {
                    for (int worker = 0; worker < concurrency; worker++) {
                        var node = nodes.get(worker % 2);
                        results.add(
                            executor.submit(() -> {
                                start.await();
                                SecurityContextHolder.getContext().setAuthentication(actor);
                                try {
                                    var durations = new ArrayList<Long>();
                                    for (int index = 0; index < 25; index++) {
                                        long at = System.nanoTime();
                                        if (!node.guard.has(actor, "REPORT_READ")) throw new IllegalStateException(
                                            "Guard DB phải cho fixture ADMIN đọc"
                                        );
                                        node.transactions.execute(read);
                                        durations.add(System.nanoTime() - at);
                                    }
                                    return durations;
                                } finally {
                                    SecurityContextHolder.clearContext();
                                }
                            })
                        );
                    }
                    start.countDown();
                    for (var future : results) samples.addAll(future.get(90, TimeUnit.SECONDS));
                } finally {
                    executor.shutdownNow();
                }
                samples.sort(Long::compareTo);
                double p95 = samples.get((int) Math.ceil(samples.size() * 0.95) - 1) / 1_000_000d;
                if (samples.size() != concurrency * 25 || p95 > 2500) throw new IllegalStateException(
                    "Tải fixture yêu cầu đủ mẫu, p95 <=2500ms và 0 lỗi"
                );
                System.out.println(
                    "PERF03_LOAD " +
                        new ObjectMapper().writeValueAsString(
                            Map.of(
                                "concurrency",
                                concurrency,
                                "operations",
                                samples.size(),
                                "durationMs",
                                (System.nanoTime() - began) / 1_000_000d,
                                "p95Ms",
                                p95,
                                "p99Ms",
                                samples.get((int) Math.ceil(samples.size() * 0.99) - 1) / 1_000_000d,
                                "errors",
                                0,
                                "scope",
                                "two logical JDBC nodes, not HTTP/production"
                            )
                        )
                );
            }
            for (var node : nodes) {
                if (node.peakActive.get() > 2 || node.maxBorrow.get() > 3_000_000_000L) throw new IllegalStateException(
                    "Pool vượt trần fixture"
                );
                // Mẫu lúc borrow, không phải sampler liên tục: không gọi đây là đỉnh queue thực tế.
                System.out.println(
                    "PERF03_POOL " +
                        new ObjectMapper().writeValueAsString(
                            Map.of(
                                "node",
                                node.pool.getPoolName(),
                                "maximum",
                                2,
                                "sampledPeakActive",
                                node.peakActive.get(),
                                "sampledPeakWaiting",
                                node.peakWaiting.get(),
                                "borrows",
                                node.borrows.get(),
                                "meanBorrowMs",
                                node.borrowNanos.get() / 1_000_000d / node.borrows.get(),
                                "maxBorrowMs",
                                node.maxBorrow.get() / 1_000_000d
                            )
                        )
                );
            }
        }
    }

    private static final class Node implements AutoCloseable {

        private final HikariDataSource pool;
        private final JdbcTransactions transactions;
        private final DatabaseAccessGuard guard;
        private final AtomicLong borrowNanos = new AtomicLong();
        private final AtomicLong maxBorrow = new AtomicLong();
        private final AtomicLong borrows = new AtomicLong();
        private final AtomicInteger peakActive = new AtomicInteger();
        private final AtomicInteger peakWaiting = new AtomicInteger();

        Node(String url, String user, String schema, String name) {
            var config = new HikariConfig();
            config.setJdbcUrl(url);
            config.setUsername(user);
            config.setPassword("test_only");
            config.setMaximumPoolSize(2);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(3000);
            config.setPoolName(name);
            pool = new HikariDataSource(config);
            var source = (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[] { DataSource.class },
                (proxy, method, args) -> {
                    try {
                        if (method.getName().equals("getConnection")) {
                            peakWaiting.accumulateAndGet(
                                pool.getHikariPoolMXBean().getThreadsAwaitingConnection(),
                                Math::max
                            );
                            long at = System.nanoTime();
                            var connection = pool.getConnection();
                            long elapsed = System.nanoTime() - at;
                            borrows.incrementAndGet();
                            borrowNanos.addAndGet(elapsed);
                            maxBorrow.accumulateAndGet(elapsed, Math::max);
                            peakActive.accumulateAndGet(pool.getHikariPoolMXBean().getActiveConnections(), Math::max);
                            return connection;
                        }
                        return method.invoke(pool, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                }
            );
            var connections = new JdbcConnectionFactory(source, new JdbcSchema(schema));
            transactions = new JdbcTransactions(connections);
            guard = new DatabaseAccessGuard(new AuthorizationRepository(connections));
        }

        @Override
        public void close() {
            pool.close();
        }
    }
}
