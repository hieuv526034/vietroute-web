package vn.vietroute.common.monitoring.service;

import com.zaxxer.hikari.HikariDataSource;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import vn.vietroute.common.config.MonitoringSettings;
import vn.vietroute.common.job.worker.JobWorker;
import vn.vietroute.common.monitoring.dto.response.RuntimeStatusResponse;

/** Đo bằng RAM với nhãn hữu hạn; scrape đọc registry/cache, tuyệt đối không mở DB hoặc gọi provider. */
@Service
public class ApplicationMetrics implements DisposableBean {

    private final String instanceId = UUID.randomUUID().toString();

    private final PrometheusMeterRegistry registry;

    private final HikariDataSource pool;

    private final JobWorker worker;

    private final DatabaseProbe database;

    private final MonitoringSettings settings;

    private final Clock clock;

    private final AtomicInteger active = new AtomicInteger();

    private final LongAdder errors = new LongAdder();

    private final LongAdder limited = new LongAdder();

    private final LongAdder unavailable = new LongAdder();

    private final Timer total;

    private final JvmGcMetrics gc = new JvmGcMetrics();

    public ApplicationMetrics(
        PrometheusMeterRegistry registry,
        HikariDataSource pool,
        JobWorker worker,
        DatabaseProbe database,
        MonitoringSettings settings,
        Clock clock
    ) {
        this.registry = registry;
        this.pool = pool;
        this.worker = worker;
        this.database = database;
        this.settings = settings;
        this.clock = clock;
        new JvmMemoryMetrics().bindTo(registry);
        gc.bindTo(registry);
        new JvmThreadMetrics().bindTo(registry);
        new ClassLoaderMetrics().bindTo(registry);
        new ProcessorMetrics().bindTo(registry);
        new UptimeMetrics().bindTo(registry);
        total = Timer.builder("vietroute.http.duration")
            .description("Completed HTTP requests excluding probes/static/scrapes")
            .serviceLevelObjectives(
                Duration.ofMillis(50),
                Duration.ofMillis(100),
                Duration.ofMillis(250),
                Duration.ofMillis(500),
                Duration.ofSeconds(1),
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(30)
            )
            .register(registry);
        Gauge.builder("vietroute.http.active", active, AtomicInteger::get).register(registry);
        FunctionCounter.builder("vietroute.http.errors", errors, LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("vietroute.http.limited", limited, LongAdder::doubleValue).register(registry);
        FunctionCounter.builder("vietroute.http.unavailable", unavailable, LongAdder::doubleValue).register(registry);
        Gauge.builder("vietroute.pool.active", pool, ApplicationMetrics::poolActive).register(registry);
        Gauge.builder("vietroute.pool.waiting", pool, ApplicationMetrics::poolWaiting).register(registry);
        Gauge.builder("vietroute.pool.maximum", pool, HikariDataSource::getMaximumPoolSize).register(registry);
        Gauge.builder("vietroute.worker.enabled", worker, value -> value.isRunning() ? 1 : 0).register(registry);
        Gauge.builder("vietroute.worker.active", worker, JobWorker::activeCount).register(registry);
        Gauge.builder("vietroute.worker.claim.age", worker, JobWorker::claimAgeSeconds).register(registry);
        FunctionCounter.builder("vietroute.worker.completed", worker, JobWorker::completedCount).register(registry);
        FunctionCounter.builder("vietroute.worker.failed", worker, JobWorker::failedCount).register(registry);
        Gauge.builder("vietroute.database.probe.enabled", settings, value ->
            value.isDatabaseEnabled() ? 1 : 0
        ).register(registry);
        Gauge.builder("vietroute.database.available", database, value -> {
            Boolean available = value.state().available();
            return available == null ? Double.NaN : available ? 1 : 0;
        }).register(registry);
        Gauge.builder("vietroute.database.probe.age", database, value -> {
            Instant last = value.state().lastSuccess();
            return last == null ? Double.NaN : Math.max(0, Duration.between(last, Instant.now(clock)).toSeconds());
        }).register(registry);
        Gauge.builder("vietroute.jobs.queued", database, value -> {
            var sample = value.state().queue();
            return sample == null ? Double.NaN : sample.queued();
        }).register(registry);
        Gauge.builder("vietroute.jobs.dead", database, value -> {
            var sample = value.state().queue();
            return sample == null ? Double.NaN : sample.dead();
        }).register(registry);
        Gauge.builder("vietroute.jobs.oldest.due", database, value -> {
            var sample = value.state().queue();
            return sample == null ? Double.NaN : sample.oldestDueSeconds();
        }).register(registry);
    }

    public void begin() {
        active.incrementAndGet();
    }

    /** Chốt trong finally để cả 401/403/429/503 trước controller vẫn được ghi nhận. */
    public void finish(String route, String method, int status, long nanos) {
        active.decrementAndGet();
        total.record(nanos, TimeUnit.NANOSECONDS);
        registry
            .counter("vietroute.http.responses", "route", route, "method", method, "status", Integer.toString(status))
            .increment();
        if (status >= 500) {
            errors.increment();
        }
        if (status == 429) {
            limited.increment();
        }
        if (status == 503) {
            unavailable.increment();
        }
    }

    /** Quyền xem chỉ số riêng với chức năng nghiệp vụ; JWT vẫn được kiểm như API quản trị khác. */
    @PreAuthorize("hasAuthority('SYSTEM_READ')")
    public RuntimeStatusResponse snapshot() {
        var memory = ManagementFactory.getMemoryMXBean();
        var state = database.state();
        return new RuntimeStatusResponse(
            instanceId,
            Instant.now(clock),
            ManagementFactory.getRuntimeMXBean().getUptime() / 1000,
            memory.getHeapMemoryUsage().getUsed(),
            memory.getHeapMemoryUsage().getMax(),
            memory.getNonHeapMemoryUsage().getUsed(),
            ProcessCpu.ratio(),
            ManagementFactory.getThreadMXBean().getThreadCount(),
            active.get(),
            total.count(),
            errors.sum(),
            limited.sum(),
            unavailable.sum(),
            total.mean(TimeUnit.MILLISECONDS),
            worker.isRunning(),
            worker.activeCount(),
            (int) poolActive(pool),
            (int) poolWaiting(pool),
            settings.isDatabaseEnabled(),
            state.available(),
            state.lastSuccess(),
            state.queue() == null ? null : state.queue().queued(),
            state.queue() == null ? null : state.queue().dead(),
            state.queue() == null ? null : state.queue().oldestDueSeconds()
        );
    }

    private static double poolActive(HikariDataSource source) {
        var bean = source.getHikariPoolMXBean();
        return bean == null ? 0 : bean.getActiveConnections();
    }

    private static double poolWaiting(HikariDataSource source) {
        var bean = source.getHikariPoolMXBean();
        return bean == null ? 0 : bean.getThreadsAwaitingConnection();
    }

    /** Spring gọi khi undeploy; không dựa annotation chỉ có sẵn nhờ Tomcat local profile. */
    @Override
    public void destroy() {
        gc.close();
    }
}
