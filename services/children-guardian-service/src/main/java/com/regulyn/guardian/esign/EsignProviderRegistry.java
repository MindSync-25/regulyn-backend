package com.regulyn.guardian.esign;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class EsignProviderRegistry {

    private final Map<ProviderId, EsignProvider> providers = new EnumMap<>(ProviderId.class);

    public EsignProviderRegistry(List<EsignProvider> providerList) {
        for (EsignProvider provider : providerList) {
            providers.put(provider.getProviderId(), provider);
        }
    }

    public EsignProvider getProvider(ProviderId providerId) {
        EsignProvider provider = providers.get(providerId);
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported eSign provider: " + providerId);
        }
        return provider;
    }
}
