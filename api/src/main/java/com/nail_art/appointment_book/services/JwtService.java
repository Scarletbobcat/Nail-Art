package com.nail_art.appointment_book.services;

import com.nail_art.appointment_book.entities.RefreshToken;
import com.nail_art.appointment_book.entities.User;
import com.nail_art.appointment_book.repositories.RefreshTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32;

    @Value("${security.jwt.secret-key}")
    private String secretKey;

    @Value("${security.jwt.expiration-time}")
    private long jwtExpiration;

    private final RefreshTokenRepository refreshTokenRepository;

    public JwtService(RefreshTokenRepository refreshTokenRepository) {
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Value("${JWT_REFRESH_EXPIRATION}")
    private long refreshExpiration;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public UUID extractUserId(String token) {
        return UUID.fromString(extractClaim(token, Claims::getSubject));
    }

    public UUID extractOrganizationId(String token) {
        return UUID.fromString(extractClaim(token, claims -> claims.get("org", String.class)));
    }

    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    /** True only when the token carries the {@code admin=true} claim (a platform-admin token). */
    public boolean extractIsPlatformAdmin(String token) {
        return Boolean.TRUE.equals(extractClaim(token, claims -> claims.get("admin", Boolean.class)));
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(User user, UUID organizationId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("org", organizationId.toString());
        claims.put("role", role);
        return buildToken(claims, user.getId().toString(), jwtExpiration);
    }

    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtExpiration);
    }

    /** Access token for an org-less platform admin: sub=userId, admin=true, no org/role. */
    public String generateAdminToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("admin", true);
        return buildToken(claims, user.getId().toString(), jwtExpiration);
    }

    public long getExpirationTime() {
        return jwtExpiration;
    }

    @Transactional
    public String generateRefreshToken(User user, UUID organizationId) {
        return generateStoredRefreshToken(user.getId(), organizationId);
    }

    /** Refresh token for an org-less platform admin. */
    @Transactional
    public String generateAdminRefreshToken(User user) {
        return generateStoredRefreshToken(user.getId(), null);
    }

    public boolean validateRefreshToken(String token) {
        return findValidRefreshToken(token)
                .isPresent();
    }

    public Optional<RefreshToken> findValidRefreshToken(String token) {
        return refreshTokenRepository.findByTokenHash(hashRefreshToken(token))
                .filter(refreshToken -> refreshToken.getExpiryDate().isAfter(Instant.now()));
    }

    @Transactional
    public void markRefreshTokenUsed(RefreshToken refreshToken) {
        refreshToken.setLastUsedAt(OffsetDateTime.now(ZoneOffset.UTC));
        refreshTokenRepository.save(refreshToken);
    }

    @Transactional
    public void deleteRefreshToken(String token) {
        refreshTokenRepository.findByTokenHash(hashRefreshToken(token)).ifPresent(refreshTokenRepository::delete);
    }

    private String generateStoredRefreshToken(UUID userId, UUID organizationId) {
        String token = generateOpaqueRefreshToken();
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(UUID.randomUUID());
        refreshToken.setUserId(userId);
        refreshToken.setOrganizationId(organizationId);
        refreshToken.setTokenHash(hashRefreshToken(token));
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshExpiration));
        refreshTokenRepository.save(refreshToken);
        return token;
    }

    private String generateOpaqueRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashRefreshToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            UserDetails userDetails,
            long expiration
    ) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(userDetails.getUsername())
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    private String buildToken(
            Map<String, Object> extraClaims,
            String subject,
            long expiration
    ) {
        return Jwts
                .builder()
                .setClaims(extraClaims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSignInKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts
                .parserBuilder()
                .setSigningKey(getSignInKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    private Key getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
