package com.memos.application.notification;

/** A transactional email with plain-text and HTML bodies. */
public record EmailMessage(String toEmail, String toName, String subject, String textBody, String htmlBody) {
}
