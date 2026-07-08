package com.schoolos.language;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Service
public class AzureTranslationService implements TranslationService {

    private static final String ENDPOINT = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=";

    private final String key;
    private final String region;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureTranslationService(@Value("${azure.translator.key}") String key,
                                    @Value("${azure.translator.region}") String region) {
        this.key = key;
        this.region = region;
    }

    @Override
    public String translate(String text, String targetLanguage) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Azure Translator is not configured (AZURE_TRANSLATOR_KEY / AZURE_SPEECH_KEY missing)");
        }
        if ("en".equalsIgnoreCase(targetLanguage)) {
            return text;
        }
        try {
            String body = objectMapper.writeValueAsString(new Object[] { new TextPayload(text) });
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ENDPOINT + targetLanguage))
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Ocp-Apim-Subscription-Region", region)
                    .header("Content-Type", "application/json")
                    .header("X-ClientTraceId", UUID.randomUUID().toString())
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Azure Translator request failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            return root.get(0).get("translations").get(0).get("text").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach Azure Translator", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to reach Azure Translator", e);
        }
    }

    private static class TextPayload {
        public final String Text;
        TextPayload(String text) { this.Text = text; }
    }
}
