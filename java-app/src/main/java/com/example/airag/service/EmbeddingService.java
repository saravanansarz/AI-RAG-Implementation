package com.example.airag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Service
public class EmbeddingService {
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(60))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${azure.oai.endpoint:}")
    private String azureEndpoint;

    @Value("${azure.oai.key:}")
    private String azureKey;

    @Value("${azure.oai.embeddings-deployment:}")
    private String embeddingsDeployment;

    public List<double[]> embedTexts(List<String> texts) throws IOException {
        if (texts == null || texts.isEmpty()) return List.of();

        String url = buildUrl();

        Map<String, Object> body = new HashMap<>();
        // Azure embeddings endpoint expects `input` to be either a string or list of strings
        body.put("input", texts);

        String json = mapper.writeValueAsString(body);

        Request req = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, MediaType.parse("application/json")))
                .addHeader("api-key", azureKey)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response resp = httpClient.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                throw new IOException("Embeddings request failed: " + resp.code() + " " + (resp.body() != null ? resp.body().string() : ""));
            }

            String respBody = resp.body() != null ? resp.body().string() : "";
            JsonNode root = mapper.readTree(respBody);
            JsonNode data = root.path("data");
            List<double[]> vectors = new ArrayList<>();
            if (data.isArray()) {
                for (JsonNode item : data) {
                    JsonNode emb = item.path("embedding");
                    if (emb.isArray()) {
                        double[] v = new double[emb.size()];
                        for (int i = 0; i < emb.size(); i++) {
                            v[i] = emb.get(i).asDouble();
                        }
                        vectors.add(v);
                    }
                }
            }

            return vectors;
        }
    }

    private String buildUrl() {
        String base = azureEndpoint != null ? azureEndpoint : "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        // /openai/deployments/{deployment}/embeddings?api-version=2023-09-01-preview
        return String.format("%s/openai/deployments/%s/embeddings?api-version=2023-09-01-preview", base, embeddingsDeployment);
    }
}
