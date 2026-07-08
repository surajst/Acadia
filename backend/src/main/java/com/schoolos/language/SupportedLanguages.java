package com.schoolos.language;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Languages offered to parents for translated announcements/messages and TTS/STT. */
public final class SupportedLanguages {

    private static final Map<String, String> NAME_BY_CODE = new LinkedHashMap<>();
    static {
        NAME_BY_CODE.put("en", "English");
        NAME_BY_CODE.put("hi", "Hindi");
        NAME_BY_CODE.put("mr", "Marathi");
        NAME_BY_CODE.put("ta", "Tamil");
        NAME_BY_CODE.put("te", "Telugu");
        NAME_BY_CODE.put("bn", "Bengali");
        NAME_BY_CODE.put("gu", "Gujarati");
        NAME_BY_CODE.put("kn", "Kannada");
        NAME_BY_CODE.put("ml", "Malayalam");
        NAME_BY_CODE.put("pa", "Punjabi");
        NAME_BY_CODE.put("ur", "Urdu");
    }

    private SupportedLanguages() {}

    public static boolean isSupported(String code) {
        return code != null && NAME_BY_CODE.containsKey(code);
    }

    public static List<Map<String, String>> asList() {
        return NAME_BY_CODE.entrySet().stream()
                .map(e -> Map.of("code", e.getKey(), "name", e.getValue()))
                .toList();
    }
}
