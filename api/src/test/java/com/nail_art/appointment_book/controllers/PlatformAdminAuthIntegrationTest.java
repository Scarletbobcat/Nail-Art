package com.nail_art.appointment_book.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nail_art.appointment_book.PostgresIntegrationTest;
import com.nail_art.appointment_book.multitenancy.TenantContext;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport;
import com.nail_art.appointment_book.support.PostgresIdentityTestSupport.SeededIdentity;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The org-less platform-admin authentication path. A platform admin is a User-level
 * flag (no org membership, no role); login mints a token with no org claim, the auth
 * filter accepts it without a membership check, and the owner/staff path stays intact.
 */
class PlatformAdminAuthIntegrationTest extends PostgresIntegrationTest {
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
    private UUID adminId;
    private SeededIdentity owner;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        identitySupport = new PostgresIdentityTestSupport(jdbcTemplate, passwordEncoder, objectMapper, secretKey);
        identitySupport.resetIdentityTables();
        owner = identitySupport.seedIdentity("owner", "owner");
        adminId = identitySupport.seedPlatformAdmin("operator");
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}"
                                .formatted(username, PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    @Test
    void adminLogin_mintsOrglessToken() throws Exception {
        JsonNode claims = identitySupport.claimsOf(login("operator"));

        assertEquals(adminId.toString(), claims.get("sub").asText());
        assertTrue(claims.get("admin").asBoolean(), "admin claim must be true");
        assertFalse(claims.has("org"), "admin token must carry no org claim");
        assertFalse(claims.has("role"), "admin token must carry no role claim");
    }

    @Test
    void adminToken_reachesMe_withOrglessIdentity() throws Exception {
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.adminBearer(adminId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.isPlatformAdmin").value(true))
                .andExpect(jsonPath("$.user.id").value(adminId.toString()))
                .andExpect(jsonPath("$.organization").doesNotExist());
    }

    @Test
    void ownerLogin_andMe_areUnchanged() throws Exception {
        JsonNode claims = identitySupport.claimsOf(login("owner"));
        assertEquals("owner", claims.get("role").asText());
        assertNotNull(claims.get("org"));

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.isPlatformAdmin").value(false))
                .andExpect(jsonPath("$.organization.id").value(owner.organizationId().toString()));
    }

    @Test
    void adminRefresh_returnsAdminToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"%s\"}"
                                .formatted(PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie, "login must set a refresh cookie");

        MvcResult refreshResult = mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isOk())
                .andReturn();

        String refreshed = objectMapper.readTree(refreshResult.getResponse().getContentAsString())
                .get("token").asText();
        JsonNode claims = identitySupport.claimsOf(refreshed);
        assertTrue(claims.get("admin").asBoolean());
        assertFalse(claims.has("org"));
    }

    @Test
    void revokedAdmin_isRejectedDespiteValidToken() throws Exception {
        String token = identitySupport.adminBearer(adminId);
        // Access works while the flag is set.
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk());

        // Revoke the flag; the still-valid token must now be rejected (live re-check).
        jdbcTemplate.update("update users set is_platform_admin = false where id = ?", adminId);

        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedAdmin_cannotRefreshToANewAdminToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"operator\",\"password\":\"%s\"}"
                                .formatted(PostgresIdentityTestSupport.TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refreshToken");
        assertNotNull(refreshCookie);

        jdbcTemplate.update("update users set is_platform_admin = false where id = ?", adminId);

        mockMvc.perform(post("/auth/refresh").cookie(refreshCookie))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithSubjectOnly_isRejected() throws Exception {
        // A non-admin token missing org/role must be rejected cleanly (no NPE -> 401),
        // proving the admin branch didn't loosen the org-scoped path.
        mockMvc.perform(get("/users/me").header(HttpHeaders.AUTHORIZATION, identitySupport.bearerSubjectOnly(adminId)))
                .andExpect(status().isUnauthorized());
    }
}
