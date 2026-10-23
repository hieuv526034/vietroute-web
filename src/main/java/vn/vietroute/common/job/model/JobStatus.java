package vn.vietroute.common.job.model;

/** Whitelist lọc queue; không nhận tên cột hoặc biểu thức SQL từ HTTP. */
public enum JobStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    DEAD,
}
