package io.regulyn.identity.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.regulyn.identity.constants.TenantStatuses;
import io.regulyn.identity.dto.TenantUsageIncrementRequest;
import io.regulyn.identity.dto.TenantUsageIncrementResponse;
import io.regulyn.identity.entity.IdempotencyKeyEntity;
import io.regulyn.identity.entity.IdempotencyKeyId;
import io.regulyn.identity.entity.Tenant;
import io.regulyn.identity.entity.TenantMonthlyUsage;
import io.regulyn.identity.repository.IdempotencyKeyRepository;
import io.regulyn.identity.repository.TenantMonthlyUsageRepository;
import io.regulyn.identity.repository.TenantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class TenantUsageService {

    private static final String SCOPE_USAGE_INCREMENT = "POST:/internal/tenants/{tenantId}/usage/increment";
    private static final int IDEMPOTENCY_TTL_DAYS = 7;

    private final TenantRepository tenantRepository;
    private final TenantMonthlyUsageRepository monthlyUsageRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final TenantReadOnlyEvaluator tenantReadOnlyEvaluator;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public TenantUsageService(TenantRepository tenantRepository,
                              TenantMonthlyUsageRepository monthlyUsageRepository,
                              IdempotencyKeyRepository idempotencyKeyRepository,
                              TenantReadOnlyEvaluator tenantReadOnlyEvaluator,
                              ObjectMapper objectMapper,
                              PlatformTransactionManager transactionManager) {
        this.tenantRepository = tenantRepository;
        this.monthlyUsageRepository = monthlyUsageRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.tenantReadOnlyEvaluator = tenantReadOnlyEvaluator;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public TenantUsageIncrementResponse incrementUsage(UUID tenantId,
                                                       TenantUsageIncrementRequest request,
                                                       String idempotencyKey) {
        if (tenantId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "TENANT_ID_REQUIRED");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IDEMPOTENCY_KEY_REQUIRED");
        }

        int dsarIncrement = request != null && request.getDsarIncrement() != null ? request.getDsarIncrement() : 0;
        int exportIncrement = request != null && request.getExportIncrement() != null ? request.getExportIncrement() : 0;
        if (dsarIncrement < 0 || exportIncrement < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USAGE_INCREMENT_INVALID");
        }
        if (dsarIncrement == 0 && exportIncrement == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "USAGE_INCREMENT_EMPTY");
        }

        String requestHash = request != null && request.getRequestHash() != null
                ? request.getRequestHash()
                : computeRequestHash(tenantId, dsarIncrement, exportIncrement, currentYearMonth());

        TenantUsageIncrementResponse response = transactionTemplate.execute(status -> {
            IdempotencyKeyEntity existingKey = idempotencyKeyRepository.findForUpdate(tenantId, SCOPE_USAGE_INCREMENT, idempotencyKey)
                    .orElse(null);
            if (existingKey != null && isIdempotencyExpired(existingKey)) {
                idempotencyKeyRepository.delete(existingKey);
                existingKey = null;
            }
            if (existingKey != null) {
                if (!requestHash.equals(existingKey.getRequestHash())) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED_DIFFERENT_REQUEST");
                }
                return responseFromIdempotency(existingKey);
            }

            Tenant tenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));
            if (!TenantStatuses.ACTIVE.equals(tenant.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_ACTIVE");
            }

            int yearMonth = currentYearMonth();
            TenantMonthlyUsage usage = monthlyUsageRepository.findByTenantIdAndYearMonth(tenantId, yearMonth)
                    .orElse(null);
            if (usage == null) {
                usage = new TenantMonthlyUsage();
                usage.setUsageId(UUID.randomUUID());
                usage.setTenantId(tenantId);
                usage.setYearMonth(yearMonth);
                usage.setDsarCount(0);
                usage.setExportCount(0);
            }

            usage.setDsarCount(usage.getDsarCount() + dsarIncrement);
            usage.setExportCount(usage.getExportCount() + exportIncrement);
            monthlyUsageRepository.save(usage);

            tenantReadOnlyEvaluator.evaluateAndUpdate(tenantId, "USAGE_INCREMENT");

            Tenant refreshedTenant = tenantRepository.findById(tenantId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "TENANT_NOT_FOUND"));

            TenantUsageIncrementResponse result = new TenantUsageIncrementResponse();
            result.setYearMonth(yearMonth);
            result.setDsarCount(usage.getDsarCount());
            result.setExportCount(usage.getExportCount());
            result.setReadOnly(refreshedTenant.getReadOnly());
            result.setReadOnlyReason(refreshedTenant.getReadOnlyReason());

            IdempotencyKeyEntity entity = new IdempotencyKeyEntity();
            entity.setId(new IdempotencyKeyId(tenantId, SCOPE_USAGE_INCREMENT, idempotencyKey));
            entity.setRequestHash(requestHash);
            entity.setResponseJson(serializeResponse(result));
            entity.setExpiresAt(Instant.now().plus(IDEMPOTENCY_TTL_DAYS, ChronoUnit.DAYS));
            idempotencyKeyRepository.save(entity);

            return result;
        });

        if (response == null) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "USAGE_INCREMENT_FAILED");
        }
        return response;
    }

    private int currentYearMonth() {
        YearMonth ym = YearMonth.now(ZoneOffset.UTC);
        return ym.getYear() * 100 + ym.getMonthValue();
    }

    private boolean isIdempotencyExpired(IdempotencyKeyEntity entity) {
        return entity.getExpiresAt() != null && entity.getExpiresAt().isBefore(Instant.now());
    }

    private TenantUsageIncrementResponse responseFromIdempotency(IdempotencyKeyEntity entity) {
        try {
            return objectMapper.readValue(entity.getResponseJson(), TenantUsageIncrementResponse.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "IDEMPOTENCY_RESPONSE_UNAVAILABLE", e);
        }
    }

    private String serializeResponse(TenantUsageIncrementResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "USAGE_INCREMENT_SERIALIZATION_FAILED", e);
        }
    }

    private String computeRequestHash(UUID tenantId, int dsarIncrement, int exportIncrement, int yearMonth) {
        return sha256(tenantId + "|" + yearMonth + "|" + dsarIncrement + "|" + exportIncrement);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}