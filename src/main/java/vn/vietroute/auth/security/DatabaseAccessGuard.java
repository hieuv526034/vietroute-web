package vn.vietroute.auth.security;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.vietroute.auth.entity.AuthorizationState;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.Role;
import vn.vietroute.auth.repository.AuthorizationRepository;
import vn.vietroute.common.constant.ApplicationConstants.SystemActor;

/**
 * Hàm SpEL dùng chung của PreAuthorize: mã truyền vào chỉ là yêu cầu, kết quả luôn đối chiếu DB.
 * Không tin authorities/role trong Authentication cũ và không cache theo request/node.
 * Mỗi hàm gộp đọc user + role + permission trong một statement, đóng connection trước nghiệp vụ.
 */
@Component("accessGuard")
@RequiredArgsConstructor
public class DatabaseAccessGuard {

    private final AuthorizationRepository repository;

    /** Guard duy nhất của annotation: nhận đúng hàm server, suy ra mã hành động rồi kiểm mapping hiện hành trong DB. */
    public boolean operation(Authentication authentication, MethodInvocation invocation) {
        AccessOperationMetadata operation;
        try {
            operation = AccessOperationMetadata.resolve(invocation);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        var current = state(authentication);
        if (current == null) return false;
        if (operation.adminOnly() && (!current.ready() || !current.roles().contains(Role.ADMIN))) return false;
        boolean allowed =
            operation.authenticated() ||
            (current.ready() &&
                current.permissions().containsAll(operation.required()) &&
                operation.any().stream().anyMatch(current.permissions()::contains));
        // Gọi bean trực tiếp không qua HTTP interceptor: thân service cũng phải kiểm ủy quyền bằng đúng snapshot mới.
        // Actor truyền vào còn role cũ sẽ bị AccessActors từ chối, thay vì dùng ADMIN/authority đã bị thu hồi.
        if (allowed) SecurityContextHolder.getContext().setAuthentication(snapshot(authentication, current));
        return allowed;
    }

    /** Auth tự phục vụ vẫn dùng được khi cần đổi mật khẩu tạm; phiên đã thu hồi/tắt bị từ chối. */
    public boolean authenticated(Authentication authentication) {
        return state(authentication) != null;
    }

    /** Quyền đơn lẻ vẫn phải tồn tại trong mapping active tại thời điểm gọi hàm. */
    public boolean has(Authentication authentication, String permission) {
        return any(authentication, permission);
    }

    /** Tương thích ANY ở endpoint; không hiểu WRITE là quyền toàn cục. */
    public boolean any(Authentication authentication, String... permissions) {
        return check(authentication, false, false, permissions);
    }

    /** ALL dùng một lần đọc DB để tránh các điều kiện nhìn hai trạng thái khác nhau. */
    public boolean all(Authentication authentication, String... permissions) {
        return check(authentication, true, false, permissions);
    }

    /** Đường cứu hộ cố định vẫn phải là ADMIN active trong DB, không tin ROLE_ADMIN do caller tự thêm. */
    public boolean admin(Authentication authentication) {
        var state = state(authentication);
        return state != null && state.ready() && state.roles().contains(Role.ADMIN);
    }

    /** ADMIN cùng toàn bộ quyền hành động, không có cơ chế ADMIN tự bỏ guard bắt buộc. */
    public boolean adminAll(Authentication authentication, String... permissions) {
        return check(authentication, true, true, permissions);
    }

    private boolean check(Authentication authentication, boolean all, boolean admin, String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        try {
            Arrays.stream(permissions).forEach(Permission::valueOf);
        } catch (IllegalArgumentException exception) {
            return false;
        }
        var current = state(authentication);
        if (current == null || !current.ready() || (admin && !current.roles().contains(Role.ADMIN))) return false;
        return all
            ? current.permissions().containsAll(List.of(permissions))
            : Arrays.stream(permissions).anyMatch(current.permissions()::contains);
    }

    /** HTTP interceptor cập nhật context bằng cùng projection DB, trước khi kiểm binding/policy và dựng capabilities. */
    public Authentication refresh(Authentication authentication) {
        var current = state(authentication);
        if (current == null) return null;
        return snapshot(authentication, current);
    }

    /** Tạo Authentication từ snapshot vừa kiểm, không đọc DB lần hai hoặc cache quyền qua request/node. */
    private Authentication snapshot(Authentication authentication, AuthorizationState current) {
        var principal = (UserPrincipal) authentication.getPrincipal();
        var granted = !current.ready()
            ? List.<SimpleGrantedAuthority>of()
            : Stream.concat(
                  current
                      .roles()
                      .stream()
                      .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name())),
                  current.permissions().stream().map(SimpleGrantedAuthority::new)
              ).toList();
        return UsernamePasswordAuthenticationToken.authenticated(
            new UserPrincipal(
                current.id(),
                principal.username(),
                current.roles(),
                current.tokenVersion(),
                principal.displayName(),
                principal.avatarUrl()
            ),
            null,
            granted
        );
    }

    /** SQL lỗi được truyền ra để trả 503 đã lọc; tuyệt đối không fallback về authorities cũ khi DB không đọc được. */
    private AuthorizationState state(Authentication authentication) {
        if (
            authentication == null ||
            !authentication.isAuthenticated() ||
            authentication instanceof AnonymousAuthenticationToken ||
            !(authentication.getPrincipal() instanceof UserPrincipal principal) ||
            principal.id() == null ||
            principal.id() == SystemActor.ID
        ) return null;
        var current = repository.find(principal.id());
        return current != null &&
            current.id() == principal.id() &&
            current.active() &&
            !current.activationPending() &&
            current.tokenVersion() == principal.tokenVersion()
            ? current
            : null;
    }
}
