package vn.vietroute.auth.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import vn.vietroute.common.constant.AccessConstants;

/**
 * Mô tả nhóm chức năng/quyền nền để danh bạ startup thu thập cùng mapping Spring.
 * Annotation chỉ là metadata: không tự cấp quyền, không đổi public và không thay guard tại service.
 * Dùng mã compile-time trong AccessConstants để annotation và registry quyền không lệch mã.
 *
 * @see AccessConstants
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessFunction {
    String group();

    /** Các mã quyền liên quan để quản trị tra cứu; biểu thức all/any thực thi vẫn ở guard service. */
    String[] permissions() default {};
}
