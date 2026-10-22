package vn.vietroute.common.config;

import java.util.function.Function;

/** Đọc biến môi trường có kiểm tra khoảng; lỗi chỉ ghi tên key, không ghi giá trị/secret. */
public final class ConfigValues {

    private final Function<String, String> source;

    public ConfigValues(Function<String, String> source) {
        this.source = source;
    }

    public String text(String key, String fallback) {
        String value = source.apply(key);
        return value == null ? fallback : value;
    }

    public int number(String key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(text(key, Integer.toString(fallback)));
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Không đính kèm giá trị cấu hình vào log.
        }
        throw new IllegalStateException(key + " is outside the allowed range");
    }

    public boolean flag(String key, boolean fallback) {
        String value = text(key, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException(key + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
