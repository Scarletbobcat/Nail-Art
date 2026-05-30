package com.nail_art.appointment_book.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.Key;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PostgresIdentityTestSupport {
    public static final String TEST_PASSWORD = "secret-pass";
    public static final String TEST_BUSINESS_PHONE = "+15555550101";
    public static final String TEST_TIMEZONE = "America/New_York";
    public static final String TEST_ORGANIZATION_NAME = "Nail Art Spa";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final String secretKey;

    public PostgresIdentityTestSupport(
            JdbcTemplate jdbcTemplate,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper,
            String secretKey
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
        this.secretKey = secretKey;
    }

    public void resetIdentityTables() {
        jdbcTemplate.update("delete from refresh_tokens");
        jdbcTemplate.update("delete from organization_users");
        jdbcTemplate.update("delete from users");
        jdbcTemplate.update("delete from organizations");
    }

    public SeededIdentity seedIdentity(String username, String role) {
        UUID organizationId = createOrganization(TEST_ORGANIZATION_NAME);
        UUID userId = seedUserInOrganization(organizationId, username, role);
        return new SeededIdentity(organizationId, userId, username, role);
    }

    public UUID createOrganization(String name) {
        UUID organizationId = jdbcTemplate.queryForObject(
                "insert into organizations (name, business_phone, timezone) values (?, ?, ?) returning id",
                UUID.class,
                name,
                TEST_BUSINESS_PHONE,
                TEST_TIMEZONE
        );
        assertNotNull(organizationId);
        return organizationId;
    }

    public UUID seedUserInOrganization(UUID organizationId, String username, String role) {
        UUID userId = jdbcTemplate.queryForObject(
                "insert into users (username, email, password_hash) values (?, ?, ?) returning id",
                UUID.class,
                username,
                username.toLowerCase(Locale.ROOT) + "@example.com",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        jdbcTemplate.update(
                "insert into organization_users (organization_id, user_id, role) values (?, ?, ?)",
                organizationId,
                userId,
                role
        );
        assertNotNull(userId);
        return userId;
    }

    /**
     * Seed an org-less platform admin: a user with is_platform_admin=true and NO
     * organization_users membership. Returns the user id.
     */
    public UUID seedPlatformAdmin(String username) {
        UUID userId = jdbcTemplate.queryForObject(
                "insert into users (username, email, password_hash, is_platform_admin) values (?, ?, ?, true) returning id",
                UUID.class,
                username,
                username.toLowerCase(Locale.ROOT) + "@example.com",
                passwordEncoder.encode(TEST_PASSWORD)
        );
        assertNotNull(userId);
        return userId;
    }

    /** Bearer for an org-less platform admin: sub=userId, admin=true, no org/role. */
    public String adminBearer(UUID userId) {
        return "Bearer " + signedAdminToken(userId, 3_600_000L);
    }

    /** Lenient JWT payload decode — does not assert org/role (admin tokens carry neither). */
    public JsonNode claimsOf(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        return objectMapper.readTree(new String(Base64.getUrlDecoder().decode(parts[1])));
    }

    /** Bearer carrying only a subject — no org, no role, no admin claim. Should be rejected. */
    public String bearerSubjectOnly(UUID userId) {
        return "Bearer " + Jwts.builder()
                .setSubject(userId.toString())
                .claim("jti", UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 3_600_000L))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String signedAdminToken(UUID userId, long expirationMillis) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("admin", true)
                .claim("jti", UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public String persistRefreshToken(UUID userId, UUID organizationId, String role) {
        String token = signedToken(userId, organizationId, role, 2_592_000_000L);
        jdbcTemplate.update(
                "insert into refresh_tokens (user_id, token, expires_at) values (?, ?, ?)",
                userId,
                token,
                OffsetDateTime.now().plusDays(30)
        );
        return token;
    }

    public String bearer(SeededIdentity identity) {
        return "Bearer " + signedToken(identity.userId(), identity.organizationId(), identity.role(), 3_600_000L);
    }

    public String signedToken(UUID userId, UUID organizationId, String role) {
        return signedToken(userId, organizationId, role, 2_592_000_000L);
    }

    public JsonNode decodeJwtPayload(String jwt) throws Exception {
        String[] parts = jwt.split("\\.");
        assertEquals(3, parts.length);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        JsonNode claims = objectMapper.readTree(payload);
        assertTrue(claims.hasNonNull("sub"));
        assertTrue(claims.hasNonNull("org"));
        assertTrue(claims.hasNonNull("role"));
        assertNotEquals("", claims.get("sub").asText());
        return claims;
    }

    public Integer countMemberships(String username, UUID organizationId) {
        return jdbcTemplate.queryForObject(
                """
                        select count(*)
                        from users u
                        join organization_users ou on ou.user_id = u.id
                        where u.username = ? and ou.organization_id = ?
                        """,
                Integer.class,
                username,
                organizationId
        );
    }

    public UUID currentOrganizationIdFor(String username) {
        return jdbcTemplate.queryForObject(
                """
                        select ou.organization_id
                        from users u
                        join organization_users ou on ou.user_id = u.id
                        where u.username = ?
                        """,
                UUID.class,
                username
        );
    }

    private String signedToken(UUID userId, UUID organizationId, String role, long expirationMillis) {
        return Jwts.builder()
                .setSubject(userId.toString())
                .claim("org", organizationId.toString())
                .claim("role", role)
                .claim("jti", UUID.randomUUID().toString())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMillis))
                .signWith(signingKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private Key signingKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretKey));
    }

    public record SeededIdentity(UUID organizationId, UUID userId, String username, String role) {
    }
}
