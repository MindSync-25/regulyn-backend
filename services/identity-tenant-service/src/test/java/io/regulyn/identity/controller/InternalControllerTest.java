package io.regulyn.identity.controller;

import io.regulyn.identity.dto.ValidateApiKeyRequest;
import io.regulyn.identity.dto.ValidateApiKeyResponse;
import io.regulyn.identity.filter.InternalAuthFilter;
import io.regulyn.identity.service.InternalApiKeyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class InternalControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InternalApiKeyService internalApiKeyService;

    @BeforeEach
    void setUp() {
        InternalAuthFilter filter = new InternalAuthFilter("change-me-in-production");

        mockMvc = MockMvcBuilders
                .standaloneSetup(new InternalController(internalApiKeyService))
                .addFilters(filter)
                .build();
    }

    @Test
    void shouldReturn403WhenInternalAuthTokenMissing() throws Exception {
        mockMvc.perform(post("/internal/api-keys/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn403WhenInternalAuthTokenInvalid() throws Exception {
        mockMvc.perform(post("/internal/api-keys/validate")
                .header("X-Internal-Auth", "wrong-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn200WhenApiKeyValid() throws Exception {
        UUID tenantId = UUID.randomUUID();
        ValidateApiKeyResponse response = ValidateApiKeyResponse.valid(
                tenantId, null, List.of("CONNECTOR_AGENT"));

        when(internalApiKeyService.validateApiKey(anyString())).thenReturn(response);

        mockMvc.perform(post("/internal/api-keys/validate")
                .header("X-Internal-Auth", "change-me-in-production")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"test-key\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.tenantId").value(tenantId.toString()));
    }

    @Test
    void shouldReturn401WhenApiKeyInvalid() throws Exception {
        when(internalApiKeyService.validateApiKey(anyString()))
                .thenReturn(ValidateApiKeyResponse.invalid());

        mockMvc.perform(post("/internal/api-keys/validate")
                .header("X-Internal-Auth", "change-me-in-production")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"apiKey\":\"invalid-key\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
