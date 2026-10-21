package vn.vietroute.common.jdbc;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata JDBC để đọc và kiểm chứng schema; không phải ORM và không tự sinh DDL từ entity. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface JdbcTable {
    String schema();

    String name();

    String[] primaryKey();

    JdbcIndex[] indexes() default {};
}
