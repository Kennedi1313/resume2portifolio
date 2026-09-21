package com.resume2portifolio.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resume2portifolio.domain.ResumeDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
@ConditionalOnProperty(name = "resume2portifolio.ai.provider", havingValue = "ollama")
public class OllamaResumeAIExtractor implements ResumeAIExtractor {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final String baseUrl;
    private final String model;

    public OllamaResumeAIExtractor(ObjectMapper objectMapper,
                                   @Value("${resume2portifolio.ai.base-url:http://ollama:11434}") String baseUrl,
                                   @Value("${resume2portifolio.ai.model:qwen2.5:3b}") String model) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.baseUrl = baseUrl.replaceAll("/$", "");
        this.model = model;
    }

    @Override
    public ResumeDocument extract(String text) {
        if (text == null || text.isBlank()) throw new IllegalArgumentException("Texto vazio");

        try {
            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "prompt", prompt(text),
                    "format", "json",
                    "stream", false,
                    "options", Map.of("temperature", 0.1)
            ));
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("Ollama respondeu HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode envelope = objectMapper.readTree(response.body());
            String json = envelope.path("response").asText();
            if (json.isBlank()) throw new IllegalStateException("Ollama retornou uma resposta vazia");
            return objectMapper.readValue(json, ResumeDocument.class);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível interpretar a resposta do Ollama", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("A chamada ao Ollama foi interrompida", exception);
        }
    }

    private String prompt(String text) {
        return """
                Você extrai dados estruturados de currículos. Responda SOMENTE com JSON válido, sem markdown.
                Use exatamente este formato e mantenha listas vazias quando não houver informação:
                {
                  "schemaVersion": "1.0",
                  "basics": {"name": "", "headline": "", "email": "", "phone": "", "location": "", "links": [{"label": "", "url": ""}]},
                  "summary": "",
                  "experience": [{"company": "", "role": "", "startDate": "", "endDate": "", "description": [""], "technologies": [""]}],
                  "education": [{"institution": "", "degree": "", "startDate": "", "endDate": ""}],
                  "skills": [""],
                  "languages": [{"name": "", "proficiency": ""}],
                  "projects": [{"name": "", "description": "", "technologies": [""], "url": ""}]
                }
                Não invente dados. Extraia somente o que estiver no texto. Datas podem usar o formato original.

                Texto do currículo:
                """ + text;
    }
}
