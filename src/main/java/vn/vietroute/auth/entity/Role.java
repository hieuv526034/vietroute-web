package vn.vietroute.auth.entity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;
import vn.vietroute.common.constant.ApplicationConstants.Regex;
import vn.vietroute.common.jdbc.JdbcIndex;
import vn.vietroute.common.jdbc.JdbcTable;

/** Quyền hệ thống; phạm vi nhà xe/chuyến phải được kiểm tra thêm tại service nghiệp vụ. */
@JdbcTable(
    name = "auth_roles",
    primaryKey = { "code" },
    indexes = {
        @JdbcIndex(name = "auth_roles_pkey", columns = { "code" }, unique = true),
        @JdbcIndex(name = "idx_roles_created_by", columns = { "created_by" }),
        @JdbcIndex(name = "idx_roles_updated_by", columns = { "updated_by" }),
    }
)
@Schema(type = "string", description = "Mã Role ổn định từ registry, JSON dạng chuỗi")
public final class Role {

    public static final Role ADMIN = new Role("ADMIN");

    public static final Role OPERATOR = new Role("OPERATOR");

    public static final Role DRIVER = new Role("DRIVER");

    public static final Role CUSTOMER = new Role("CUSTOMER");

    private final String code;

    private Role(String code) {
        this.code = code;
    }

    @JsonValue
    public String name() {
        return code;
    }

    public static Role[] values() {
        return new Role[] { ADMIN, OPERATOR, DRIVER, CUSTOMER };
    }

    /** Chỉ dựng mã; service còn phải kiểm role tồn tại/đang bật trong DB trước khi gán. */
    @JsonCreator
    public static Role valueOf(String code) {
        if (code == null || !code.matches(Regex.ROLE_CODE)) {
            throw new IllegalArgumentException("Mã role không hợp lệ.");
        }
        for (Role role : values()) {
            if (role.code.equals(code)) {
                return role;
            }
        }
        return new Role(code);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && code.equals(role.code);
    }

    @Override
    public int hashCode() {
        return code.hashCode();
    }

    @Override
    public String toString() {
        return code;
    }
}
