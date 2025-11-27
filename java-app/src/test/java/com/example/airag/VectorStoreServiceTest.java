package com.example.airag;

import com.example.airag.model.EmbeddingRecord;
import com.example.airag.service.VectorStoreService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class VectorStoreServiceTest {

    @Test
    public void testNearestNeighbors_basic() {
        VectorStoreService store = new VectorStoreService();

        EmbeddingRecord r1 = new EmbeddingRecord("1", "apple banana", new double[]{1, 0});
        EmbeddingRecord r2 = new EmbeddingRecord("2", "orange pear", new double[]{0.1, 0.9});
        EmbeddingRecord r3 = new EmbeddingRecord("3", "car vehicle", new double[]{0, 1});

        store.index(List.of(r1, r2, r3));

        List<EmbeddingRecord> best = store.nearestNeighbors(new double[]{1, 0}, 2);
        Assertions.assertEquals(2, best.size());
        Assertions.assertEquals("1", best.get(0).getId());
    }
}
