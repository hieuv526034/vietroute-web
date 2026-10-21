package vn.vietroute.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

/** Giới hạn theo byte UTF-8 để tránh mật khẩu bị BCrypt cắt sau 72 byte. */
@Target({ElementType.FIELD, ElementType.PARAMETER}) @Retention(RetentionPolicy.RUNTIME) @Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
public @interface StrongPassword {
    String message() default "{validation.password}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
