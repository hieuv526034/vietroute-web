package vn.vietroute.auth.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.security.access.prepost.PreAuthorize;
import vn.vietroute.auth.entity.PermissionAction;

/**
 * Một guard chung cho mọi tài nguyên/hành động, không tạo thêm biểu thức AccessRules theo chức năng.
 * Spring truyền MethodInvocation thật; accessGuard đối chiếu tài khoản và mapping DB trước khi chạy hàm.
 */
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@PreAuthorize("@accessGuard.operation(authentication, #accessInvocation)")
public @interface AccessOperation {
    /** Bỏ trống để kế thừa AccessResource trên class; đây là định danh nghiệp vụ, không phải tên role. */
    String resource() default "";

    PermissionAction action() default PermissionAction.READ;

    /** Chỉ cho mã tương thích lịch sử; module mới không cần khai báo alias hoặc hằng quyền riêng. */
    String[] aliases() default {};

    /** Quyền bổ sung bắt buộc, ví dụ cấp tài khoản còn cần quyền gán role. */
    String[] required() default {};

    /** Giữ giới hạn dữ liệu nhạy cảm/cứu hộ, không gắn ADMIN cho nghiệp vụ thường. */
    boolean adminOnly() default false;

    /** Auth tự phục vụ chỉ đòi phiên hợp lệ; owner/version vẫn kiểm trong service. */
    boolean authenticated() default false;

    /** Hàm upsert: tên tham số boolean tạo mới, hoặc ID bằng 0 tạo mới; không nhận biểu thức SpEL từ DB/client. */
    String createArgument() default "";
}
