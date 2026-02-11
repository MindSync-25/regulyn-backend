package com.regulyn.nominee;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class NomineeServiceIntegrationTest extends BaseIntegrationTest {

    @Test
    public void test01_RegisterNominee_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();

        String requestJson = String.format("""
            {
              "dataPrincipalId": "%s",
              "nomineeName": "John Doe",
              "nomineeEmail": "john@example.com",
              "nomineePhone": "+1234567890",
              "relationship": "FAMILY",
              "scope": "FULL_RIGHTS"
            }
            """, principalId);

        mockMvc.perform(post("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestJson))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.nomineeId").exists())
            .andExpect(jsonPath("$.nomineeName").value("John Doe"))
            .andExpect(jsonPath("$.nomineeEmail").value("john@example.com"))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    public void test02_VerifyNominee_Success() throws Exception {
        // First register
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();

        String registerJson = String.format("""
            {
              "dataPrincipalId": "%s",
              "nomineeName": "Jane Doe",
              "nomineeEmail": "jane@example.com",
              "nomineePhone": "+1234567890",
              "relationship": "LEGAL_REP",
              "scope": "LIMITED"
            }
            """, principalId);

        String registerResponse = mockMvc.perform(post("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String nomineeId = objectMapper.readTree(registerResponse).get("nomineeId").asText();

        // Now verify
        String verifyJson = """
            {
              "method": "EMAIL_OTP"
            }
            """;

        mockMvc.perform(post("/nominees/" + nomineeId + "/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(verifyJson))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomineeId").value(nomineeId))
            .andExpect(jsonPath("$.status").value("VERIFIED"))
            .andExpect(jsonPath("$.verifiedAt").exists());
    }

    @Test
    public void test03_DisableNominee_Success() throws Exception {
        // Register and verify
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();

        String registerJson = String.format("""
            {
              "dataPrincipalId": "%s",
              "nomineeName": "Bob Smith",
              "nomineeEmail": "bob@example.com",
              "nomineePhone": "+1234567890",
              "relationship": "LEGAL_REP",
              "scope": "FULL_RIGHTS"
            }
            """, principalId);

        String registerResponse = mockMvc.perform(post("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String nomineeId = objectMapper.readTree(registerResponse).get("nomineeId").asText();

        // Verify first
        mockMvc.perform(post("/nominees/" + nomineeId + "/verify")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"method\": \"EMAIL_OTP\"}"))
            .andExpect(status().isOk());

        // Now disable
        UUID adminId = UUID.randomUUID();
        mockMvc.perform(delete("/nominees/" + nomineeId)
                .header("X-User-ID", adminId.toString()))
            .andExpect(status().isNoContent());

        // Verify it's disabled
        mockMvc.perform(get("/nominees/" + nomineeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DISABLED"));
    }

    @Test
    public void test04_GetNominee_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();

        String registerJson = String.format("""
            {
              "dataPrincipalId": "%s",
              "nomineeName": "Alice Johnson",
              "nomineeEmail": "alice@example.com",
              "nomineePhone": "+1234567890",
              "relationship": "FAMILY",
              "scope": "DSAR_ONLY"
            }
            """, principalId);

        String registerResponse = mockMvc.perform(post("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        String nomineeId = objectMapper.readTree(registerResponse).get("nomineeId").asText();

        mockMvc.perform(get("/nominees/" + nomineeId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nomineeId").value(nomineeId))
            .andExpect(jsonPath("$.nomineeName").value("Alice Johnson"))
            .andExpect(jsonPath("$.nomineeEmail").value("alice@example.com"));
    }

    @Test
    public void test05_GetNomineesByPrincipal_Success() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID principalId = UUID.randomUUID();

        // Register 3 nominees
        for (int i = 1; i <= 3; i++) {
            String registerJson = String.format("""
                {
                  "dataPrincipalId": "%s",
                  "nomineeName": "Nominee %d",
                  "nomineeEmail": "nominee%d@example.com",
                  "nomineePhone": "+1234567890",
                  "relationship": "FAMILY",
                  "scope": "FULL_RIGHTS"
                }
                """, principalId, i, i);

            mockMvc.perform(post("/nominees")
                    .header("X-Tenant-ID", tenantId.toString())
                    .header("X-User-ID", principalId.toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(registerJson))
                .andExpect(status().isCreated());
        }

        mockMvc.perform(get("/nominees")
                .header("X-Tenant-ID", tenantId.toString())
                .header("X-User-ID", principalId.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(3))
            .andExpect(jsonPath("$[0].nomineeName").value("Nominee 1"))
            .andExpect(jsonPath("$[1].nomineeName").value("Nominee 2"))
            .andExpect(jsonPath("$[2].nomineeName").value("Nominee 3"));
    }
}
