package vn.vietroute.common.job;

/** Handler phải chạy có timeout nhỏ hơn lease, idempotent khi có thể và không giữ DB transaction lúc gọi mạng. */
public interface JobHandler {
    String type();
    void handle(Job job) throws Exception;
}
