package com.schoolos.language;

/** Text-to-speech and speech-to-text for parent-facing local-language audio. */
public interface SpeechService {
    /** Returns raw MP3 audio bytes for the given text spoken in languageCode (e.g. "hi-IN"). */
    byte[] synthesizeSpeech(String text, String languageCode);

    /** Transcribes raw audio (WAV, 16kHz mono PCM) spoken in languageCode into text. */
    String transcribe(byte[] audio, String languageCode);
}
