package vn.vietroute.auth.security;

import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import org.springframework.security.core.Authentication;

/** Snapshot DB của tài nguyên được gán. Không có snapshot hoặc tập rỗng luôn từ chối tài nguyên riêng. */
@Getter
@Setter
public class ResourceEntitlements {

    private Set<String> targets;

    private Set<String> legacyPermissions;

    private String requestTarget;

    private AccessAuthorization.Binding requestBinding;

    public ResourceEntitlements(
        Set<String> targets,
        Set<String> legacyPermissions,
        String requestTarget,
        AccessAuthorization.Binding requestBinding
    ) {
        targets = Set.copyOf(targets);
        legacyPermissions = Set.copyOf(legacyPermissions);

        this.targets = targets;
        this.legacyPermissions = legacyPermissions;
        this.requestTarget = requestTarget;
        this.requestBinding = requestBinding;
    }

    public Set<String> targets() {
        return targets;
    }

    public Set<String> legacyPermissions() {
        return legacyPermissions;
    }

    public String requestTarget() {
        return requestTarget;
    }

    public AccessAuthorization.Binding requestBinding() {
        return requestBinding;
    }

    /** Chỉ tài nguyên có khai báo quyền mới được gán; đăng nhập đơn thuần không cấp quyền tài nguyên. */
    public boolean allows(String target, AccessAuthorization.Binding binding) {
        if (binding.permissions().isEmpty()) return false;
        if (targets.contains(target)) return true;
        // Role cũ chưa chuyển: chỉ dùng quyền của chính role cũ, không mượn projection từ role mới.
        return binding.match().equals("ALL")
            ? legacyPermissions.containsAll(binding.permissions())
            : binding.permissions().stream().anyMatch(legacyPermissions::contains);
    }

    /** Target được interceptor lấy từ HandlerMapping server, không đọc URL/header do client tự khai báo. */
    public static String currentTarget(Authentication authentication) {
        return authentication != null &&
            authentication.getDetails() instanceof ResourceEntitlements grants &&
            grants.requestBinding() != null &&
            !grants.requestBinding().permissions().isEmpty()
            ? grants.requestTarget()
            : null;
    }
}
