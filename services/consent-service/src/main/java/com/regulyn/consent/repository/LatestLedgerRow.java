package com.regulyn.consent.repository;

import java.time.Instant;
import java.util.UUID;

public interface LatestLedgerRow {
    UUID getLedgerId();
    UUID getDataPrincipalId();
    String getState();
    Instant getEffectiveAt();
    String getConsentTextHashSha256();
}
