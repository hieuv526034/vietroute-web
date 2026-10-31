package vn.vietroute.auth.security;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import vn.vietroute.auth.dto.record.capability.NavigationItemResponse;
import vn.vietroute.common.constant.AccessConstants.Functions;

/** Cùng định nghĩa cây cho header và màn gán; thêm function không phải viết lại if role tại header. */
public final class AccessNavigation {

    private AccessNavigation() {}

    public static List<NavigationItemResponse> build(
        Map<String, Boolean> allowed,
        String current,
        Locale locale,
        MessageSource messages
    ) {
        return children(null, allowed, current, locale, messages);
    }

    /** Bỏ nhóm rỗng sau lọc; cây đã được validate không có chu trình lúc context khởi động. */
    private static List<NavigationItemResponse> children(
        String parent,
        Map<String, Boolean> allowed,
        String current,
        Locale locale,
        MessageSource messages
    ) {
        return AccessFeatures.definitions()
            .stream()
            .filter(feature -> parent == null ? feature.parent() == null : parent.equals(feature.parent()))
            .map(feature -> {
                var nested = children(feature.code(), allowed, current, locale, messages);
                String path = allowed.getOrDefault(feature.code(), false) ? feature.path() : null;
                boolean active = current.equals(path) || nested.stream().anyMatch(NavigationItemResponse::active);
                return new NavigationItemResponse(
                    messages.getMessage(feature.nameKey(), null, locale),
                    path,
                    icon(feature.code()),
                    active,
                    nested
                );
            })
            .filter(item -> item.path() != null || !item.children().isEmpty())
            .toList();
    }

    private static String icon(String code) {
        return switch (code) {
            case Functions.ACCOUNTS -> "user";
            case Functions.SYSTEM -> "system";
            case
                Functions.CATALOG,
                Functions.LOCATION,
                Functions.PROVINCE,
                Functions.VEHICLE_TYPE,
                Functions.BOOKING_SOURCE -> "catalog";
            case Functions.BUSINESS, Functions.CARRIER, Functions.VEHICLE, Functions.DRIVER -> "system";
            case Functions.MEDIA -> "image";
            case Functions.JOBS -> "clock";
            case Functions.MONITORING, Functions.JOURNAL -> "activity";
            case Functions.CONFIGURATION -> "settings";
            default -> "shield";
        };
    }
}
