package vn.vietroute.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;
import java.lang.annotation.*;
import vn.vietroute.common.constant.Constants;

/** Mã không dấu/khoảng trắng/ký tự đặc biệt; dùng thêm @NotBlank nếu trường bắt buộc. */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME) @Documented
@Constraint(validatedBy = {}) @ReportAsSingleViolation
@Pattern(regexp = Constants.CODE_PATTERN)
public @interface BusinessCode {
    String message() default "{validation.code}";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
