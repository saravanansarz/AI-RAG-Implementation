package com.example.airag.controller;

import com.example.airag.model.Question;
import com.example.airag.service.AzureOpenAiService;
import com.example.airag.service.ChunkService;
import com.example.airag.service.EmbeddingService;
import com.example.airag.service.VectorStoreService;
import com.example.airag.service.CsvService;
import com.example.airag.service.PdfService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qa-pdf")
public class QaPdfController {

    private final PdfService pdfService;
    private final CsvService csvService;
    private final AzureOpenAiService azureOpenAiService;
    private final ChunkService chunkService;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;

    @Value("${app.pdf.path:sustainable-finance-impact-report.pdf}")
    private String pdfPath;

    public QaPdfController(PdfService pdfService, CsvService csvService, AzureOpenAiService azureOpenAiService,
                           ChunkService chunkService, EmbeddingService embeddingService, VectorStoreService vectorStoreService) {
        this.pdfService = pdfService;
        this.csvService = csvService;
        this.azureOpenAiService = azureOpenAiService;
        this.chunkService = chunkService;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
    }

    @GetMapping("/")
    public ResponseEntity<?> qaPdf() throws IOException {
        // check and load
        String pdfText = pdfService.extractText(pdfPath);
        List<Question> questions = csvService.readQuestions("questions.csv");

        // Build RAG index: split -> embed -> index
        List<String> chunks = chunkService.splitText(pdfText);
        List<Map<String, Object>> results = new ArrayList<>();

        try {
            List<double[]> chunkVectors = embeddingService.embedTexts(chunks);
            List<com.example.airag.model.EmbeddingRecord> recs = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                String id = "chunk-" + i;
                double[] vector = i < chunkVectors.size() ? chunkVectors.get(i) : new double[0];
                recs.add(new com.example.airag.model.EmbeddingRecord(id, chunks.get(i), vector));
            }
            vectorStoreService.index(recs);
        } catch (Exception e) {
            // embedding error -> log and continue without retrieval
            System.err.println("Embedding/indexing failed: " + e.getMessage());
        }

        for (Question q : questions) {
            String questionText = q.getQuestion();

            // embed question and retrieve top contexts
            String context = "";
            try {
                List<double[]> qvec = embeddingService.embedTexts(List.of(questionText));
                if (!qvec.isEmpty()) {
                    List<com.example.airag.model.EmbeddingRecord> neighbors = vectorStoreService.nearestNeighbors(qvec.get(0), 3);
                    StringBuilder sb = new StringBuilder();
                    for (com.example.airag.model.EmbeddingRecord n : neighbors) {
                        sb.append("[p").append(n.getId()).append("] ").append(n.getText()).append("\n\n");
                    }
                    context = sb.toString();
                }
            } catch (Exception ex) {
                System.err.println("Failed to embed question or retrieve: " + ex.getMessage());
            }

            Map<String, Object> answer = azureOpenAiService.askWithContext(questionText, context);
            results.add(Map.of(
                    "question", questionText,
                    "type", q.getType(),
                    "expected_values", q.getExpectedValues(),
                    "answer", answer.getOrDefault("answer", ""),
                    "raw", answer,
                    "context", context
            ));
        }

        return ResponseEntity.ok(Map.of("results", results));
    }
}
