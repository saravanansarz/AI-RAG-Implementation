package com.example.airag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class AzureOpenAiService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(60))
            .build();

    @Value("${azure.oai.endpoint:}")
    private String azureEndpoint;

    @Value("${azure.oai.key:}")
    private String azureKey;

    @Value("${azure.oai.deployment:}")
    private String azureDeployment;

    // azure search info used by extension payload
    @Value("${azure.search.endpoint:}")
    private String azureSearchEndpoint;

    @Value("${azure.search.key:}")
    private String azureSearchKey;

    @Value("${azure.search.index:}")
    private String azureSearchIndex;

    public Map<String, Object> ask(String question) {
        return askWithContext(question, null);
    }

    public Map<String, Object> askWithContext(String question, String context) {
        try {
            String url = buildUrl();

            // build the request JSON with messages and extension dataSources like the python client did
            Map<String, Object> body = new HashMap<>();
            String userContent = question;
            if (context != null && !context.isEmpty()) {
                // Provide context and then the question
                userContent = "Context:\n" + context + "\n\nQuestion: " + question;
            }

            body.put("messages", new Object[]{
                    Map.of("role", "system", "content", "You are a helpful Assistant"),
                    Map.of("role", "user", "content", userContent)
            });
            body.put("max_tokens", 1000);
            body.put("temperature", 0.5);

            // add extension / dataSources block that the Python example uses via extra_body
            Map<String, Object> dsParams = new HashMap<>();
            dsParams.put("endpoint", azureSearchEndpoint);
            dsParams.put("key", azureSearchKey);
            dsParams.put("indexName", azureSearchIndex);

            Map<String, Object> ds = Map.of(
                    "type", "AzureCognitiveSearch",
                    "parameters", dsParams
            );

            body.put("dataSources", new Object[]{ds});

            String json = mapper.writeValueAsString(body);

            Request request = new Request.Builder()
                    .url(url)
                    .post(RequestBody.create(json, MediaType.parse("application/json")))
                    .addHeader("api-key", azureKey)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response resp = httpClient.newCall(request).execute()) {
                if (!resp.isSuccessful()) {
                    String err = resp.body() != null ? resp.body().string() : "";
                    return Map.of("error", "Request failed: " + resp.code(), "details", err);
                }

                String bodyStr = resp.body() != null ? resp.body().string() : "";
                JsonNode root = mapper.readTree(bodyStr);
                // try to follow OpenAI-style response -> choices[0].message.content
                JsonNode choices = root.path("choices");
                String answer = "";
                if (choices.isArray() && choices.size() > 0) {
                    answer = choices.get(0).path("message").path("content").asText("");
                }

                Map<String, Object> result = new HashMap<>();
                result.put("answer", answer);
                result.put("rawResponse", root);
                return result;
            }
        } catch (IOException e) {
            return Map.of("error", e.getMessage());
        }
    }

    private String buildUrl() {
        // Example: https://{endpoint}/openai/deployments/{deployment}/chat/completions?api-version=2023-09-01-preview
        String base = azureEndpoint;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return String.format("%s/openai/deployments/%s/chat/completions?api-version=2023-09-01-preview", base, azureDeployment);
    }
}
