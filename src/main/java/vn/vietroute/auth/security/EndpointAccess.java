package vn.vietroute.auth.security;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import vn.vietroute.auth.dto.FunctionDTO;
import vn.vietroute.auth.entity.User;
import vn.vietroute.auth.initializer.AuthInitializer;
import vn.vietroute.auth.repo.UserRepo;
import vn.vietroute.common.constant.ApplicationConstants.Routes;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.exception.BusinessException;
import vn.vietroute.common.web.routing.WebRoutePolicy;

/** Một nơi thu thập endpoint và kiểm request; không đọc annotation quyền hoặc tên role để cấp quyền. */
@Component
public class EndpointAccess implements HandlerInterceptor, ApplicationListener<ContextRefreshedEvent> {

    private final ApplicationContext root;
    private final UserRepo users;
    private final AuthInitializer initializer;
    private volatile Map<String, FunctionDTO> endpoints = Map.of();

    public EndpointAccess(ApplicationContext root, UserRepo users, AuthInitializer initializer) {
        this.root = root;
        this.users = users;
        this.initializer = initializer;
    }

    /** Root chỉ nhận refresh servlet con trực tiếp, sau khi RequestMappingHandlerMapping đã hoàn chỉnh. */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        if (
            (context != root && context.getParent() != root) ||
            context.getBeansOfType(RequestMappingHandlerMapping.class).isEmpty()
        ) return;
        collect(context.getBean(RequestMappingHandlerMapping.class));
        initializer.synchronize(all());
    }

    public void collect(RequestMappingHandlerMapping mapping) {
        Map<String, FunctionDTO> found = new LinkedHashMap<>();
        mapping.getHandlerMethods().forEach((route, handler) -> {
            if (route.getMethodsCondition().isEmpty()) throw new IllegalStateException(
                "Endpoint cần HTTP method: " + handler.getShortLogMessage()
            );
            boolean api =
                AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), RestController.class) ||
                AnnotatedElementUtils.hasAnnotation(handler.getMethod(), ResponseBody.class);
            Operation operation = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), Operation.class);
            String name = handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
            for (String path : route.getPatternValues())
                for (RequestMethod verb : route.getMethodsCondition().getMethods()) {
                    String method = verb.name().equals("HEAD") ? "GET" : verb.name();
                    FunctionDTO dto = new FunctionDTO();
                    dto.setKind(api ? "ENDPOINT" : "MENU");
                    dto.setTarget(method + " " + path);
                    dto.setHttpMethod(method);
                    dto.setPathTemplate(path);
                    dto.setHandler(name);
                    dto.setDescription(operation == null || operation.summary().isBlank() ? name : operation.summary());
                    dto.setPublicEndpoint(WebRoutePolicy.publicEndpoint(path, method));
                    dto.setSelfService(WebRoutePolicy.selfService(path, method));
                    found.put(dto.getTarget(), dto);
                }
        });
        endpoints = Map.copyOf(found);
    }

    public List<FunctionDTO> all() {
        return endpoints
            .values()
            .stream()
            .sorted(Comparator.comparing(FunctionDTO::getKind).thenComparing(FunctionDTO::getTarget))
            .toList();
    }

    public FunctionDTO find(String target) {
        return endpoints.get(target);
    }

    public List<FunctionDTO> assignable() {
        return all()
            .stream()
            .filter(value -> !value.isPublicEndpoint() && !value.isSelfService())
            .toList();
    }

    /** Luôn đọc account/tokenVersion và mapping DB; lỗi DB không dùng snapshot hoặc JWT authorities cũ. */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) return true;
        String method = request.getMethod().equals("HEAD") ? "GET" : request.getMethod();
        String target = method + " " + request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        FunctionDTO endpoint = endpoints.get(target);
        if (endpoint == null) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        if (endpoint.isPublicEndpoint() && !target.equals("POST " + Routes.AUTH_LOGOUT)) return true;
        Authentication previous = SecurityContextHolder.getContext().getAuthentication();
        if (previous == null || !(previous.getPrincipal() instanceof UserPrincipal)) {
            if (endpoint.isPublicEndpoint()) return true;
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        SecurityContextHolder.getContext().setAuthentication(authorize(previous, target, endpoint.isSelfService()));
        return true;
    }

    /** Đọc lại account và quyền endpoint; dùng chung với interceptor, không cache grant. */
    public Authentication authorize(Authentication previous, String target, boolean selfService) {
        if (previous == null || !(previous.getPrincipal() instanceof UserPrincipal)) throw new BusinessException(
            ErrorCode.TOKEN_INVALID
        );
        UserPrincipal actor = (UserPrincipal) previous.getPrincipal();
        User user = users.load(actor.id());
        if (
            user == null ||
            !user.isActive() ||
            user.isDeleted() ||
            user.isActivationPending() ||
            user.getTokenVersion() != actor.tokenVersion()
        ) throw new BusinessException(ErrorCode.TOKEN_INVALID);
        if (!selfService && !target.equals("POST " + Routes.AUTH_LOGOUT)) {
            if (user.isMustChangePassword()) throw new BusinessException(ErrorCode.PASSWORD_CHANGE_REQUIRED);
            if (user.getEmailVerifiedAt() == null) throw new BusinessException(ErrorCode.EMAIL_VERIFICATION_REQUIRED);
            if (!user.getTargets().contains(target)) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        List<SimpleGrantedAuthority> authorities =
            user.isMustChangePassword() || user.getEmailVerifiedAt() == null
                ? List.of()
                : user.getTargets().stream().map(SimpleGrantedAuthority::new).toList();
        return UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(user, target), null, authorities);
    }
}
