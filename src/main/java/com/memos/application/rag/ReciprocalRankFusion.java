package com.memos.application.rag;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static List<RetrievedChunk> fuse(
            List<RetrievedChunk> vectorHits,
            List<RetrievedChunk> keywordHits,
            int rrfK) {
        Map<UUID, RetrievedChunk> byId = new LinkedHashMap<>();
        Map<UUID, Double> fused = new HashMap<>();
        Map<UUID, Double> vectorScores = new HashMap<>();
        Map<UUID, Boolean> lexical = new HashMap<>();

        addList(vectorHits, fused, rrfK, byId);
        for (RetrievedChunk chunk : vectorHits) {
            vectorScores.merge(chunk.chunkId(), chunk.vectorScore() > 0 ? chunk.vectorScore() : chunk.score(), Math::max);
        }
        addList(keywordHits, fused, rrfK, byId);
        for (RetrievedChunk chunk : keywordHits) {
            lexical.put(chunk.chunkId(), true);
        }

        List<Map.Entry<UUID, Double>> ranked = new ArrayList<>(fused.entrySet());
        ranked.sort(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()));

        List<RetrievedChunk> merged = new ArrayList<>(ranked.size());
        for (Map.Entry<UUID, Double> entry : ranked) {
            RetrievedChunk source = byId.get(entry.getKey());
            merged.add(new RetrievedChunk(
                    source.chunkId(),
                    source.documentId(),
                    source.chunkIndex(),
                    source.content(),
                    source.sourceTitle(),
                    entry.getValue(),
                    vectorScores.getOrDefault(entry.getKey(), 0.0),
                    lexical.getOrDefault(entry.getKey(), source.lexicalHit())));
        }
        return merged;
    }

    private static void addList(
            List<RetrievedChunk> hits,
            Map<UUID, Double> fused,
            int rrfK,
            Map<UUID, RetrievedChunk> byId) {
        for (int i = 0; i < hits.size(); i++) {
            RetrievedChunk chunk = hits.get(i);
            byId.putIfAbsent(chunk.chunkId(), chunk);
            double contribution = 1.0 / (rrfK + i + 1);
            fused.merge(chunk.chunkId(), contribution, Double::sum);
        }
    }
}
