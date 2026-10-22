package com.memos.web.stats;

public record StatsResponse(
        long totalDocuments,
        long readyDocuments,
        long processingDocuments,
        long failedDocuments,
        long totalConversations
) {}
