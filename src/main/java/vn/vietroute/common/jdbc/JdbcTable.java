package vn.vietroute.common.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata table trong schema cấu hình của connection; không phải ORM, không tự sinh DDL từ entity. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JdbcTable {
    String name();

    String[] primaryKey();

    JdbcIndex[] indexes() default {};
}
