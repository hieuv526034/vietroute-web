package vn.vietroute.support;

import java.util.LinkedHashMap;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.monitoring.dto.request.ApiJournalSearchRequest;
import vn.vietroute.common.monitoring.dto.response.ApiJournalDetail;
import vn.vietroute.common.monitoring.dto.response.ApiJournalEntry;
import vn.vietroute.common.monitoring.repository.ApiJournalRepository;

/** Test double chỉ dùng kiểm filter/security; SQL, persistence/restart/cleanup được kiểm bằng PostgreSQL tạm CI. */
@Configuration
public class JournalTestConfig {

    @Bean
    @Primary
    public ApiJournalRepository isolatedJournalRepository() {
        return new FakeJournalRepository();
    }

    public static class FakeJournalRepository extends ApiJournalRepository {

        private final LinkedHashMap<UUID, ApiJournalDetail> rows = new LinkedHashMap<>();

        public FakeJournalRepository() {
            super(null, null);
        }

        @Override
        public synchronized void append(ApiJournalDetail detail) {
            rows.put(detail.entry().id(), detail);
        }

        @Override
        public void cleanup(int minutes, int capacity) {
            /* Không giả vờ kiểm SQL cleanup qua test double. */
        }

        @Override
        public synchronized PageResponse<ApiJournalEntry> page(ApiJournalSearchRequest search, int minutes) {
            var matched = rows
                .values()
                .stream()
                .map(ApiJournalDetail::entry)
                .filter(e -> search.getTraceId().isEmpty() || search.getTraceId().equals(e.traceId()))
                .toList();
            return PageResponse.of(
                matched.stream().skip(search.offset()).limit(search.getSize()).toList(),
                search,
                matched.size()
            );
        }

        @Override
        public synchronized ApiJournalDetail find(UUID id, int minutes) {
            return rows.get(id);
        }
    }
}
