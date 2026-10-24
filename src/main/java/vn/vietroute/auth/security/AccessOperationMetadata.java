package vn.vietroute.auth.security;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;
import vn.vietroute.auth.entity.Permission;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.auth.security.annotation.AccessOperation;
import vn.vietroute.auth.security.annotation.AccessResource;

/** Suy ra RESOURCE_ACTION từ metadata tin cậy của hàm, không cần một hằng/rule cho mỗi quyền. */
public record AccessOperationMetadata(
    List<String> any,
    List<String> required,
    boolean adminOnly,
    boolean authenticated
) {
    /** Startup kiểm và thu thập cả guard service không có HTTP, không cần gọi hàm nghiệp vụ để sinh registry. */
    public static Set<String> declaredCodes(Class<?> type, Method method) {
        var declaration = AnnotatedElementUtils.findMergedAnnotation(method, AccessOperation.class);
        if (declaration == null) declaration = AnnotatedElementUtils.findMergedAnnotation(type, AccessOperation.class);
        if (declaration == null || declaration.authenticated()) return Set.of();
        String resource = declaration.resource();
        var inherited = AnnotatedElementUtils.findMergedAnnotation(type, AccessResource.class);
        if (resource.isBlank() && inherited != null) resource = inherited.value();
        Set<String> codes = new LinkedHashSet<>();
        if (declaration.createArgument().isEmpty()) codes.add(resource + "_" + declaration.action().name());
        else {
            String argument = declaration.createArgument();
            boolean found = Arrays.stream(method.getParameters()).anyMatch(parameter ->
                parameter.getName().equals(argument)
            );
            if (!found) throw new IllegalStateException("Guard tạo/sửa thiếu tham số ở " + method.getName());
            codes.add(resource + "_CREATE");
            codes.add(resource + "_UPDATE");
        }
        codes.addAll(List.of(declaration.aliases()));
        codes.addAll(List.of(declaration.required()));
        codes.forEach(Permission::valueOf);
        return Set.copyOf(codes);
    }

    /** Chỉ metadata của method/target server thật tham gia chọn guard; tham số chỉ chọn nhánh tạo/sửa đã khai báo. */
    public static AccessOperationMetadata resolve(MethodInvocation invocation) {
        if (invocation == null || invocation.getThis() == null) throw new IllegalArgumentException(
            "Thiếu lời gọi được bảo vệ"
        );
        Class<?> type = ClassUtils.getUserClass(invocation.getThis());
        var method = AopUtils.getMostSpecificMethod(invocation.getMethod(), type);
        var declaration = AnnotatedElementUtils.findMergedAnnotation(method, AccessOperation.class);
        if (declaration == null) declaration = AnnotatedElementUtils.findMergedAnnotation(type, AccessOperation.class);
        if (declaration == null) throw new IllegalArgumentException("Hàm thiếu AccessOperation");
        if (declaration.authenticated()) return new AccessOperationMetadata(
            List.of(),
            List.of(),
            declaration.adminOnly(),
            true
        );
        String resource = declaration.resource();
        var inherited = AnnotatedElementUtils.findMergedAnnotation(type, AccessResource.class);
        if (resource.isBlank() && inherited != null) resource = inherited.value();
        PermissionAction action = declaration.action();
        if (!declaration.createArgument().isEmpty()) {
            var parameters = method.getParameters();
            int index = -1;
            for (int position = 0; position < parameters.length; position++) {
                if (parameters[position].getName().equals(declaration.createArgument())) index = position;
            }
            if (index < 0) throw new IllegalArgumentException("Tham số tạo/sửa không tồn tại");
            Object value = invocation.getArguments()[index];
            boolean create;
            if (value instanceof Boolean flag) create = flag;
            else if (value instanceof Number id && id.longValue() >= 0) create = id.longValue() == 0;
            else throw new IllegalArgumentException("Tham số tạo/sửa không hợp lệ");
            action = create ? PermissionAction.CREATE : PermissionAction.UPDATE;
        }
        var codes = new ArrayList<String>();
        codes.add(Permission.valueOf(resource + "_" + action.name()).name());
        Arrays.stream(declaration.aliases()).map(Permission::valueOf).map(Permission::name).forEach(codes::add);
        return new AccessOperationMetadata(
            List.copyOf(codes),
            Arrays.stream(declaration.required()).map(Permission::valueOf).map(Permission::name).toList(),
            declaration.adminOnly(),
            false
        );
    }
}
