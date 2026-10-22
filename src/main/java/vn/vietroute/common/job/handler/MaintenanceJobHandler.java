package vn.vietroute.common.job.handler;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.common.config.JobSettings;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.repository.JobRepository;

/** Handler bảo trì có thể enqueue từ module vận hành; xử lý lặp an toàn, xóa tối đa một lô nhỏ. */
@Component
@RequiredArgsConstructor
public class MaintenanceJobHandler implements JobHandler {

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    private final JobSettings settings;

    @Override
    public String type() {
        return "SYSTEM_CLEANUP";
    }

    @Override
    public void handle(Job job) {
        transactions.execute(connection -> {
            jobs.cleanup(connection, settings.getRetentionDays());
            return null;
        });
    }
}
