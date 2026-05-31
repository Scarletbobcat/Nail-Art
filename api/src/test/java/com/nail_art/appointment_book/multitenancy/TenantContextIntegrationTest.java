package com.nail_art.appointment_book.multitenancy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport.SeededIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TenantContextIntegrationTest.TenantProbeConfig.class)
class TenantContextIntegrationTest extends PostgresIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    private PostgresIdentityTestSupport identitySupport;

    @BeforeEach
    void setUpIdentity() {
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        identitySupport.resetIdentityTables();
        TenantContext.clear();
    }

    @Test
    void anonymousAuthLogin_withTenantContextUnset_succeeds() throws Exception {
        identitySupport.seedIdentity("owner", "owner");

        assertThat(TenantContext.get())
                .as("TenantContext should be unset before anonymous login")
                .isNull();

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "owner",
                                  "password": "%s"
                                }
                                """.formatted(PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());

        assertThat(TenantContext.get())
                .as("TenantContext should still be unset after anonymous login")
                .isNull();
    }

    @Test
    void authedRequest_withForgedOrgClaim_isRejectedAsBadMembership() throws Exception {
        // org A's real user, but a JWT minted with org B's org claim. The membership cross-check
        // (existsByUserIdAndOrganizationId) must reject it before any tenant-scoped query runs —
        // a forged org claim never opens another tenant's scope.
        SeededIdentity orgA = identitySupport.seedIdentity("owner-a", "owner", "Org A");
        SeededIdentity orgB = identitySupport.seedIdentity("owner-b", "owner", "Org B");

        String forgedToken = "Bearer " + identitySupport.signedToken(
                orgA.userId(), orgB.organizationId(), orgA.role());

        mockMvc.perform(get("/employees/").header(HttpHeaders.AUTHORIZATION, forgedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Invalid organization membership"));
    }

    @Test
    void tenantContextWebFilter_setsContextFromPrincipal() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(get("/test/tenant-context")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value(identity.organizationId().toString()));
    }

    @Test
    void tenantContextWebFilter_clearsAfterRequest() throws Exception {
        SeededIdentity identity = identitySupport.seedIdentity("owner", "owner");

        mockMvc.perform(get("/test/tenant-context")
                        .header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(identity)))
                .andExpect(status().isOk());

        assertThat(TenantContext.get())
                .as("TenantContext should be cleared after request for org %s", identity.organizationId())
                .isNull();
    }

    @TestConfiguration
    static class TenantProbeConfig {
        @RestController
        static class TenantProbeController {
            @GetMapping("/test/tenant-context")
            Map<String, String> currentTenant() {
                UUID organizationId = TenantContext.get();
                return Map.of("organizationId", organizationId == null ? "" : organizationId.toString());
            }
        }
    }
}
