package vn.vietroute.common.jdbc;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Mô tả index đã tạo bằng migration; thứ tự cột phải khớp truy vấn và index PostgreSQL. */
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface JdbcIndex {
    String name();

    String[] columns();

    boolean unique() default false;
}
