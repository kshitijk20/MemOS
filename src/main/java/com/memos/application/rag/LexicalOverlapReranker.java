package com.memos.application.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

@Component
public class LexicalOverlapReranker implements RerankPort {

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }

        String haystackQuery = query == null ? "" : query.trim();
        String queryLower = haystackQuery.toLowerCase(Locale.ROOT);
        List<String> tokens = SearchQueryTerms.tokens(haystackQuery);
        List<String> identifiers = SearchQueryTerms.identifiers(haystackQuery);

        List<RetrievedChunk> scored = new ArrayList<>(candidates.size());
        for (RetrievedChunk chunk : candidates) {
            String content = chunk.content() == null ? "" : chunk.content();
            String contentLower = content.toLowerCase(Locale.ROOT);

            int overlap = 0;
            for (String token : tokens) {
                if (contentLower.contains(token.toLowerCase(Locale.ROOT))) {
                    overlap++;
                }
            }
            boolean exactPhrase = haystackQuery.length() >= 4 && contentLower.contains(queryLower);
            int identifierHits = 0;
            for (String identifier : identifiers) {
                if (content.contains(identifier) || contentLower.contains(identifier.toLowerCase(Locale.ROOT))) {
                    identifierHits++;
                }
            }

            double boosted = chunk.score()
                    + (0.08 * overlap)
                    + (exactPhrase ? 0.25 : 0)
                    + (0.2 * identifierHits);
            boolean lexicalHit = chunk.lexicalHit() || identifierHits > 0 || exactPhrase;
            scored.add(new RetrievedChunk(
                    chunk.chunkId(),
                    chunk.documentId(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.sourceTitle(),
                    boosted,
                    chunk.vectorScore(),
                    lexicalHit));
        }

        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());
        return scored.subList(0, Math.min(topK, scored.size()));
    }
}
