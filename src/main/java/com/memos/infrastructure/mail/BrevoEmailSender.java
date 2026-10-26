package com.memos.infrastructure.mail;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.memos.application.notification.EmailMessage;
import com.memos.application.notification.EmailSenderPort;

/**
 * Sends email through Brevo's HTTP API (https://developers.brevo.com/reference/sendtransacemail).
 * An HTTP API rather than SMTP because free hosts such as Render block outbound SMTP ports.
 */
public class BrevoEmailSender implements EmailSenderPort {

    static final String ENDPOINT = "https://api.brevo.com/v3/smtp/email";

    private final RestClient restClient;
    private final String apiKey;
    private final String fromEmail;
    private final String fromName;

    public BrevoEmailSender(RestClient restClient, String apiKey, String fromEmail, String fromName) {
        this.restClient = restClient;
        this.apiKey = apiKey;
        this.fromEmail = fromEmail;
        this.fromName = fromName;
    }

    @Override
    public void send(EmailMessage message) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("email", fromEmail, "name", fromName),
                "to", List.of(Map.of("email", message.toEmail(), "name", message.toName())),
                "subject", message.subject(),
                "textContent", message.textBody(),
                "htmlContent", message.htmlBody());
        try {
            restClient.post()
                    .uri(ENDPOINT)
                    .header("api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // Brevo explains rejections (unverified sender, invalid key, quota) in the body.
            throw new IllegalStateException(
                    "Brevo rejected the email (HTTP " + e.getStatusCode().value() + "): " + e.getResponseBodyAsString(), e);
        }
    }
}
