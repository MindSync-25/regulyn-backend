package com.regulyn.consent.client;

public interface TranslationClient {
    String translate(String text, String fromLanguage, String toLanguage);

    default String getEngine() {
        return null;
    }

    default String getEngineVersion() {
        return null;
    }
}
