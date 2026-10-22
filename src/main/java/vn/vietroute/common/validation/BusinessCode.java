package vn.vietroute.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Mã không dấu/khoảng trắng/ký tự đặc biệt; dùng thêm @NotBlank nếu trường bắt buộc. */
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = {})
@ReportAsSingleViolation
@Pattern(regexp = Regex.BUSINESS_CODE)
public @interface BusinessCode {
    String message() default "{validation.code}";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
