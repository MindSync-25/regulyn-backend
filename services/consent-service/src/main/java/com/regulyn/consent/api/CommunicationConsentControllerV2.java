package com.regulyn.consent.api;

import com.regulyn.consent.entity.CommunicationChannel;
import com.regulyn.consent.model.*;
import com.regulyn.consent.service.CommunicationConsentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/api/v2/consent/communication")
public class CommunicationConsentControllerV2 {

    private final CommunicationConsentService communicationConsentService;

    public CommunicationConsentControllerV2(CommunicationConsentService communicationConsentService) {
        this.communicationConsentService = communicationConsentService;
    }

    @PostMapping("/channels/{channel}/opt-in")
    public ResponseEntity<CommunicationConsentResponse> optIn(
            @PathVariable("channel") String channel,
            @RequestBody CommunicationConsentRequest request) {
        return ResponseEntity.ok(
                communicationConsentService.recordOptIn(parseChannel(channel), request)
        );
    }

    @PostMapping("/channels/{channel}/opt-out")
    public ResponseEntity<CommunicationConsentResponse> optOut(
            @PathVariable("channel") String channel,
            @RequestBody CommunicationConsentRequest request) {
        return ResponseEntity.ok(
                communicationConsentService.recordOptOut(parseChannel(channel), request)
        );
    }

    @GetMapping("/channels/{channel}/status")
    public ResponseEntity<CommunicationConsentStatusResponse> getStatus(
            @PathVariable("channel") String channel,
            @RequestParam("dataPrincipalId") UUID dataPrincipalId) {
        return ResponseEntity.ok(
                communicationConsentService.getStatus(parseChannel(channel), dataPrincipalId)
        );
    }

    @PostMapping("/batch/status")
    public ResponseEntity<CommunicationConsentBatchResponse> batchStatus(
            @RequestBody CommunicationConsentBatchRequest request) {
        if (request == null || request.channel() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "channel is required");
        }
        CommunicationChannel channel = parseChannel(request.channel());
        List<UUID> principalIds = request.recipients() == null ? List.of() :
                request.recipients().stream()
                        .map(CommunicationConsentBatchRecipient::dataPrincipalId)
                        .filter(id -> id != null)
                        .toList();

        return ResponseEntity.ok(
                communicationConsentService.batchStatus(channel, principalIds)
        );
    }

    private CommunicationChannel parseChannel(String channel) {
        try {
            return CommunicationChannel.valueOf(channel.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown channel: " + channel);
        }
    }
}
