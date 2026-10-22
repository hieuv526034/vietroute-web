package vn.vietroute.business.media.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.vietroute.business.media.repository.MediaRepository;
import vn.vietroute.common.jdbc.JdbcTransactions;
import vn.vietroute.common.job.handler.JobHandler;
import vn.vietroute.common.job.model.Job;
import vn.vietroute.common.job.model.JobFailure;
import vn.vietroute.common.media.MediaStorage;

/** Xóa lặp an toàn: nhận trạng thái trước mạng; retry dùng lại publicId, không tạo file mới. */
@Component
@RequiredArgsConstructor
public class MediaCleanupHandler implements JobHandler {

    private final MediaRepository images;

    private final JdbcTransactions transactions;

    private final MediaStorage storage;

    private final ObjectMapper json;

    @Override
    public String type() {
        return "MEDIA_CLEANUP";
    }

    @Override
    public void handle(Job job) throws Exception {
        long id = json.readTree(job.payload()).path("id").asLong(-1);
        if (id < 1) {
            throw new JobFailure("MEDIA_PAYLOAD_INVALID", false);
        }
        var image = transactions.execute(connection -> images.claimCleanup(connection, id));
        if (image == null) {
            return;
        } // READY/DELETED không bị ảnh hưởng bởi job dọn upload cũ.
        storage.delete(image.getPublicId());
        transactions.execute(connection -> {
            images.deleted(connection, id);
            return null;
        });
    }
}
