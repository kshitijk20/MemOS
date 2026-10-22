package com.memos.application.ingestion;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.memos.domain.document.Document;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckIngestionRecovery implements ApplicationRunner {

    private final DocumentIngestionService documentIngestionService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Checking for interrupted document ingestions");
        documentIngestionService.recoverInterruptedIngestion();
        for (Document pending : documentIngestionService.findPendingDocuments()) {
            log.info("Resuming PENDING ingestion for document {}", pending.getId());
            documentIngestionService.ingest(pending.getId());
        }
    }
}
