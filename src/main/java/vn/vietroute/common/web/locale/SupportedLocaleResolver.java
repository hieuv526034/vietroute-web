package vn.vietroute.common.web.locale;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;
import vn.vietroute.common.constant.ApplicationConstants.Http;

/** Locale cho API/UI: query → X-Locale → tiếng Việt; không tự đổi theo ngôn ngữ trình duyệt. */
public class SupportedLocaleResolver implements LocaleResolver {

    public static final Locale DEFAULT = Locale.forLanguageTag("vi");

    public static String explicitLanguage(HttpServletRequest request) {
        String query = request.getParameter(Http.LANGUAGE_PARAMETER);
        return query != null ? query : request.getHeader(Http.LOCALE_HEADER);
    }

    public static boolean supported(String language) {
        return "vi".equalsIgnoreCase(language) || "en".equalsIgnoreCase(language);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        String explicit = explicitLanguage(request);
        if (explicit != null) {
            return supported(explicit) ? Locale.forLanguageTag(explicit) : DEFAULT;
        }
        return DEFAULT;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Use lang or X-Locale");
    }
}
