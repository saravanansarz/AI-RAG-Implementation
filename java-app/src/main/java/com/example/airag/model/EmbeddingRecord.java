package com.example.airag.model;

import java.util.Arrays;

public class EmbeddingRecord {
    private final String id;
    private final String text;
    private final double[] vector;

    public EmbeddingRecord(String id, String text, double[] vector) {
        this.id = id;
        this.text = text;
        this.vector = vector;
    }

    public String getId() { return id; }
    public String getText() { return text; }
    public double[] getVector() { return vector; }

    @Override
    public String toString() {
        return "EmbeddingRecord{" +
                "id='" + id + '\'' +
                ", text='" + (text.length() > 40 ? text.substring(0, 40) + "..." : text) + '\'' +
                ", vectorLen=" + (vector == null ? 0 : vector.length) +
                '}';
    }
}
