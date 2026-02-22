package com.regulyn.consent.client;

import org.springframework.stereotype.Component;

@Component
public class NoopTranslationClient implements TranslationClient {
    @Override
    public String translate(String text, String fromLanguage, String toLanguage) {
        // Dev/local stub — returns the source text unchanged.
        // In production, replace with a real translation service implementation.
        return text;
    }

    @Override
    public String getEngine() {
        return "noop";
    }

    @Override
    public String getEngineVersion() {
        return "1.0";
    }
}
