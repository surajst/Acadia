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
import java.util.Map;

@Service
public class AzureSpeechService implements SpeechService {

    // Neural voice per language — extend as more languages are supported.
    // Falls back to an English voice if the requested language isn't mapped.
    private static final Map<String, String> VOICE_BY_LANGUAGE = Map.ofEntries(
            Map.entry("en", "en-US-JennyNeural"),
            Map.entry("hi", "hi-IN-SwaraNeural"),
            Map.entry("mr", "mr-IN-AarohiNeural"),
            Map.entry("ta", "ta-IN-PallaviNeural"),
            Map.entry("te", "te-IN-ShrutiNeural"),
            Map.entry("bn", "bn-IN-TanishaaNeural"),
            Map.entry("gu", "gu-IN-DhwaniNeural"),
            Map.entry("kn", "kn-IN-SapnaNeural"),
            Map.entry("ml", "ml-IN-SobhanaNeural"),
            Map.entry("pa", "pa-IN-VaaniNeural"),
            Map.entry("ur", "ur-IN-GulNeural")
    );

    // BCP-47 locale used by the STT recognition endpoint per language.
    private static final Map<String, String> LOCALE_BY_LANGUAGE = Map.ofEntries(
            Map.entry("en", "en-US"),
            Map.entry("hi", "hi-IN"),
            Map.entry("mr", "mr-IN"),
            Map.entry("ta", "ta-IN"),
            Map.entry("te", "te-IN"),
            Map.entry("bn", "bn-IN"),
            Map.entry("gu", "gu-IN"),
            Map.entry("kn", "kn-IN"),
            Map.entry("ml", "ml-IN"),
            Map.entry("pa", "pa-IN"),
            Map.entry("ur", "ur-IN")
    );

    private final String key;
    private final String region;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AzureSpeechService(@Value("${azure.speech.key}") String key,
                               @Value("${azure.speech.region}") String region) {
        this.key = key;
        this.region = region;
    }

    @Override
    public byte[] synthesizeSpeech(String text, String languageCode) {
        requireConfigured();
        String voice = VOICE_BY_LANGUAGE.getOrDefault(languageCode, VOICE_BY_LANGUAGE.get("en"));
        String locale = LOCALE_BY_LANGUAGE.getOrDefault(languageCode, "en-US");
        String ssml = "<speak version='1.0' xml:lang='" + locale + "'>"
                + "<voice xml:lang='" + locale + "' name='" + voice + "'>"
                + escapeXml(text)
                + "</voice></speak>";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://" + region + ".tts.speech.microsoft.com/cognitiveservices/v1"))
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Content-Type", "application/ssml+xml")
                    .header("X-Microsoft-OutputFormat", "audio-16khz-128kbitrate-mono-mp3")
                    .POST(HttpRequest.BodyPublishers.ofString(ssml, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Azure Speech TTS request failed: " + response.statusCode());
            }
            return response.body();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach Azure Speech", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to reach Azure Speech", e);
        }
    }

    @Override
    public String transcribe(byte[] audio, String languageCode) {
        requireConfigured();
        String locale = LOCALE_BY_LANGUAGE.getOrDefault(languageCode, "en-US");

        try {
            URI uri = URI.create("https://" + region
                    + ".stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=" + locale);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(uri)
                    .header("Ocp-Apim-Subscription-Key", key)
                    .header("Content-Type", "audio/wav; codecs=audio/pcm; samplerate=16000")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(audio))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Azure Speech STT request failed: " + response.statusCode() + " " + response.body());
            }
            JsonNode root = objectMapper.readTree(response.body());
            if (!"Success".equals(root.path("RecognitionStatus").asText())) {
                throw new IllegalStateException("Speech could not be recognized: " + root.path("RecognitionStatus").asText());
            }
            return root.path("DisplayText").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to reach Azure Speech", e);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to reach Azure Speech", e);
        }
    }

    private void requireConfigured() {
        if (key == null || key.isBlank() || region == null || region.isBlank()) {
            throw new IllegalStateException("Azure Speech is not configured (AZURE_SPEECH_KEY / AZURE_SPEECH_REGION missing)");
        }
    }

    private static String escapeXml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&apos;");
    }
}
