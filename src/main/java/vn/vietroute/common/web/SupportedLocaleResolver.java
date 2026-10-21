package vn.vietroute.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import org.springframework.web.servlet.LocaleResolver;

/** Locale cho API/UI: query → X-Locale → Accept-Language → tiếng Việt. */
public class SupportedLocaleResolver implements LocaleResolver {

    public static final Locale DEFAULT = Locale.forLanguageTag("vi");

    public static String explicitLanguage(HttpServletRequest request) {
        String query = request.getParameter("lang");
        return query != null ? query : request.getHeader("X-Locale");
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
        String accept = request.getHeader("Accept-Language");
        if (accept != null) {
            try {
                Locale match = Locale.lookup(Locale.LanguageRange.parse(accept), List.of(DEFAULT, Locale.ENGLISH));
                if (match != null) {
                    return match;
                }
            } catch (IllegalArgumentException ignored) {
                /* Header sai dùng mặc định, không lộ lỗi parser. */
            }
        }
        return DEFAULT;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("Use lang, X-Locale or Accept-Language");
    }
}
