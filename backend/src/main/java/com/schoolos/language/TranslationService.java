package com.schoolos.language;

/** Translates text into a target language (BCP-47 code, e.g. "hi", "mr"). */
public interface TranslationService {
    String translate(String text, String targetLanguage);
}
