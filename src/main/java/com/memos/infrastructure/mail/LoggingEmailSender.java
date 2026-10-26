package com.memos.infrastructure.mail;

import com.memos.application.notification.EmailMessage;
import com.memos.application.notification.EmailSenderPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Used when no email provider is configured (e.g. local development): logs the message instead,
 * so flows like password reset can still be completed by copying the link from the log.
 */
@Slf4j
public class LoggingEmailSender implements EmailSenderPort {

    @Override
    public void send(EmailMessage message) {
        log.warn("Email sending is not configured (set BREVO_API_KEY). Email to {} not sent. Subject: {}\n{}",
                message.toEmail(), message.subject(), message.textBody());
    }
}
