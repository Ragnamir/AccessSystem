package com.example.accesssystem.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class IssuerTokenVerificationServiceTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IssuerTokenVerificationService tokenVerificationService;

    private KeyPair issuerKeyPair;
    private String issuerPublicKeyPem;
    private String issuerCode = "issuer-1";

    @BeforeEach
    void setUp() throws Exception {
        // Clean up and set up issuer key
        jdbcTemplate.update("DELETE FROM issuer_keys WHERE issuer_code = ?", issuerCode);
        
        // Generate RSA key pair for issuer
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        issuerKeyPair = keyGen.generateKeyPair();
        issuerPublicKeyPem = convertToPEM(issuerKeyPair.getPublic(), "PUBLIC KEY");
        
        // Insert issuer key
        jdbcTemplate.update(
            "INSERT INTO issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), issuerCode, issuerPublicKeyPem, "RSA", "RS256"
        );
    }

    @Test
    void verifyToken_validToken_returnsValid() {
        // Create a valid JWT token
        String token = createJWTToken(issuerKeyPair, issuerCode, "user-123", 
            Date.from(Instant.now().plusSeconds(3600))); // expires in 1 hour
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isTrue();
        assertThat(result.getUserId()).isPresent();
        assertThat(result.getUserId().get().value()).isEqualTo("user-123");
        assertThat(result.getIssuerId()).isPresent();
        assertThat(result.getIssuerId().get().value()).isEqualTo(issuerCode);
    }

    @Test
    void verifyToken_expiredToken_returnsExpired() {
        // Create an expired JWT token
        String token = createJWTToken(issuerKeyPair, issuerCode, "user-123", 
            Date.from(Instant.now().minusSeconds(3600))); // expired 1 hour ago
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("expired");
    }

    @Test
    void verifyToken_invalidSignature_returnsInvalid() throws Exception {
        // Create a token with a different key (invalid signature)
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair wrongKeyPair = keyGen.generateKeyPair();
        
        String token = createJWTToken(wrongKeyPair, issuerCode, "user-123", 
            Date.from(Instant.now().plusSeconds(3600)));
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("verification failed");
    }

    @Test
    void verifyToken_missingIssuer_returnsInvalid() {
        // Create a token without issuer claim
        String token = Jwts.builder()
            .subject("user-123")
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(issuerKeyPair.getPrivate())
            .compact();
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("iss");
    }

    @Test
    void verifyToken_unknownIssuer_returnsInvalid() {
        // Create a token with unknown issuer
        String token = createJWTToken(issuerKeyPair, "unknown-issuer", "user-123", 
            Date.from(Instant.now().plusSeconds(3600)));
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("Issuer key not found");
    }

    @Test
    void verifyToken_missingUserId_returnsInvalid() {
        // Create a token without userId/sub claim
        String token = Jwts.builder()
            .issuer(issuerCode)
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(issuerKeyPair.getPrivate())
            .compact();
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isFalse();
        assertThat(result.getReason()).contains("user identifier");
    }

    @Test
    void verifyToken_withAttributes_extractsAttributes() {
        // Create a token with custom attributes
        String token = Jwts.builder()
            .issuer(issuerCode)
            .subject("user-123")
            .claim("department", "IT")
            .claim("role", "admin")
            .expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(issuerKeyPair.getPrivate())
            .compact();
        
        IssuerTokenVerificationService.TokenVerificationResult result = 
            tokenVerificationService.verifyAndDecodeToken(token);
        
        assertThat(result.isValid()).isTrue();
        Map<String, Object> attributes = result.getAttributes();
        assertThat(attributes).containsEntry("department", "IT");
        assertThat(attributes).containsEntry("role", "admin");
    }

    private String createJWTToken(KeyPair keyPair, String issuer, String userId, Date expiration) {
        return Jwts.builder()
            .issuer(issuer)
            .subject(userId)
            .expiration(expiration)
            .signWith(keyPair.getPrivate())
            .compact();
    }

    private String convertToPEM(PublicKey publicKey, String type) {
        byte[] encoded = publicKey.getEncoded();
        String base64 = Base64.getEncoder().encodeToString(encoded);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN ").append(type).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            int end = Math.min(i + 64, base64.length());
            pem.append(base64, i, end).append("\n");
        }
        pem.append("-----END ").append(type).append("-----");
        return pem.toString();
    }
}

