package com.memos.application.notification;

/** Sends transactional email (e.g. password reset links). */
public interface EmailSenderPort {

    /** @throws RuntimeException if the provider rejects or can't be reached */
    void send(EmailMessage message);
}
