package com.regulyn.consent.model;

import java.time.Instant;

public record PublishVersionResponse(boolean published, Instant publishedAt) {}
