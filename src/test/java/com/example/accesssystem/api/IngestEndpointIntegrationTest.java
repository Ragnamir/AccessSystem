package com.example.accesssystem.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class IngestEndpointIntegrationTest {

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

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    private KeyPair testKeyPair;
    private String testPublicKeyPem;
    private KeyPair issuerKeyPair;
    private String issuerPublicKeyPem;
    private String issuerCode = "test-issuer-1";

    private UUID userId;
    private UUID checkpointId;
    private UUID zoneAId;
    private UUID zoneBId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM denials");
        jdbcTemplate.update("DELETE FROM user_state");
        jdbcTemplate.update("DELETE FROM event_nonces");
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM checkpoint_keys WHERE checkpoint_code = ?", "cp-1");
        jdbcTemplate.update("DELETE FROM issuer_keys WHERE issuer_code = ?", issuerCode);
        jdbcTemplate.update("DELETE FROM checkpoints WHERE code = ?", "cp-1");
        jdbcTemplate.update("DELETE FROM users WHERE code = ?", "user-123");
        jdbcTemplate.update("DELETE FROM zones WHERE code IN (?, ?)", "zone-a", "zone-b");
        
        // Create test data IDs
        userId = UUID.randomUUID();
        checkpointId = UUID.randomUUID();
        zoneAId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        
        // Create zones
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
        
        // Create user
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId, "user-123");
        
        // Create checkpoint
        jdbcTemplate.update(
            "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, ?)",
            checkpointId, "cp-1", zoneAId, zoneBId
        );
        
        // Create access rule: user can access zone-b
        jdbcTemplate.update(
            "INSERT INTO access_rules (user_id, to_zone_id) VALUES (?, ?)",
            userId, zoneBId
        );

        // Initialize user state to ensure transition from zone-a is allowed
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Generate RSA key pair for checkpoint
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        testKeyPair = keyGen.generateKeyPair();
        testPublicKeyPem = convertToPEM(testKeyPair.getPublic(), "PUBLIC KEY");
        
        // Insert checkpoint key
        jdbcTemplate.update(
            "INSERT INTO checkpoint_keys (id, checkpoint_code, public_key_pem, key_type) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), "cp-1", testPublicKeyPem, "RSA"
        );
        
        // Generate RSA key pair for issuer
        issuerKeyPair = keyGen.generateKeyPair();
        issuerPublicKeyPem = convertToPEM(issuerKeyPair.getPublic(), "PUBLIC KEY");
        
        // Insert issuer key
        jdbcTemplate.update(
            "INSERT INTO issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), issuerCode, issuerPublicKeyPem, "RSA", "RS256"
        );
    }

    private String url() {
        return "http://localhost:" + port + "/ingest/event";
    }

    @Test
    void ingest_validPayload_returns202() throws Exception {
        // Build canonical payload and sign it
        String checkpointId = "cp-1";
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        // Create valid JWT token
        String userToken = createJWTToken(issuerCode, "user-123", Date.from(Instant.now().plusSeconds(3600)));
        
        String canonical = String.join("|", checkpointId, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(testKeyPair.getPrivate());
        signature.update(canonicalBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
        String eventId = UUID.randomUUID().toString();
        String json = String.format(
            "{" +
            "\"checkpointId\":\"%s\"," +
            "\"eventId\":\"%s\"," +
            "\"timestamp\":\"%s\"," +
            "\"fromZone\":\"%s\"," +
            "\"toZone\":\"%s\"," +
            "\"userToken\":\"%s\"," +
            "\"signature\":\"%s\"" +
            "}",
            checkpointId, eventId, timestamp, fromZone, toZone, userToken, signatureBase64
        );

        ResponseEntity<Map> response = post(json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("accepted");
        assertThat(response.getBody().get("checkpointId")).isEqualTo("cp-1");
        assertThat(response.getBody().get("userId")).isEqualTo("user-123");
    }

    @Test
    void ingest_missingField_returns400() {
        String json = "{" +
            "\"timestamp\":\"2025-01-01T12:00:00Z\"," +
            "\"fromZone\":\"zone-a\"," +
            "\"toZone\":\"zone-b\"," +
            "\"userToken\":\"abc123\"," +
            "\"signature\":\"sig==\"" +
            "}";

        ResponseEntity<String> response = postRaw(json, String.class);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void ingest_badTimestamp_returns400() {
        String json = "{" +
            "\"checkpointId\":\"cp-1\"," +
            "\"timestamp\":\"2025-01-01 12:00:00\"," +
            "\"fromZone\":\"zone-a\"," +
            "\"toZone\":\"zone-b\"," +
            "\"userToken\":\"abc123\"," +
            "\"signature\":\"sig==\"" +
            "}";

        ResponseEntity<String> response = postRaw(json, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<Map> post(String json) {
        return postRaw(json, Map.class);
    }

    private <T> ResponseEntity<T> postRaw(String json, Class<T> responseType) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        return restTemplate.postForEntity(url(), entity, responseType);
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
    
    private String createJWTToken(String issuer, String userId, Date expiration) {
        return Jwts.builder()
            .issuer(issuer)
            .subject(userId)
            .claim("userId", userId)
            .expiration(expiration)
            .signWith(issuerKeyPair.getPrivate())
            .compact();
    }
}


