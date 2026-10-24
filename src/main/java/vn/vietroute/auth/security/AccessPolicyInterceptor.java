package vn.vietroute.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import vn.vietroute.auth.dto.response.AccessPolicyResponse;
import vn.vietroute.auth.service.AccessManagementService;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;

/** Chặn cả URL trang và API theo template server; ẩn menu không phải hàng rào bảo mật. */
public final class AccessPolicyInterceptor implements HandlerInterceptor {

    private final AccessManagementService access;

    public AccessPolicyInterceptor(AccessManagementService access) {
        this.access = access;
    }

    /** Một snapshot trên request, dùng lại cho capabilities/header để không query policy nhiều lần. */
    public static List<AccessPolicyResponse> snapshot(HttpServletRequest request) {
        Object value = request.getAttribute(Http.ACCESS_POLICY_ATTRIBUTE);
        if (value instanceof List<?> items) return items
            .stream()
            .filter(AccessPolicyResponse.class::isInstance)
            .map(AccessPolicyResponse.class::cast)
            .toList();
        return List.of();
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        // Guard annotation thực thi cả khi route không cho cấu hình policy; DB không thể biến route thành public.
        if (
            handler instanceof HandlerMethod methodHandler &&
            !AccessTargets.isPublic(request.getMethod(), path) &&
            !AccessAuthorization.allows(AccessAuthorization.binding(methodHandler), auth)
        ) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        List<AccessPolicyResponse> policies = access.requestPolicies(auth);
        request.setAttribute(Http.ACCESS_POLICY_ATTRIBUTE, policies);
        // Spring tự phục vụ HEAD từ GET; cùng chính sách để HEAD không bỏ qua ràng buộc của GET.
        String method = request.getMethod().equals("HEAD") ? "GET" : request.getMethod();
        if (!AccessTargets.configurable(method, path)) {
            return true;
        }
        String target = method + " " + path;
        for (var policy : policies) {
            boolean applies =
                policy.target().equals(target) && (policy.kind().equals("ENDPOINT") || method.equals("GET"));
            if (applies && !AccessManagementService.allows(policy, auth)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        return true;
    }
}
