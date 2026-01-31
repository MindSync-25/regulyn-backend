package com.regulyn.consent.model;

import java.time.Instant;
import java.util.UUID;

public record WithdrawConsentResponse(
    UUID receiptId,
    String status,
    Instant withdrawnAt
) {}
