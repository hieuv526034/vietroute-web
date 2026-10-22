package vn.vietroute.common.job.worker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.JobSettings;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.handler.JobHandler;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.job.repository.JobRepository;

/** Poller và executor tách biệt; queue memory=0, chỉ nhận việc khi có luồng rảnh, không tạo thread vô hạn. */
@Component
@DependsOn("databaseInitializer")
public class JobWorker implements SmartLifecycle {

    private static final Logger LOG = Logger.getLogger(JobWorker.class.getName());

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    private final JobSettings settings;

    private final Map<String, JobHandler> handlers;

    private final Semaphore slots;

    private final AtomicLong completed = new AtomicLong();

    private final AtomicLong failed = new AtomicLong();

    private volatile long lastClaimNanos;

    private volatile boolean running;

    private ThreadPoolTaskExecutor executor;

    private ScheduledExecutorService poller;

    private long polls;

    public JobWorker(
        JobRepository jobs,
        JdbcTransactions transactions,
        JobSettings settings,
        List<JobHandler> handlers
    ) {
        this.jobs = jobs;
        this.transactions = transactions;
        this.settings = settings;
        this.handlers = handlers.stream().collect(Collectors.toUnmodifiableMap(JobHandler::type, Function.identity()));
        slots = new Semaphore(settings.getThreads());
    }

    @Override
    public synchronized void start() {
        if (running || !settings.isEnabled()) {
            return;
        }
        executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(settings.getThreads());
        executor.setMaxPoolSize(settings.getThreads());
        executor.setQueueCapacity(0);
        executor.setThreadNamePrefix("vietroute-job-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(45);
        executor.initialize();
        poller = Executors.newSingleThreadScheduledExecutor(task -> new Thread(task, "vietroute-job-poller"));
        running = true;
        poller.scheduleWithFixedDelay(this::poll, 0, settings.getPollSeconds(), TimeUnit.SECONDS);
    }

    /** Bắt lỗi mỗi lượt để scheduler không chết vĩnh viễn khi DB mất tạm thời. Không log payload/provider exception. */
    private void poll() {
        try {
            for (int index = 0; running && index < settings.getThreads() && slots.tryAcquire(); index++) {
                try {
                    executor.execute(this::runBatch);
                } catch (TaskRejectedException exception) {
                    slots.release();
                    break;
                }
            }
            if (++polls % Math.max(1, 3600 / settings.getPollSeconds()) == 0) {
                transactions.execute(connection -> {
                    jobs.cleanup(connection, settings.getRetentionDays());
                    return null;
                });
            }
        } catch (RuntimeException exception) {
            LOG.warning("Job poll unavailable; next scheduled poll will retry");
        }
    }

    /** Rút tối đa 32 việc liên tiếp mỗi slot, tránh để backlog chờ hết poll delay sau từng email nhanh. */
    private void runBatch() {
        try {
            for (int index = 0; running && index < 32; index++) {
                if (!runOne()) {
                    break;
                }
            }
        } finally {
            slots.release();
        }
    }

    /** Claim/ACK dùng transaction ngắn riêng; gửi mạng nằm ngoài transaction và không mang SecurityContext HTTP. */
    private boolean runOne() {
        try {
            Job job = transactions.execute(connection -> jobs.claim(connection, settings.getLeaseSeconds()));
            lastClaimNanos = System.nanoTime();
            if (job == null) {
                return false;
            }
            String error = null;
            boolean retry = true;
            try {
                JobHandler handler = handlers.get(job.type());
                if (handler == null) {
                    throw new JobFailure("UNKNOWN_HANDLER", false);
                }
                handler.handle(job);
            } catch (JobFailure exception) {
                error = exception.code();
                retry = exception.retryable();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                error = "WORKER_INTERRUPTED";
            } catch (Exception exception) {
                error = "HANDLER_FAILED";
            }
            String failureCode = error;
            boolean retryable = retry;
            int delay = retryDelay(job.attempts());
            boolean acknowledged = transactions.execute(connection ->
                jobs.finish(connection, job, failureCode, retryable, delay)
            );
            if (acknowledged && error == null) {
                completed.incrementAndGet();
            }
            if (error != null) {
                failed.incrementAndGet();
                LOG.warning("Job failed id=" + job.id() + " code=" + error);
            }
            return true;
        } catch (RuntimeException exception) {
            LOG.warning("Job transaction unavailable; lease expiry permits recovery");
            return false;
        }
    }

    /** Backoff mũ có jitter tránh nhiều instance retry cùng một thời điểm. */
    public static int retryDelay(int attempt) {
        return (
            Math.min(3600, 5 * (1 << Math.min(9, Math.max(0, attempt - 1)))) + ThreadLocalRandom.current().nextInt(5)
        );
    }

    @Override
    public synchronized void stop() {
        running = false;
        if (poller != null) {
            poller.shutdownNow();
        }
        if (executor != null) {
            executor.shutdown();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    public long completedCount() {
        return completed.get();
    }

    public long failedCount() {
        return failed.get();
    }

    public int activeCount() {
        return settings.getThreads() - slots.availablePermits();
    }

    /** Tuổi lần claim DB thành công gần nhất; -1 nghĩa chưa có mẫu, không giả heartbeat bằng cờ running. */
    public double claimAgeSeconds() {
        long last = lastClaimNanos;
        return last == 0 ? -1 : Math.max(0, (System.nanoTime() - last) / 1_000_000_000.0);
    }
}
