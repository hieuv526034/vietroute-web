package vn.vietroute.auth.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;

/** Danh bạ khai báo của node, không chứa authority user. Dùng chung cho sync và bảo vệ registry kể cả khi tắt ghi startup. */
@Component
public final class AccessPermissionDefinitions {

    private volatile Set<String> codes = collect(List.of(), Set.of());

    /** Collector servlet công bố một snapshot metadata sau khi mapping HTTP và service đã được validate. */
    public void remember(List<AccessResourceResponse> inventory, Set<String> serviceCodes) {
        codes = collect(inventory, serviceCodes);
    }

    public boolean contains(String code) {
        return codes.contains(code);
    }

    /** Cùng tập mã bootstrap, VIEW, HTTP (kể cả alias/extra) và service được sync và đánh dấu builtIn trên UI/API. */
    public static Set<String> collect(List<AccessResourceResponse> inventory, Set<String> serviceCodes) {
        Set<String> result = new LinkedHashSet<>();
        Arrays.stream(Permission.values()).map(Permission::name).forEach(result::add);
        AccessFeatures.definitions()
            .stream()
            .map(AccessFeatures.Feature::viewPermission)
            .filter(Objects::nonNull)
            .forEach(result::add);
        inventory.forEach(item -> result.addAll(item.declaredPermissions()));
        result.addAll(serviceCodes);
        result.forEach(Permission::valueOf);
        return Set.copyOf(result);
    }

    /** Module thêm key permission.CODE ở cả vi/en; thiếu key thì hiện đúng mã để không nhập nhằng các quyền mới. */
    public static String localizedName(Permission permission, ResourceBundle messages) {
        String key = permission.messageKey();
        return messages.containsKey(key) ? messages.getString(key) : permission.name();
    }
}
