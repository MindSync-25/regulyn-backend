package com.regulyn.nominee;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class ClaimServiceIntegrationTest extends BaseIntegrationTest {

    private String createNominee(UUID tenantId, UUID principalId) throws Exception {
        String registerJson = String.format("""
            {
              "dataPrincipalId": "%s",
              "nomineeName": "Test Nominee",
              "nomineeEmail": "test@example.com",
              "nomineePhone": "+1234567890",
              "relationship": "FAMILY",
              "scope": "FULL_RIGHTS"
            }
            """, principalId);

        String response = mockMvc.perform(post("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("nomineeId").asText();
    }

    @Test
    public void test06_CreateClaim_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        String nomineeId = createNominee(tenantId, principalId);

        String claimJson = String.format("""
            {
              "nomineeId": "%s",
              "dataPrincipalId": "%s",
              "claimType": "ACCOUNT_ACCESS",
              "reason": "Need access to deceased account",
              "documentRefs": [
                {
                  "docType": "ID_PROOF",
                  "docRef": "s3://bucket/id-proof.pdf"
                },
                {
                  "docType": "DEATH_CERT",
                  "docRef": "s3://bucket/death-cert.pdf"
                }
              ]
            }
            """, nomineeId, principalId);

        mockMvc.perform(post("/claims")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(claimJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.claimId").exists())
            .andExpect(jsonPath("$.nomineeId").value(nomineeId))
            .andExpect(jsonPath("$.claimType").value("ACCOUNT_ACCESS"))
            .andExpect(jsonPath("$.status").value("SUBMITTED"))
            .andExpect(jsonPath("$.documentRefs").isArray())
            .andExpect(jsonPath("$.documentRefs.length()").value(2))
            .andExpect(jsonPath("$.documentRefs[0].docType").value("ID_PROOF"))
            .andExpect(jsonPath("$.documentRefs[1].docType").value("DEATH_CERT"));
    }

    @Test
    public void test07_TransitionClaim_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        String nomineeId = createNominee(tenantId, principalId);

        // Create claim
        String claimJson = String.format("""
            {
              "nomineeId": "%s",
              "dataPrincipalId": "%s",
              "claimType": "DSAR_SUBMISSION",
              "reason": "Need to submit DSAR",
              "documentRefs": [
                {
                  "docType": "ID_PROOF",
                  "docRef": "s3://bucket/id.pdf"
                }
              ]
            }
            """, nomineeId, principalId);

        String claimResponse = mockMvc.perform(post("/claims")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(claimJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String claimId = objectMapper.readTree(claimResponse).get("claimId").asText();

        // Transition to IN_REVIEW
        String transitionJson = """
            {
              "toStatus": "IN_REVIEW"
            }
            """;

        UUID adminId = UUID.randomUUID();
        mockMvc.perform(post("/claims/" + claimId + "/transition")
                .header("X-User-ID", adminId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(transitionJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimId").value(claimId))
            .andExpect(jsonPath("$.status").value("IN_REVIEW"));
    }

    @Test
    public void test08_ApproveClaim_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        String nomineeId = createNominee(tenantId, principalId);

        // Create claim
        String claimJson = String.format("""
            {
              "nomineeId": "%s",
              "dataPrincipalId": "%s",
              "claimType": "RIGHTS_TRANSFER",
              "reason": "Transfer rights to nominee",
              "documentRefs": [
                {
                  "docType": "COURT_ORDER",
                  "docRef": "s3://bucket/court-order.pdf"
                }
              ]
            }
            """, nomineeId, principalId);

        String claimResponse = mockMvc.perform(post("/claims")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(claimJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String claimId = objectMapper.readTree(claimResponse).get("claimId").asText();

        // Transition to IN_REVIEW first
        mockMvc.perform(post("/claims/" + claimId + "/transition")
                .header("X-User-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\": \"IN_REVIEW\"}"))
            .andExpect(status().isOk());

        // Now approve
        String approveJson = """
            {
              "decision": "APPROVE",
              "notes": "All documents verified"
            }
            """;

        UUID approverId = UUID.randomUUID();
        mockMvc.perform(post("/claims/" + claimId + "/approve")
                .header("X-User-ID", approverId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(approveJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimId").value(claimId))
            .andExpect(jsonPath("$.status").value("APPROVED"))
            .andExpect(jsonPath("$.approvedBy").value(approverId.toString()))
            .andExpect(jsonPath("$.approvedAt").exists());
    }

    @Test
    public void test09_RejectClaim_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        String nomineeId = createNominee(tenantId, principalId);

        // Create claim
        String claimJson = String.format("""
            {
              "nomineeId": "%s",
              "dataPrincipalId": "%s",
              "claimType": "ACCOUNT_ACCESS",
              "reason": "Need account access",
              "documentRefs": [
                {
                  "docType": "ID_PROOF",
                  "docRef": "s3://bucket/id.pdf"
                }
              ]
            }
            """, nomineeId, principalId);

        String claimResponse = mockMvc.perform(post("/claims")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(claimJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String claimId = objectMapper.readTree(claimResponse).get("claimId").asText();

        // Transition to IN_REVIEW
        mockMvc.perform(post("/claims/" + claimId + "/transition")
                .header("X-User-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\": \"IN_REVIEW\"}"))
            .andExpect(status().isOk());

        // Now reject
        String rejectJson = """
            {
              "decision": "REJECT",
              "notes": "Insufficient documentation"
            }
            """;

        UUID rejectorId = UUID.randomUUID();
        mockMvc.perform(post("/claims/" + claimId + "/approve")
                .header("X-User-ID", rejectorId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(rejectJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimId").value(claimId))
            .andExpect(jsonPath("$.status").value("REJECTED"));
    }

    @Test
    public void test10_CloseClaim_WithEvidence_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();
        String nomineeId = createNominee(tenantId, principalId);

        // Create claim
        String claimJson = String.format("""
            {
              "nomineeId": "%s",
              "dataPrincipalId": "%s",
              "claimType": "DSAR_SUBMISSION",
              "reason": "Need DSAR data",
              "documentRefs": [
                {
                  "docType": "ID_PROOF",
                  "docRef": "s3://bucket/id.pdf"
                }
              ]
            }
            """, nomineeId, principalId);

        String claimResponse = mockMvc.perform(post("/claims")
                .header("X-Tenant-ID", tenantId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(claimJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String claimId = objectMapper.readTree(claimResponse).get("claimId").asText();

        // Transition through workflow
        mockMvc.perform(post("/claims/" + claimId + "/transition")
                .header("X-User-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"toStatus\": \"IN_REVIEW\"}"))
            .andExpect(status().isOk());

        mockMvc.perform(post("/claims/" + claimId + "/approve")
                .header("X-User-ID", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\": \"APPROVE\", \"notes\": \"OK\"}"))
            .andExpect(status().isOk());

        // Mock evidence service returns bundleId
        UUID evidenceId1 = UUID.randomUUID();
        UUID evidenceId2 = UUID.randomUUID();

        // Close claim with evidence
        String closeJson = String.format("""
            {
              "includeEvidenceIds": ["%s", "%s"],
              "closureNotes": "Completed successfully"
            }
            """, evidenceId1, evidenceId2);

        UUID closerId = UUID.randomUUID();
        mockMvc.perform(post("/claims/" + claimId + "/close")
                .header("X-User-ID", closerId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(closeJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.claimId").value(claimId))
            .andExpect(jsonPath("$.status").value("CLOSED"))
            .andExpect(jsonPath("$.closedAt").exists())
            .andExpect(jsonPath("$.evidenceBundleId").exists());
    }
}
