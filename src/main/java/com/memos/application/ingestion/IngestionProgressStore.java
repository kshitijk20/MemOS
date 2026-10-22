package com.memos.application.ingestion;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class IngestionProgressStore {

    private final ConcurrentHashMap<UUID, IngestionProgress> store = new ConcurrentHashMap<>();

    public void update(UUID documentId, String phase, int processed, int total) {
        store.put(documentId, new IngestionProgress(phase, processed, total));
    }

    public Optional<IngestionProgress> get(UUID documentId) {
        return Optional.ofNullable(store.get(documentId));
    }

    public void remove(UUID documentId) {
        store.remove(documentId);
    }

    public record IngestionProgress(String phase, int processed, int total) {
        public int percentComplete() {
            return total == 0 ? 0 : (int) Math.round(100.0 * processed / total);
        }
    }
}
