package vn.vietroute.auth.security;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.method.HandlerMethod;
import vn.vietroute.auth.dto.record.resource.AccessResourceResponse;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.security.annotation.AccessFunction;
import vn.vietroute.auth.security.annotation.AccessFunction.Match;

/** Một cách tính guard HTTP cho interceptor, menu và capabilities; quyền hiệu lực do JWT filter nạp từ DB. */
public final class AccessAuthorization {

    private AccessAuthorization() {}

    public record Binding(
        String function,
        List<String> permissions,
        String match,
        boolean adminOnly,
        boolean authenticated
    ) {}

    /** Method khai báo quyền ghi đè class; function bỏ trống kế thừa class, tránh nhập lại đường dẫn. */
    public static Binding binding(HandlerMethod handler) {
        var type = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), AccessFunction.class);
        var method = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), AccessFunction.class);
        var declaration = method == null ? type : method;
        if (declaration == null) return new Binding("", List.of(), Match.ANY.name(), false, false);
        String function = declaration.authenticated()
            ? ""
            : declaration.function().isEmpty() && type != null
              ? type.function()
              : declaration.function();
        return new Binding(
            function,
            permissions(declaration),
            declaration.match().name(),
            declaration.adminOnly() || (type != null && type.adminOnly()),
            declaration.authenticated()
        );
    }

    public static boolean allows(Binding binding, Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) return false;
        Set<String> codes = auth
            .getAuthorities()
            .stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());
        if (binding.adminOnly() && !codes.contains("ROLE_" + Role.ADMIN.name())) return false;
        if (binding.permissions().isEmpty()) return binding.authenticated();
        return binding.match().equals(Match.ALL.name())
            ? codes.containsAll(binding.permissions())
            : binding.permissions().stream().anyMatch(codes::contains);
    }

    /** Guard nguồn vẫn bắt buộc; tài nguyên riêng còn phải có mapping DB chính xác. */
    public static boolean allows(Binding binding, Authentication auth, String target) {
        if (!allows(binding, auth)) return false;
        // Chỉ giữ đường tự phục vụ đã khai báo rõ trong source, có kiểm chủ sở hữu tại service.
        if (binding.permissions().isEmpty()) return binding.authenticated();
        return auth.getDetails() instanceof ResourceEntitlements grants && grants.allows(target, binding);
    }

    /** Gộp quyền sinh từ action với mã tương thích cũ; chỉ metadata server được dùng, không đoán theo HTTP verb. */
    public static List<String> permissions(AccessFunction declaration) {
        var result = new ArrayList<String>(List.of(declaration.permissions()));
        String resource = declaration.resource().isBlank() ? declaration.group() : declaration.resource();
        Arrays.stream(declaration.actions())
            .map(action -> resource + "_" + action.name())
            .forEach(result::add);
        return result.stream().map(Permission::valueOf).map(Permission::name).distinct().sorted().toList();
    }

    public static boolean allows(AccessResourceResponse item, Authentication auth) {
        return (
            item.publicRoute() ||
            allows(
                new Binding(item.function(), item.declaredPermissions(), item.match(), item.adminOnly(), false),
                auth,
                item.target()
            )
        );
    }
}
