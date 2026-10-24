package vn.vietroute.auth.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import vn.vietroute.auth.entity.PermissionAction;
import vn.vietroute.common.constant.AccessConstants;

/**
 * Mô tả nhóm chức năng/quyền nền để danh bạ startup thu thập cùng mapping Spring.
 * Annotation khai báo guard HTTP và metadata: không đổi public, không thay scope/guard tại service.
 * Dùng mã compile-time trong AccessConstants để annotation và registry quyền không lệch mã.
 *
 * @see AccessConstants
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessFunction {
    String group();

    /** Chức năng trong cây; method để trống sẽ kế thừa function của class. */
    String function() default "";

    /** Module mới khai báo hành động; mã RESOURCE_ACTION được suy ra, không phải thêm hằng Permission/AccessRules. */
    PermissionAction[] actions() default {};

    /** Bỏ trống dùng group làm resource; đặt riêng khi nhóm menu và tài nguyên quyền khác nhau. */
    String resource() default "";

    /** ANY: quyền tương thích có thể thay thế; ALL: mọi quyền khai báo đều bắt buộc. */
    Match match() default Match.ANY;

    /** Chỉ dùng cho dữ liệu nhật ký/hiện diện nhạy cảm, không gắn ADMIN lên nghiệp vụ thông thường. */
    boolean adminOnly() default false;

    /** Self-service chỉ đòi principal đã xác thực; owner vẫn được kiểm trong service. */
    boolean authenticated() default false;

    enum Match {
        ANY,
        ALL,
    }

    /** Quyền guard HTTP và danh bạ; service giữ cùng điều kiện ở AccessRules để bảo vệ lời gọi ngoài HTTP. */
    String[] permissions() default {};
}
