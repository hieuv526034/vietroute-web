package vn.vietroute.common.job.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.repository.AccessChangeRepository;
import vn.vietroute.common.config.integration.JobSettings;
import vn.vietroute.common.config.monitoring.ApiJournalSettings;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.repository.JobRepository;
import vn.vietroute.common.monitoring.repository.ApiJournalRepository;

/** Handler bảo trì có thể enqueue từ module vận hành; xử lý lặp an toàn, xóa tối đa một lô nhỏ. */
@Component
@RequiredArgsConstructor
public class MaintenanceJobHandler implements JobHandler {

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    private final JobSettings settings;

    private final ApiJournalRepository journal;

    private final ApiJournalSettings journalSettings;

    private final AccessChangeRepository accessChanges;

    @Override
    public String type() {
        return "SYSTEM_CLEANUP";
    }

    @Override
    public void handle(Job job) {
        transactions.execute(connection -> {
            jobs.cleanup(connection, settings.getRetentionDays());
            accessChanges.cleanup(connection);
            return null;
        });
        // Transaction log riêng, giữ batch nhỏ; job retry nếu dọn DB lỗi, không tạo queue RAM.
        journal.cleanup(journalSettings.getRetentionMinutes(), journalSettings.getCapacity());
    }
}
