package com.memos.web.document;

public record IngestionProgressResponse(String phase, int processed, int total, int percentComplete) {}
