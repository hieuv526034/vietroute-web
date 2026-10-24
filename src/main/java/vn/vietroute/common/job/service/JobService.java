package vn.vietroute.common.job.service;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.UserPrincipal;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.dto.response.JobResponse;
import vn.vietroute.common.job.model.JobStatus;
import vn.vietroute.common.job.repository.JobRepository;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobs;

    private final JdbcTransactions transactions;

    /** Chỉ trả trạng thái làm sạch, không trả payload chứa email. */
    @AccessOperation(resource = Groups.JOB, action = PermissionAction.READ)
    public PageResponse<JobResponse> page(PageRequest page, JobStatus status) {
        return jobs.page(page, status);
    }

    /** Tìm receipt theo UUID, chỉ lấy metadata được phép công bố. */
    @AccessOperation(resource = Groups.JOB, action = PermissionAction.READ)
    public JobResponse find(UUID id) {
        return jobs.find(id);
    }

    /** Retry thủ công giữ ID/dedup key; chỉ thực hiện một lần với version hiện hành. */
    @AccessOperation(resource = Groups.JOB, action = PermissionAction.RETRY)
    public JobResponse retry(UUID id, long version, UserPrincipal actor) {
        transactions.execute(connection -> {
            jobs.retry(connection, id, version, actor.id());
            return null;
        });
        return jobs.find(id);
    }
}
