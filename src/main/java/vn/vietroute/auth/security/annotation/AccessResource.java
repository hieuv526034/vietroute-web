package vn.vietroute.auth.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Định danh tài nguyên của service; khai báo một lần, các hàm chỉ chọn hành động. Không chứa role được cấp. */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface AccessResource {
    String value();
}
