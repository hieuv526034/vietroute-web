package vn.vietroute.common.monitoring.service;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.Mac;
import org.springframework.stereotype.Service;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.common.base.PageRequest;
import vn.vietroute.common.base.PageResponse;
import vn.vietroute.common.config.application.AppSettings;
import vn.vietroute.common.config.monitoring.ActivitySettings;
import vn.vietroute.common.constant.AccessConstants.Groups;
import vn.vietroute.common.monitoring.dto.response.ActivitySnapshot;
import vn.vietroute.common.monitoring.dto.response.OnlineVisitorResponse;
import vn.vietroute.common.monitoring.dto.response.RequestAnomalyResponse;
import vn.vietroute.common.monitoring.repository.ActivityRepository;

/** Chỉ giữ semaphore/counter vận hành trong RAM; từng lượt ghi đi thẳng DB, không giữ hàng đợi lịch sử. */
@Service
public class ActivityService {

    private final ActivitySettings settings;

    private final ActivityRepository repository;

    private final AppSettings app;

    private final Clock clock;

    private final Semaphore writer = new Semaphore(1);

    private final AtomicLong missed = new AtomicLong();

    private volatile long retryAfter;

    private volatile long cleanupAfter;

    public ActivityService(ActivitySettings settings, ActivityRepository repository, AppSettings app, Clock clock) {
        this.settings = settings;
        this.repository = repository;
        this.app = app;
        this.clock = clock;
    }

    /** Cờ startup để filter không mở DB khi chưa chủ động bật tính năng. */
    public boolean enabled() {
        return settings.isEnabled();
    }

    /** Domain separation tránh dùng chữ ký visitor thay cho JWT; không lưu IP hoặc cookie thô trong DB. */
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(app.getJwtKey());
            return HexFormat.of().formatHex(mac.doFinal(("activity:" + value).getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không tạo được định danh quan sát.");
        }
    }

    /** Cookie chỉ định danh quan sát, tuyệt đối không dùng làm credential xác thực. */
    public boolean validCookie(String value) {
        if (value == null || value.length() != 101 || value.charAt(36) != '.') {
            return false;
        }
        String expected = hash("cookie:" + value.substring(0, 36));
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.US_ASCII),
            value.substring(37).getBytes(StandardCharsets.US_ASCII)
        );
    }

    /** Ghi lỗi không làm lỗi nghiệp vụ; có backoff 30 giây và missed counter công khai cho quản trị. */
    public void record(String visitor, String source, Long user, int status) {
        if (!enabled()) {
            return;
        }
        long now = clock.millis();
        if (now < retryAfter || !writer.tryAcquire()) {
            missed.incrementAndGet();
            return;
        }
        try {
            if (
                !repository.record(
                    hash("visitor:" + visitor),
                    hash(user == null ? "source:" + source : "user:" + user),
                    user,
                    status
                )
            ) {
                missed.incrementAndGet();
            }
            if (now >= cleanupAfter) {
                cleanupAfter = now + 60000;
                repository.cleanup();
            }
        } catch (RuntimeException exception) {
            missed.incrementAndGet();
            retryAfter = now + 30000;
        } finally {
            writer.release();
        }
    }

    /** Đọc số liệu chia sẻ; lỗi DB được trả ra ngoài để UI không hiển thị số 0 giả. */
    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.READ, adminOnly = true)
    public ActivitySnapshot snapshot() {
        return enabled()
            ? repository.snapshot(missed.get())
            : new ActivitySnapshot(false, 0, 0, 0, 0, 0, 0, 0, 0, missed.get(), clock.instant());
    }

    /** Danh sách online phân trang SQL, userId là đối tượng quan sát đã xác thực. */
    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.READ, adminOnly = true)
    public PageResponse<OnlineVisitorResponse> online(PageRequest page) {
        return enabled() ? repository.online(page) : PageResponse.of(List.of(), page, 0);
    }

    /** Lấy tín hiệu vượt ngưỡng, không tự khóa user hoặc kết luận đây là tấn công. */
    @AccessOperation(resource = Groups.SYSTEM, action = PermissionAction.READ, adminOnly = true)
    public List<RequestAnomalyResponse> anomalies() {
        return enabled() ? repository.anomalies(settings.getRequestThreshold()) : List.of();
    }
}
