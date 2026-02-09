package com.regulyn.dsar.api;

import com.regulyn.auth.context.TenantContext;
import com.regulyn.auth.context.TenantContextHolder;
import com.regulyn.dsar.model.AttachmentReferenceRequest;
import com.regulyn.dsar.model.AttachmentResponse;
import com.regulyn.dsar.service.AttachmentService;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/dsar/{dsarId}/attachments")
public class DsarAttachmentController {

    private final AttachmentService attachmentService;

    public DsarAttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AttachmentResponse> uploadAttachment(
        @PathVariable("dsarId") UUID dsarId,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @RequestPart("file") MultipartFile file) {

        TenantContext context = TenantContextHolder.getContext();
        AttachmentService.AttachmentResult result = attachmentService.addUploadAttachment(
            context.getTenantId(), context.getUserId(), dsarId, idempotencyKey, file);

        return ResponseEntity.status(result.idempotent() ? 200 : 201).body(result.response());
    }

    @PostMapping(value = "/reference", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AttachmentResponse> referenceAttachment(
        @PathVariable("dsarId") UUID dsarId,
        @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
        @Valid @RequestBody AttachmentReferenceRequest request) {

        TenantContext context = TenantContextHolder.getContext();
        AttachmentService.AttachmentResult result = attachmentService.addReferenceAttachment(
            context.getTenantId(), context.getUserId(), dsarId, idempotencyKey, request);

        return ResponseEntity.status(result.idempotent() ? 200 : 201).body(result.response());
    }

    @GetMapping
    public List<AttachmentResponse> listAttachments(@PathVariable("dsarId") UUID dsarId) {
        TenantContext context = TenantContextHolder.getContext();
        return attachmentService.listAttachments(context.getTenantId(), dsarId);
    }
}