package vn.vietroute.common.monitoring.service;

import jakarta.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import vn.vietroute.common.constant.ApplicationConstants.Http;
import vn.vietroute.common.constant.ApplicationConstants.Regex;

/** Trạng thái đồng bộ theo request; chỉ các giá trị đã lọc được đính vào request, không giữ raw body. */
public final class ApiJournalState {

    public String requestBody = "{\"capture\":\"OMITTED_OR_NO_BODY\"}";
    public String responseBody = "{\"capture\":\"NO_ENVELOPE\"}";
    public String code = "UNKNOWN";

    public String messageKey = "";

    public String diagnostic = "HTTP_STATUS_ONLY";

    public String sqlState = "";

    private ApiJournalState() {}

    public static ApiJournalState start(HttpServletRequest request) {
        ApiJournalState state = new ApiJournalState();
        request.setAttribute(Http.JOURNAL_STATE, state);
        return state;
    }

    public static ApiJournalState get(HttpServletRequest request) {
        Object value = request.getAttribute(Http.JOURNAL_STATE);
        return value instanceof ApiJournalState state ? state : null;
    }

    /** Chỉ giữ SQLState chuẩn, không message/cause/SQL/parameter của driver. */
    public static void failure(HttpServletRequest request, Exception exception) {
        ApiJournalState state = get(request);
        if (state == null) return;
        Throwable cause = exception;
        for (int i = 0; i < 8 && cause != null; i++, cause = cause.getCause()) {
            if (cause instanceof SQLException sql) {
                String code = sql.getSQLState();
                if (code != null && code.matches(Regex.SQL_STATE)) {
                    state.sqlState = code;
                    state.diagnostic = switch (code.substring(0, 2)) {
                        case "08" -> "DB_CONNECTION";
                        case "28" -> "DB_AUTHENTICATION";
                        case "42" -> "DB_SCHEMA_OR_QUERY";
                        case "53" -> "DB_CAPACITY";
                        case "40" -> "DB_TRANSACTION";
                        case "55" -> "DB_LOCK_OR_STATE";
                        default -> "DB_FAILURE";
                    };
                    return;
                }
                state.diagnostic = "DB_FAILURE";
            }
        }
        if (state.sqlState.isEmpty() && !state.diagnostic.equals("DB_FAILURE")) state.diagnostic = "APPLICATION_ERROR";
    }
}
