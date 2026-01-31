package com.regulyn.dsar.service;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.entity.DsarRequestEntity;
import com.regulyn.dsar.model.DsarRequest;
import com.regulyn.dsar.model.DsarResponse;
import com.regulyn.dsar.repository.DsarRequestRepository;
import com.regulyn.events.factory.EventFactory;
import com.regulyn.events.model.EventEnvelopeV1;
import com.regulyn.events.outbox.OutboxWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class DsarService {

  private final DsarRequestRepository requestRepository;
  private final OutboxWriter outboxWriter;

  public DsarService(DsarRequestRepository requestRepository, OutboxWriter outboxWriter) {
    this.requestRepository = requestRepository;
    this.outboxWriter = outboxWriter;
  }

  @Transactional
  public DsarResponse submitRequest(DsarRequest request) {
    TenantContext context = TenantContextHolder.getContext();
    
    String requestId = UUID.randomUUID().toString();
    String status = "RECEIVED";
    
    // Persist DSAR request
    DsarRequestEntity entity = new DsarRequestEntity();
    entity.setTenantId(context.getTenantId());
    entity.setRequestId(requestId);
    entity.setRequestType(request.getRequestType());
    entity.setStatus(status);
    entity.setRequesterEmail(request.getRequesterEmail());
    entity.setCreatedBy(context.getUserId());
    
    requestRepository.save(entity);
    
    // Create event for outbox
    Map<String, Object> eventPayload = new HashMap<>();
    eventPayload.put("requestId", requestId);
    eventPayload.put("requestType", request.getRequestType());
    eventPayload.put("status", status);
    eventPayload.put("requesterEmail", request.getRequesterEmail());
    
    EventEnvelopeV1 envelope = EventFactory.create(
        "dsar.created",
        "dsar-grievance-service",
        "DSAR",
        requestId,
        eventPayload
    );
    
    outboxWriter.write(envelope);
    
    return new DsarResponse(requestId, status);
  }
}
