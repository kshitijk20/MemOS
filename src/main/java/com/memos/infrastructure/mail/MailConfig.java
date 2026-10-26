package com.memos.infrastructure.mail;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.memos.application.notification.EmailSenderPort;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public EmailSenderPort emailSender(
            RestClient.Builder restClientBuilder,
            @Value("${memos.mail.brevo-api-key:}") String brevoApiKey,
            @Value("${memos.mail.from-email:}") String fromEmail,
            @Value("${memos.mail.from-name:MemOS}") String fromName) {
        if (brevoApiKey.isBlank() || fromEmail.isBlank()) {
            log.info("Email provider not configured; emails will be logged instead of sent");
            return new LoggingEmailSender();
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(15));
        log.info("Sending email through Brevo as {}", fromEmail);
        return new BrevoEmailSender(restClientBuilder.requestFactory(requestFactory).build(), brevoApiKey, fromEmail, fromName);
    }
}
