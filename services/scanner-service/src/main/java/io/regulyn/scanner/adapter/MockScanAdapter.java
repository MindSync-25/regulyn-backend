package io.regulyn.scanner.adapter;

import io.regulyn.scanner.adapter.model.Finding;
import io.regulyn.scanner.model.ScanSource;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component("MOCK")
public class MockScanAdapter implements ScanAdapter {

    @Override
    public List<Finding> runInventory(ScanSource source, Instant since) {
        List<Finding> findings = new ArrayList<>();

        // Entity findings - customer_profile
        Finding entityCustomer = new Finding();
        entityCustomer.setFindingType("ENTITY");
        entityCustomer.setEntityType("customer_profile");
        entityCustomer.setRiskLevel("MED");
        entityCustomer.setConfidence(85);
        entityCustomer.setDetails(Map.of("recordCount", 1000));
        findings.add(entityCustomer);

        // Entity findings - orders
        Finding entityOrders = new Finding();
        entityOrders.setFindingType("ENTITY");
        entityOrders.setEntityType("orders");
        entityOrders.setRiskLevel("LOW");
        entityOrders.setConfidence(90);
        entityOrders.setDetails(Map.of("recordCount", 5000));
        findings.add(entityOrders);

        // Field findings - PII
        Finding fieldEmail = new Finding();
        fieldEmail.setFindingType("FIELD");
        fieldEmail.setEntityType("customer_profile");
        fieldEmail.setFieldName("email");
        fieldEmail.setDataCategory("PII");
        fieldEmail.setRiskLevel("HIGH");
        fieldEmail.setConfidence(95);
        fieldEmail.setDetails(Map.of("nullable", false));
        findings.add(fieldEmail);

        // Field findings - FINANCIAL
        Finding fieldCreditCard = new Finding();
        fieldCreditCard.setFindingType("FIELD");
        fieldCreditCard.setEntityType("orders");
        fieldCreditCard.setFieldName("payment_method");
        fieldCreditCard.setDataCategory("FINANCIAL");
        fieldCreditCard.setRiskLevel("HIGH");
        fieldCreditCard.setConfidence(90);
        fieldCreditCard.setDetails(Map.of("encrypted", true));
        findings.add(fieldCreditCard);

        return findings;
    }

    @Override
    public List<Finding> runRetentionCandidates(ScanSource source, Instant since) {
        List<Finding> findings = new ArrayList<>();

        // Generate deterministic UUIDs for retention candidates
        UUID[] candidateIds = {
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"),
            UUID.fromString("00000000-0000-0000-0000-000000000004"),
            UUID.fromString("00000000-0000-0000-0000-000000000005")
        };

        String[] riskLevels = {"HIGH", "MED", "MED", "LOW", "MED"};
        int[] confidences = {95, 85, 80, 70, 90};

        for (int i = 0; i < candidateIds.length; i++) {
            Finding candidate = new Finding();
            candidate.setFindingType("RETENTION_CANDIDATE");
            candidate.setEntityType("customer_profile");
            candidate.setSubjectId(candidateIds[i]);
            candidate.setRiskLevel(riskLevels[i]);
            candidate.setConfidence(confidences[i]);
            candidate.setDetails(Map.of("reason", "inactive_account", "lastActivity", "2024-01-01"));
            findings.add(candidate);
        }

        return findings;
    }
}
