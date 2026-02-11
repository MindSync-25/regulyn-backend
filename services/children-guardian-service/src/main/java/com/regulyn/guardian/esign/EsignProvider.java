package com.regulyn.guardian.esign;

public interface EsignProvider {
    ProviderId getProviderId();

    EsignCreateRequestResult createSigningRequest(EsignCreateRequest cmd);

    EsignWebhookParseResult parseAndVerifyWebhook(EsignWebhookRequest req);
}
