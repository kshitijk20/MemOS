package com.memos.infrastructure.mail;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.memos.application.notification.EmailMessage;

class BrevoEmailSenderTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final BrevoEmailSender sender = new BrevoEmailSender(builder.build(), "key-123", "me@gmail.com", "MemOS");
    private final EmailMessage message = new EmailMessage("alice@example.com", "Alice", "Subject", "text", "<p>html</p>");

    @Test
    void postsTheMessageToBrevoWithTheApiKey() {
        server.expect(requestTo(BrevoEmailSender.ENDPOINT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("api-key", "key-123"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sender.email").value("me@gmail.com"))
                .andExpect(jsonPath("$.to[0].email").value("alice@example.com"))
                .andExpect(jsonPath("$.subject").value("Subject"))
                .andExpect(jsonPath("$.textContent").value("text"))
                .andRespond(withSuccess("{\"messageId\":\"<1@brevo>\"}", MediaType.APPLICATION_JSON));

        sender.send(message);

        server.verify();
    }

    @Test
    void reportsBrevosReasonWhenRejected() {
        server.expect(requestTo(BrevoEmailSender.ENDPOINT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"code\":\"invalid_parameter\",\"message\":\"sender not verified\"}"));

        assertThatThrownBy(() -> sender.send(message))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 400")
                .hasMessageContaining("sender not verified");
    }
}
