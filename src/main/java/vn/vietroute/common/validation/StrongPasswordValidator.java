package vn.vietroute.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Mật khẩu có ít nhất 8 ký tự, đủ hoa/thường/số/ký tự khác; không trim mật khẩu. */
public class StrongPasswordValidator implements ConstraintValidator<StrongPassword, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return valid(value);
    }

    public static boolean valid(String value) {
        return (
            value != null &&
            value.length() >= 8 &&
            value.getBytes(StandardCharsets.UTF_8).length <= 72 &&
            value.chars().anyMatch(Character::isUpperCase) &&
            value.chars().anyMatch(Character::isLowerCase) &&
            value.chars().anyMatch(Character::isDigit) &&
            value.chars().anyMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))
        );
    }
}
