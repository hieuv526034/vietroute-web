package vn.vietroute.common.monitoring.service;

import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.monitoring.ApiJournalSettings;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;
import vn.vietroute.common.monitoring.repository.ApiJournalRepository;

/** Ghi trực tiếp/commit PostgreSQL, không cache/queue lịch sử trong RAM; lỗi log không làm lỗi nghiệp vụ. */
@Component
public class ApiJournalStore {

    private static final Logger LOG = Logger.getLogger(ApiJournalStore.class.getName());

    private final ApiJournalSettings settings;

    private final ApiJournalRepository repository;

    private final String instanceId = UUID.randomUUID().toString();

    private final Semaphore writer = new Semaphore(1);

    private final AtomicLong failedWrites = new AtomicLong();

    private final AtomicLong lastWarning = new AtomicLong();

    private volatile long retryAfter;

    private volatile long cleanupAfter;

    public ApiJournalStore(ApiJournalSettings settings, ApiJournalRepository repository) {
        this.settings = settings;
        this.repository = repository;
    }

    public String instanceId() {
        return instanceId;
    }

    public boolean enabled() {
        return settings.isEnabled();
    }

    /** Không enqueue nếu slot bận/DB lỗi: đếm lỗi, không giữ payload để gửi lại sau. */
    public void add(ApiJournalDetail detail) {
        if (!enabled()) return;
        long now = System.nanoTime();
        if ((retryAfter != 0 && now - retryAfter < 0) || !acquireWriter()) {
            failed(detail, now);
            return;
        }
        try {
            // Request khác có thể vừa mở circuit trong lúc request này chờ slot.
            if (retryAfter != 0 && System.nanoTime() - retryAfter < 0) {
                failed(detail, now);
                return;
            }
            repository.append(detail);
        } catch (RuntimeException exception) {
            retryAfter = now + 30_000_000_000L;
            failed(detail, now);
            return;
        } finally {
            writer.release();
        }
        // Insert commit trước cleanup: timeout dọn không được làm mất bản ghi vừa ghi thành công.
        if ((cleanupAfter == 0 || now - cleanupAfter >= 0) && writer.tryAcquire()) {
            try {
                cleanupAfter = now + 60_000_000_000L;
                repository.cleanup(settings.getRetentionMinutes(), settings.getCapacity());
            } catch (RuntimeException ignored) {
                LOG.warning("event=api_journal_cleanup_unavailable");
            } finally {
                writer.release();
            }
        }
    }

    /** Chỉ chờ slot tối đa 100ms trên request hiện hành; không tạo hàng đợi/batch log riêng. */
    private boolean acquireWriter() {
        try {
            return writer.tryAcquire(100, TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void failed(ApiJournalDetail detail, long now) {
        failedWrites.incrementAndGet();
        long previous = lastWarning.get();
        if ((previous == 0 || now - previous >= 1_000_000_000L) && lastWarning.compareAndSet(previous, now)) {
            LOG.warning("event=api_journal_write_unavailable traceId=" + detail.entry().traceId());
        }
    }

    /** Đọc DB dùng chung: node khác/restart vẫn thấy record đã commit. */
    public ApiJournalResponse page(ApiJournalSearchRequest search) {
        return new ApiJournalResponse(
            instanceId,
            enabled(),
            settings.getCapacity(),
            settings.getRetentionMinutes(),
            failedWrites.get(),
            "POSTGRESQL",
            repository.page(search, settings.getRetentionMinutes())
        );
    }

    public ApiJournalDetail find(UUID id) {
        return repository.find(id, settings.getRetentionMinutes());
    }
}
