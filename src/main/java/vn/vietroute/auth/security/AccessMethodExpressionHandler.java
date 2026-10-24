package vn.vietroute.auth.security;

import java.util.function.Supplier;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.core.Authentication;

/** Truyền lời gọi server vào guard chung; không đọc tên hàm/tài nguyên từ payload hoặc HTTP header. */
public final class AccessMethodExpressionHandler extends DefaultMethodSecurityExpressionHandler {

    @Override
    public EvaluationContext createEvaluationContext(
        Supplier<Authentication> authentication,
        MethodInvocation invocation
    ) {
        EvaluationContext context = super.createEvaluationContext(authentication, invocation);
        context.setVariable("accessInvocation", invocation);
        return context;
    }
}
