package com.regulyn.consent.client;

import org.springframework.stereotype.Component;

@Component
public class NoopTranslationClient implements TranslationClient {
    @Override
    public String translate(String text, String fromLanguage, String toLanguage) {
        return null;
    }

    @Override
    public String getEngine() {
        return null;
    }

    @Override
    public String getEngineVersion() {
        return null;
    }
}
