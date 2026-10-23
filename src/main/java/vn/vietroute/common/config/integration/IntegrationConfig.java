package vn.vietroute.common.config.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.vietroute.common.config.application.ConfigurationEnvironment;
import vn.vietroute.common.mail.transport.GmailTransport;
import vn.vietroute.common.mail.transport.MailTransport;
import vn.vietroute.common.mail.transport.SmtpTransport;
import vn.vietroute.common.media.storage.CloudinaryMediaStorage;
import vn.vietroute.common.media.storage.MediaStorage;

/** Tạo bean hạ tầng theo trách nhiệm; Settings vẫn là nơi kiểm cấu hình và secrets. */
@Configuration
public class IntegrationConfig {

    @Bean
    public MediaSettings mediaSettings(ConfigurationEnvironment environment) {
        return new MediaSettings(environment);
    }

    @Bean
    public MediaStorage mediaStorage(MediaSettings settings) {
        return new CloudinaryMediaStorage(settings);
    }

    @Bean
    public MailSettings mailSettings(ConfigurationEnvironment environment) {
        return new MailSettings(environment);
    }

    @Bean
    public MailTransport mailTransport(MailSettings settings, ObjectMapper json, Clock clock) {
        return settings.getProvider().equals("smtp")
            ? new SmtpTransport(settings)
            : new GmailTransport(
                  settings,
                  json,
                  HttpClient.newBuilder()
                      .connectTimeout(Duration.ofSeconds(10))
                      .followRedirects(HttpClient.Redirect.NEVER)
                      .build(),
                  clock
              );
    }

    @Bean
    public JobSettings jobSettings(ConfigurationEnvironment environment) {
        return new JobSettings(environment);
    }
}
