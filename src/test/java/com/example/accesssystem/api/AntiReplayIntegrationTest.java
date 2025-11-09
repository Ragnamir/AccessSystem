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
class AntiReplayIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:13-alpine");

    @DynamicPropertySource
    static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Set shorter skew for testing
        registry.add("access-system.anti-replay.timestamp-skew-seconds", () -> "60");
        registry.add("access-system.anti-replay.event-nonce-ttl-seconds", () -> "3600");
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

        // Initialize user state to zone-a to satisfy state checks for transitions
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
    void ingest_expiredTimestamp_returns403() throws Exception {
        // Create timestamp 2 minutes in the past (outside 60-second skew window)
        String expiredTimestamp = Instant.now().minusSeconds(120).toString();
        
        String eventId = UUID.randomUUID().toString();
        String checkpointId = "cp-1";
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, "user-123", Date.from(Instant.now().plusSeconds(3600)));
        
        String canonical = String.join("|", checkpointId, expiredTimestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(testKeyPair.getPrivate());
        signature.update(canonicalBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
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
            checkpointId, eventId, expiredTimestamp, fromZone, toZone, userToken, signatureBase64
        );

        ResponseEntity<Map> response = post(json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("timestamp_out_of_window");
    }

    @Test
    void ingest_futureTimestamp_returns403() throws Exception {
        // Create timestamp 2 minutes in the future (outside 60-second skew window)
        String futureTimestamp = Instant.now().plusSeconds(120).toString();
        
        String eventId = UUID.randomUUID().toString();
        String checkpointId = "cp-1";
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, "user-123", Date.from(Instant.now().plusSeconds(3600)));
        
        String canonical = String.join("|", checkpointId, futureTimestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(testKeyPair.getPrivate());
        signature.update(canonicalBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
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
            checkpointId, eventId, futureTimestamp, fromZone, toZone, userToken, signatureBase64
        );

        ResponseEntity<Map> response = post(json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("timestamp_out_of_window");
    }

    @Test
    void ingest_duplicateEventId_returns403() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String checkpointId = "cp-1";
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, "user-123", Date.from(Instant.now().plusSeconds(3600)));
        
        String canonical = String.join("|", checkpointId, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(testKeyPair.getPrivate());
        signature.update(canonicalBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
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

        // First request should succeed
        ResponseEntity<Map> firstResponse = post(json);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(firstResponse.getBody()).isNotNull();
        assertThat(firstResponse.getBody().get("status")).isEqualTo("accepted");

        // Second request with same eventId should be rejected
        ResponseEntity<Map> secondResponse = post(json);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(secondResponse.getBody()).isNotNull();
        assertThat(secondResponse.getBody().get("status")).isEqualTo("rejected");
        assertThat(secondResponse.getBody().get("reason")).isEqualTo("duplicate_event_id");
    }

    @Test
    void ingest_validTimestampWithinSkew_returns202() throws Exception {
        // Create timestamp 30 seconds in the past (within 60-second skew window)
        String validTimestamp = Instant.now().minusSeconds(30).toString();
        
        String eventId = UUID.randomUUID().toString();
        String checkpointId = "cp-1";
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, "user-123", Date.from(Instant.now().plusSeconds(3600)));
        
        String canonical = String.join("|", checkpointId, validTimestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(testKeyPair.getPrivate());
        signature.update(canonicalBytes);
        byte[] signatureBytes = signature.sign();
        String signatureBase64 = Base64.getEncoder().encodeToString(signatureBytes);
        
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
            checkpointId, eventId, validTimestamp, fromZone, toZone, userToken, signatureBase64
        );

        ResponseEntity<Map> response = post(json);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("accepted");
        assertThat(response.getBody().get("checkpointId")).isEqualTo("cp-1");
    }

    private ResponseEntity<Map> post(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        return restTemplate.postForEntity(url(), entity, Map.class);
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

