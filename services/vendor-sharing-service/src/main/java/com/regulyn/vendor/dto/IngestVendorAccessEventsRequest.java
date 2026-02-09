package com.regulyn.vendor.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class IngestVendorAccessEventsRequest {

    private List<VendorAccessTelemetryEventIngestDto> events;

    public List<VendorAccessTelemetryEventIngestDto> getEvents() {
        return events;
    }

    public void setEvents(List<VendorAccessTelemetryEventIngestDto> events) {
        this.events = events;
    }
}
