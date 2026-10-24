package vn.vietroute.auth.security;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.response.AccessResourceResponse;
import vn.vietroute.auth.service.AccessResourceService;
import vn.vietroute.common.config.application.AppSettings;

/** Chỉ gắn vào servlet context: root migration xong và toàn bộ mapping MVC sẵn sàng mới được ghi DB. */
public final class AccessResourceSynchronizer implements ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext context;

    private final AppSettings settings;

    private final AccessResourceService resources;

    private final AtomicBoolean synchronizedOnce = new AtomicBoolean();

    private volatile List<AccessResourceResponse> inventory = List.of();

    /** Metadata source bất biến theo servlet context, không chứa/cache quyền user hoặc lịch sử request. */
    public List<AccessResourceResponse> inventory() {
        return inventory;
    }

    public AccessResourceSynchronizer(
        ApplicationContext context,
        AppSettings settings,
        AccessResourceService resources
    ) {
        this.context = context;
        this.settings = settings;
        this.resources = resources;
    }

    /** Không phản ứng với event context khác hoặc refresh lặp; lỗi DB làm startup thất bại rõ ràng, không báo đã đồng bộ. */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() != context || !synchronizedOnce.compareAndSet(false, true)) {
            return;
        }
        // Validate metadata cả khi tắt ghi DB; cờ sync chỉ điều khiển transaction startup.
        inventory = AccessTargets.discover(context.getBean(RequestMappingHandlerMapping.class));
        if (settings.isAccessSyncEnabled()) resources.synchronize(inventory);
    }
}
