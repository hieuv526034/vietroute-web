package vn.vietroute.common.constant;

import java.util.Arrays;
import lombok.Getter;

/** Mã field validation riêng, không phụ thuộc câu dịch hoặc tên annotation trong Spring. */
@Getter
public enum ValidationCode {
    REQUIRED("VAL001", "validation.required"),
    INVALID("VAL002", "validation.invalid"),
    CODE("VAL003", "validation.code"),
    USERNAME("VAL004", "validation.username"),
    PASSWORD("VAL005", "validation.password"),
    PASSWORD_LENGTH("VAL006", "validation.passwordLength"),
    LENGTH("VAL007", "validation.length"),
    PAGE("VAL008", "validation.page"),
    SIZE("VAL009", "validation.size"),
    SORT_ORDER("VAL010", "validation.sortOrder"),
    USERNAME_SEARCH("VAL011", "validation.usernameSearch"),
    LATITUDE("VAL012", "validation.location.latitude"),
    LONGITUDE("VAL013", "validation.location.longitude"),
    COORDINATE_PRECISION("VAL014", "validation.location.coordinatePrecision");

    private final String code;

    private final String messageKey;

    ValidationCode(String code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }

    public static ValidationCode fromKey(String key) {
        return Arrays.stream(values())
            .filter(value -> value.messageKey.equals(key))
            .findFirst()
            .orElse(INVALID);
    }
}
