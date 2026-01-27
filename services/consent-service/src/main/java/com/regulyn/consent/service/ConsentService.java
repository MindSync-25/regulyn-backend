package com.regulyn.consent.service;

import com.regulyn.consent.model.ConsentRequest;
import com.regulyn.consent.model.ConsentResponse;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
public class ConsentService {
  
  public ConsentResponse createConsent(ConsentRequest request) {
    String receiptId = UUID.randomUUID().toString();
    String payload = String.format("%s|%s|%s|%s|%s",
        request.getUserId(),
        request.getPurpose(),
        request.getLanguage(),
        request.getNoticeText(),
        request.getSource());
    
    String hash = hashPayload(payload);
    return new ConsentResponse(receiptId, hash);
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
