package com.example.airag.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ChunkService {
    private static final int DEFAULT_CHUNK_SIZE = 1000; // characters
    private static final int DEFAULT_OVERLAP = 200;

    public List<String> splitText(String text) {
        return splitText(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> splitText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isEmpty()) return chunks;

        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + chunkSize);
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) chunks.add(chunk);

            if (end == text.length()) break;

            start = Math.max(0, end - overlap);
        }

        return chunks;
    }
}
