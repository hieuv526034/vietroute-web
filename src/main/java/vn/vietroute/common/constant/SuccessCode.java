package vn.vietroute.common.constant;

import lombok.Getter;

/** Các kết quả thành công cũng có key dịch, không viết thông báo trực tiếp trong controller. */
@Getter
public enum SuccessCode {
    OK("OK000", "success.ok"),
    LOGIN("OK001", "success.login"),
    LOGOUT("OK002", "success.logout"),
    PASSWORD_CHANGED("OK003", "success.passwordChanged"),
    CREATED("OK004", "success.created"),
    ACCEPTED("OK005", "success.accepted");

    private final String code;

    private final String messageKey;

    SuccessCode(String code, String messageKey) {
        this.code = code;
        this.messageKey = messageKey;
    }
}
