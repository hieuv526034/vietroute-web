package vn.vietroute.common.util;

import java.util.Locale;
import java.util.UUID;
import vn.vietroute.common.constant.ErrorCode;
import vn.vietroute.common.validation.BusinessRules;

/** Sinh mã chỉ gồm chữ/số; UNIQUE trong DB vẫn là ràng buộc quyết định chống trùng. */
public final class CodeGenerator {
    private CodeGenerator() { }
    public static String generate(String prefix) {
        BusinessRules.require(prefix != null && prefix.matches("[A-Z]{2,6}"), ErrorCode.INVALID_CODE);
        return prefix + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
    }
}
