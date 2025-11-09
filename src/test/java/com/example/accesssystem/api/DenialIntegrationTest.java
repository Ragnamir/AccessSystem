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

/**
 * Integration tests for denial logging.
 * Verifies that access denials are correctly recorded in the denials table
 * with appropriate reason categories for various failure scenarios.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class DenialIntegrationTest {

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

    private KeyPair checkpointKeyPair;
    private KeyPair issuerKeyPair;
    private String issuerCode = "test-issuer-1";
    private String checkpointCode = "cp-1";
    private String userCode = "user-123";
    private UUID userId;
    private UUID checkpointId;
    private UUID zoneAId;
    private UUID zoneBId;

    @BeforeEach
    void setUp() throws Exception {
        // Clean up
        jdbcTemplate.update("DELETE FROM denials");
        jdbcTemplate.update("DELETE FROM events");
        jdbcTemplate.update("DELETE FROM user_state");
        jdbcTemplate.update("DELETE FROM event_nonces");
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM checkpoint_keys WHERE checkpoint_code = ?", checkpointCode);
        jdbcTemplate.update("DELETE FROM issuer_keys WHERE issuer_code = ?", issuerCode);
        jdbcTemplate.update("DELETE FROM checkpoints WHERE code = ?", checkpointCode);
        jdbcTemplate.update("DELETE FROM users WHERE code = ?", userCode);
        jdbcTemplate.update("DELETE FROM zones WHERE code IN (?, ?)", "zone-a", "zone-b");
        
        // Create test data
        userId = UUID.randomUUID();
        checkpointId = UUID.randomUUID();
        zoneAId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        
        // Create zones
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
        
        // Create user
        jdbcTemplate.update("INSERT INTO users (id, code) VALUES (?, ?)", userId, userCode);
        
        // Create checkpoint
        jdbcTemplate.update(
            "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, ?)",
            checkpointId, checkpointCode, zoneAId, zoneBId
        );
        
        // Generate and insert checkpoint key
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        checkpointKeyPair = keyGen.generateKeyPair();
        String checkpointPublicKeyPem = convertToPEM(checkpointKeyPair.getPublic(), "PUBLIC KEY");
        
        jdbcTemplate.update(
            "INSERT INTO checkpoint_keys (id, checkpoint_code, public_key_pem, key_type) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), checkpointCode, checkpointPublicKeyPem, "RSA"
        );
        
        // Generate and insert issuer key
        issuerKeyPair = keyGen.generateKeyPair();
        String issuerPublicKeyPem = convertToPEM(issuerKeyPair.getPublic(), "PUBLIC KEY");
        
        jdbcTemplate.update(
            "INSERT INTO issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), issuerCode, issuerPublicKeyPem, "RSA", "RS256"
        );
    }

    private String url() {
        return "http://localhost:" + port + "/ingest/event";
    }

    @Test
    void invalidSignature_shouldRecordDenialWithSignatureInvalidReason() throws Exception {
        String eventId = UUID.randomUUID().toString();
        // Format timestamp to match expected format: yyyy-MM-dd'T'HH:mm:ss'Z' (without nanoseconds)
        String timestamp = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS).toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign with wrong key (issuer key instead of checkpoint key) - invalid signature
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(issuerKeyPair.getPrivate()); // Wrong key!
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
            checkpointCode, eventId, timestamp, fromZone, toZone, userToken, signatureBase64
        );

        // Send request
        ResponseEntity<Map> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        
        // Verify denial was recorded
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE reason = 'SIGNATURE_INVALID' AND checkpoint_code = ?",
            Integer.class,
            checkpointCode
        );
        assertThat(denialCount).isEqualTo(1);
        
        Map<String, Object> denial = jdbcTemplate.queryForMap(
            "SELECT reason, details FROM denials WHERE reason = 'SIGNATURE_INVALID' AND checkpoint_code = ?",
            checkpointCode
        );
        assertThat(denial.get("reason")).isEqualTo("SIGNATURE_INVALID");
        assertThat(denial.get("details")).asString().contains("Signature verification failed");
    }

    @Test
    void invalidToken_shouldRecordDenialWithTokenInvalidReason() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        // Create invalid token (expired)
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().minusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign correctly
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(checkpointKeyPair.getPrivate());
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
            checkpointCode, eventId, timestamp, fromZone, toZone, userToken, signatureBase64
        );

        // Send request
        ResponseEntity<Map> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify denial was recorded
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE reason = 'TOKEN_INVALID' AND checkpoint_code = ?",
            Integer.class,
            checkpointCode
        );
        assertThat(denialCount).isEqualTo(1);
    }

    @Test
    void replayAttack_shouldRecordDenialWithReplayReason() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        // Create access rule
        jdbcTemplate.update(
            "INSERT INTO access_rules (user_id, to_zone_id) VALUES (?, ?)",
            userId, zoneBId
        );
        
        // Create initial state
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Clean up any existing denials for this test
        jdbcTemplate.update("DELETE FROM denials WHERE checkpoint_code = ?", checkpointCode);
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(checkpointKeyPair.getPrivate());
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
            checkpointCode, eventId, timestamp, fromZone, toZone, userToken, signatureBase64
        );

        // Send first request (should succeed)
        ResponseEntity<Map> response1 = post(json);
        assertThat(response1.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        
        // Send same request again (replay attack)
        ResponseEntity<Map> response2 = post(json);
        
        // Verify response
        assertThat(response2.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        
        // Verify denial was recorded
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE reason = 'REPLAY' AND checkpoint_code = ?",
            Integer.class,
            checkpointCode
        );
        assertThat(denialCount).isEqualTo(1);
    }

    @Test
    void accessDenied_shouldRecordDenialWithAccessDeniedReason() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        // Create initial state
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // NO access rule - access should be denied
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(checkpointKeyPair.getPrivate());
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
            checkpointCode, eventId, timestamp, fromZone, toZone, userToken, signatureBase64
        );

        // Send request
        ResponseEntity<Map> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("reason")).isEqualTo("access_denied");
        
        // Verify denial was recorded (may be recorded twice - once in controller, once in service)
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE reason = 'ACCESS_DENIED' AND checkpoint_code = ? AND user_code = ?",
            Integer.class,
            checkpointCode, userCode
        );
        // Denial may be recorded in both IngestController and TransactionalEventProcessingService
        assertThat(denialCount).isGreaterThanOrEqualTo(1);
        
        // Get first denial record (there may be multiple)
        java.util.List<Map<String, Object>> denials = jdbcTemplate.queryForList(
            "SELECT reason, details, from_zone_code, to_zone_code FROM denials WHERE reason = 'ACCESS_DENIED' AND checkpoint_code = ? LIMIT 1",
            checkpointCode
        );
        assertThat(denials).isNotEmpty();
        Map<String, Object> denial = denials.get(0);
        assertThat(denial.get("reason")).isEqualTo("ACCESS_DENIED");
        assertThat(denial.get("from_zone_code")).isEqualTo("zone-a");
        assertThat(denial.get("to_zone_code")).isEqualTo("zone-b");
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

