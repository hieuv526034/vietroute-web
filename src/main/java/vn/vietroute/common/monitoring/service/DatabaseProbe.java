package vn.vietroute.common.monitoring.service;

import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.monitoring.MonitoringSettings;
import vn.vietroute.common.monitoring.repository.MonitoringRepository;
import vn.vietroute.common.monitoring.repository.MonitoringRepository.QueueSample;

/** Một luồng, fixed delay không chồng poll; tắt mặc định để không giữ Neon luôn thức. */
@Component
@DependsOn("databaseInitializer")
public class DatabaseProbe implements SmartLifecycle {

    private static final Logger LOG = Logger.getLogger(DatabaseProbe.class.getName());

    private final MonitoringSettings settings;

    private final MonitoringRepository repository;

    private volatile State state = new State(null, null, null);

    private volatile boolean running;

    private ScheduledExecutorService scheduler;

    public DatabaseProbe(MonitoringSettings settings, MonitoringRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    @Override
    public synchronized void start() {
        if (running || !settings.isDatabaseEnabled()) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "vietroute-monitoring");
            thread.setDaemon(true);
            return thread;
        });
        running = true;
        scheduler.scheduleWithFixedDelay(this::sample, 0, settings.getPollSeconds(), TimeUnit.SECONDS);
    }

    /** Chỉ log chuyển trạng thái và hồi phục; không log exception chứa chuỗi kết nối. */
    public void sample() {
        if (!settings.isDatabaseEnabled()) {
            return;
        }
        State previous = state;
        try {
            QueueSample value = repository.sample();
            state = new State(true, value.timestamp(), value);
            if (Boolean.FALSE.equals(previous.available())) {
                LOG.info("event=database_probe_recovered");
            }
        } catch (Exception exception) {
            state = new State(false, previous.lastSuccess(), null);
            if (!Boolean.FALSE.equals(previous.available())) {
                LOG.warning("event=database_probe_unavailable");
            }
        }
    }

    public State state() {
        return state;
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public record State(Boolean available, Instant lastSuccess, QueueSample queue) {}
}
