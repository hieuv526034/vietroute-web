package vn.vietroute.common.config.application;

import java.util.function.Function;
import vn.vietroute.common.constant.ApplicationConstants.Env;

/** Đọc biến môi trường có kiểm tra khoảng; lỗi chỉ ghi tên key, không ghi giá trị/secret. */
public final class ConfigValues {

    /** Nguồn đọc cấu hình thống nhất; thông báo lỗi chỉ nêu tên key. */
    private final Function<String, String> source;

    public ConfigValues(Function<String, String> source) {
        this.source = source;
    }

    /** Chỉ nhận key thuộc registry, tránh gõ sai tên biến ở từng Settings. */
    public String raw(Env key) {
        return source.apply(key.key());
    }

    /** Chỉ dùng dự phòng khi thiếu biến; chuỗi rỗng tường minh vẫn được Settings kiểm tra theo hợp đồng. */
    public String text(Env key, String fallback) {
        String value = raw(key);
        return value == null ? fallback : value;
    }

    /** Số nguyên ngoài khoảng hoặc sai định dạng làm startup dừng, không âm thầm dùng ngưỡng không an toàn. */
    public int number(Env key, int fallback, int min, int max) {
        try {
            int value = Integer.parseInt(text(key, Integer.toString(fallback)));
            if (value >= min && value <= max) {
                return value;
            }
        } catch (NumberFormatException ignored) {
            // Không đính kèm giá trị cấu hình vào log.
        }
        throw new IllegalStateException(key.key() + " is outside the allowed range");
    }

    /** Chỉ nhận true/false, không hiểu nhầm typo thành false như Boolean.parseBoolean đơn thuần. */
    public boolean flag(Env key, boolean fallback) {
        String value = text(key, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalStateException(key.key() + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }
}
