package vn.vietroute.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        var auth = SecurityContextHolder.getContext().getAuthentication();
        List<AccessPolicyResponse> policies = access.requestPolicies(auth);
        request.setAttribute(Http.ACCESS_POLICY_ATTRIBUTE, policies);
        if (!AccessTargets.configurable(request.getMethod(), path)) {
            return true;
        }
        String target = request.getMethod() + " " + path;
        for (var policy : policies) {
            boolean applies =
                policy.target().equals(target) &&
                (policy.kind().equals("ENDPOINT") || request.getMethod().equals("GET"));
            if (applies && !AccessManagementService.allows(policy, auth)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        return true;
    }
}
