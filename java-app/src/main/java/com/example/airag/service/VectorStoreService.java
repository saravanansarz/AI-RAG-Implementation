package com.example.airag.service;

import com.example.airag.model.EmbeddingRecord;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorStoreService {
    private final List<EmbeddingRecord> records = new ArrayList<>();

    public void index(List<EmbeddingRecord> recs) {
        records.clear();
        records.addAll(recs);
    }

    public List<EmbeddingRecord> nearestNeighbors(double[] query, int topK) {
        PriorityQueue<Map.Entry<EmbeddingRecord, Double>> pq = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));

        for (EmbeddingRecord r : records) {
            double sim = cosineSimilarity(query, r.getVector());
            pq.add(Map.entry(r, sim));
            if (pq.size() > topK) pq.poll();
        }

        List<EmbeddingRecord> out = new ArrayList<>(topK);
        while (!pq.isEmpty()) {
            out.add(pq.poll().getKey());
        }
        // items are returned from smallest to largest; reverse to return best-first
        Collections.reverse(out);
        return out;
    }

    private static double cosineSimilarity(double[] a, double[] b) {
        if (a == null || b == null) return 0.0;
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }
}
