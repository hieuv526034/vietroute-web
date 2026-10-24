package vn.vietroute.auth.service;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import vn.vietroute.common.mail.model.MailPayload;
import vn.vietroute.common.web.locale.SupportedLocaleResolver;

/** Dựng email tài khoản từ template cố định; mọi biến dùng th:text, không nhận HTML/link từ người dùng. */
@Component
public final class AccountMailComposer {

    private final MessageSource messages;

    private final SpringTemplateEngine engine;

    public AccountMailComposer(MessageSource messages) {
        this.messages = messages;
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("mail/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(true);
        engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
    }

    /** Kích hoạt và gắn email dùng cùng xác minh; reset có nội dung/thời hạn riêng, giữ token nguyên vẹn để sao chép. */
    public MailPayload compose(String to, String purpose, String token, Locale requestedLocale) {
        String key = switch (purpose) {
            case "ACTIVATE" -> "mail.account.activate";
            case "RESET" -> "mail.account.reset";
            default -> throw new IllegalArgumentException("Unsupported account mail purpose");
        };
        Locale locale =
            requestedLocale != null && "en".equals(requestedLocale.getLanguage())
                ? Locale.ENGLISH
                : SupportedLocaleResolver.DEFAULT;
        String subject = message(key + ".subject", locale);
        Context context = new Context(locale);
        context.setVariable("language", locale.getLanguage());
        context.setVariable("subject", subject);
        context.setVariable("heading", message(key + ".heading", locale));
        context.setVariable("intro", message(key + ".intro", locale));
        context.setVariable("expiry", message(key + ".expiry", locale));
        context.setVariable("instructions", message(key + ".instructions", locale));
        context.setVariable("code", token);
        for (String name : new String[] { "caption", "eyebrow", "codeLabel", "notice", "footer" }) {
            context.setVariable(name, message("mail.account." + name, locale));
        }
        return new MailPayload(
            to,
            subject,
            messages.getMessage(key + ".text", new Object[] { token }, locale),
            engine.process("account-code", context)
        );
    }

    private String message(String key, Locale locale) {
        return messages.getMessage(key, null, locale);
    }
}
