package vn.vietroute.common.monitoring.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.ApiJournalSettings;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalResponse;

/** Ring buffer từng node; lock ngắn, không I/O/DB/thread/job và không giữ servlet/raw exception. */
@Component
@RequiredArgsConstructor
public class ApiJournalStore {

    private final ApiJournalSettings settings;
    private final Clock clock;
    private final String instanceId = UUID.randomUUID().toString();
    private final ArrayDeque<ApiJournalDetail> records = new ArrayDeque<>();
    private long evicted;

    public String instanceId() {
        return instanceId;
    }

    public boolean enabled() {
        return settings.isEnabled();
    }

    public synchronized void add(ApiJournalDetail detail) {
        if (!enabled()) return;
        prune();
        records.addFirst(detail);
        while (records.size() > settings.getCapacity()) {
            records.removeLast();
            evicted++;
        }
    }

    private void prune() {
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(settings.getRetentionMinutes()));
        // Request hoàn tất không cùng thứ tự bắt đầu; quét bộ đệm nhỏ để TTL không bỏ sót request chậm.
        int before = records.size();
        records.removeIf(item -> item.entry().timestamp().isBefore(cutoff));
        evicted += before - records.size();
    }

    /** Lọc/paging trên snapshot giới hạn tối đa 1000, mới nhất trước. Không phân trang DB. */
    public synchronized ApiJournalResponse page(ApiJournalSearchRequest search) {
        prune();
        List<ApiJournalDetail> matches = records
            .stream()
            .filter(item -> {
                var e = item.entry();
                return (
                    (search.getTraceId().isEmpty() || e.traceId().equals(search.getTraceId())) &&
                    e.endpoint().contains(search.getEndpoint()) &&
                    (search.getMethod().isEmpty() || e.method().equals(search.getMethod())) &&
                    (search.getStatus() == 0 || e.status() == search.getStatus()) &&
                    e.durationMs() >= search.getMinDurationMs() &&
                    (search.getCode().isEmpty() || e.code().equals(search.getCode())) &&
                    (!search.isErrorsOnly() || e.status() >= 400)
                );
            })
            .toList();
        var content = matches
            .stream()
            .skip(search.offset())
            .limit(search.getSize())
            .map(ApiJournalDetail::entry)
            .toList();
        return new ApiJournalResponse(
            instanceId,
            enabled(),
            settings.getCapacity(),
            settings.getRetentionMinutes(),
            evicted,
            PageResponse.of(content, search, matches.size())
        );
    }

    public synchronized ApiJournalDetail find(UUID id) {
        prune();
        return records
            .stream()
            .filter(item -> item.entry().id().equals(id))
            .findFirst()
            .orElse(null);
    }
}
