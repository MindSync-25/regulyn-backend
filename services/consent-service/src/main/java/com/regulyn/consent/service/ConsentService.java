package com.regulyn.consent.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.consent.entity.ConsentRecord;
import com.regulyn.consent.model.ConsentRequest;
import com.regulyn.consent.model.ConsentResponse;
import com.regulyn.consent.repository.ConsentRecordRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class ConsentService {

  private final ConsentRecordRepository consentRepository;
  private final OutboxWriter outboxWriter;

  public ConsentService(ConsentRecordRepository consentRepository, OutboxWriter outboxWriter) {
    this.consentRepository = consentRepository;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public ConsentResponse createConsent(ConsentRequest request) {
    TenantContext context = TenantContextHolder.getContext();
    
    String receiptId = UUID.randomUUID().toString();
    String payload = String.format("%s|%s|%s|%s|%s",
        request.getUserId(),
        request.getPurpose(),
        request.getLanguage(),
        request.getNoticeText(),
        request.getSource());
    
    String payloadHash = hashPayload(payload);
    String noticeHash = hashPayload(request.getNoticeText());
    
    // Persist consent record
    ConsentRecord record = new ConsentRecord();
    record.setTenantId(context.getTenantId());
    record.setReceiptId(receiptId);
    record.setUserId(request.getUserId());
    record.setPurpose(request.getPurpose());
    record.setLanguage(request.getLanguage());
    record.setNoticeHash(noticeHash);
    record.setSource(request.getSource());
    record.setPayloadHash(payloadHash);
    record.setCreatedBy(context.getUserId());
    
    consentRepository.save(record);
    
    // Create event for outbox
    Map<String, Object> eventPayload = new HashMap<>();
    eventPayload.put("receiptId", receiptId);
    eventPayload.put("userId", request.getUserId());
    eventPayload.put("purpose", request.getPurpose());
    eventPayload.put("language", request.getLanguage());
    eventPayload.put("source", request.getSource());
    eventPayload.put("noticeHash", noticeHash); // Hash instead of raw notice text
    
    EventEnvelopeV1 envelope = EventFactory.create(
        "consent.created",
        "consent-service",
        "CONSENT",
        receiptId,
        eventPayload
    );
    
    outboxWriter.write(envelope);
    
    return new ConsentResponse(receiptId, payloadHash);
  }
  
  private String hashPayload(String payload) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("SHA-256 algorithm not found", e);
    }
  }
}
