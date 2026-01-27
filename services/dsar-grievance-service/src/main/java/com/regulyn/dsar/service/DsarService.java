package com.regulyn.dsar.service;

import com.regulyn.dsar.model.DsarRequest;
import com.regulyn.dsar.model.DsarResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DsarService {
  
  private final Map<String, DsarRequest> requestStore = new HashMap<>();
  
  public DsarResponse submitRequest(DsarRequest request) {
    String requestId = UUID.randomUUID().toString();
    requestStore.put(requestId, request);
    return new DsarResponse(requestId, "RECEIVED");
  }
}
