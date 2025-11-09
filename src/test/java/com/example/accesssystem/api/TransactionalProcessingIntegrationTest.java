package com.example.accesssystem.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
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
 * Integration tests for transactional event processing.
 * Verifies that successful zone transitions are processed atomically:
 * - Access check is performed
 * - User state is updated
 * - Event is recorded in events table
 * All operations must succeed or fail together.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class TransactionalProcessingIntegrationTest {

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
    private String checkpointPublicKeyPem;
    private KeyPair issuerKeyPair;
    private String issuerPublicKeyPem;
    private String issuerCode = "test-issuer-1";
    private String checkpointCode = "cp-1";
    private String exitCheckpointCode = "cp-1-exit";
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
        jdbcTemplate.update("DELETE FROM checkpoint_keys WHERE checkpoint_code = ?", exitCheckpointCode);
        jdbcTemplate.update("DELETE FROM issuer_keys WHERE issuer_code = ?", issuerCode);
        jdbcTemplate.update("DELETE FROM checkpoints WHERE code = ?", checkpointCode);
        jdbcTemplate.update("DELETE FROM checkpoints WHERE code = ?", exitCheckpointCode);
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

        // Configure exit checkpoint for zone-a -> OUT
        jdbcTemplate.update(
            "INSERT INTO checkpoints (id, code, from_zone_id, to_zone_id) VALUES (?, ?, ?, NULL)",
            UUID.randomUUID(), exitCheckpointCode, zoneAId
        );
        
        // Create access rule: user can access zone-b
        jdbcTemplate.update(
            "INSERT INTO access_rules (user_id, to_zone_id) VALUES (?, ?)",
            userId, zoneBId
        );
        
        // Generate and insert checkpoint key
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        checkpointKeyPair = keyGen.generateKeyPair();
        checkpointPublicKeyPem = convertToPEM(checkpointKeyPair.getPublic(), "PUBLIC KEY");
        
        jdbcTemplate.update(
            "INSERT INTO checkpoint_keys (id, checkpoint_code, public_key_pem, key_type) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), checkpointCode, checkpointPublicKeyPem, "RSA"
        );
        
        // Generate and insert issuer key
        issuerKeyPair = keyGen.generateKeyPair();
        issuerPublicKeyPem = convertToPEM(issuerKeyPair.getPublic(), "PUBLIC KEY");
        
        jdbcTemplate.update(
            "INSERT INTO issuer_keys (id, issuer_code, public_key_pem, key_type, algorithm) VALUES (?, ?, ?, ?, ?)",
            UUID.randomUUID(), issuerCode, issuerPublicKeyPem, "RSA", "RS256"
        );
    }

    private String url() {
        return "http://localhost:" + port + "/ingest/event";
    }

    @Test
    void successfulEvent_shouldUpdateUserStateAndRecordEvent() throws Exception {
        // Initial state: user is in zone-a
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Prepare event: transit from zone-a to zone-b
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("accepted");
        assertThat(response.getBody().get("eventId")).isEqualTo(eventId);
        
        // Verify user state was updated atomically
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneBId);
        
        // Verify event was recorded atomically
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(1);
        
        // Verify event details
        Map<String, Object> event = jdbcTemplate.queryForMap(
            "SELECT event_id, checkpoint_id, user_id, from_zone_id, to_zone_id FROM events WHERE event_id = ?",
            eventId
        );
        assertThat(event.get("checkpoint_id")).isEqualTo(checkpointId);
        assertThat(event.get("user_id")).isEqualTo(userId);
        assertThat(event.get("from_zone_id")).isEqualTo(zoneAId);
        assertThat(event.get("to_zone_id")).isEqualTo(zoneBId);
    }

    @Test
    void successfulEvent_fromOutZone_shouldInitializeUserStateAndRecordEvent() throws Exception {
        // User has no state yet (outside system)
        // No user_state record exists
        
        // Prepare event: entry from OUT to zone-b
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().atOffset(java.time.ZoneOffset.UTC)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        String fromZone = "OUT";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        
        // Verify user state was initialized atomically
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneBId);
        
        // Verify event was recorded atomically
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(1);
        
        // Verify event has NULL from_zone_id (OUT zone)
        Map<String, Object> event = jdbcTemplate.queryForMap(
            "SELECT from_zone_id, to_zone_id FROM events WHERE event_id = ?",
            eventId
        );
        assertThat(event.get("from_zone_id")).isNull();
        assertThat(event.get("to_zone_id")).isEqualTo(zoneBId);
    }

    @Test
    void deniedAccess_shouldNotUpdateUserStateOrRecordEvent() throws Exception {
        // Initial state: user is in zone-a
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Remove access rule (no access from zone-a to zone-b)
        jdbcTemplate.update(
            "DELETE FROM access_rules WHERE user_id = ? AND to_zone_id = ?",
            userId, zoneBId
        );
        
        // Prepare event: transit from zone-a to zone-b (should be denied)
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("access_denied");
        
        // Verify user state was NOT updated
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneAId); // Still in zone-a
        
        // Verify event was NOT recorded
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(0);
    }

    @Test
    void stateMismatch_transitionFromWrongZone_shouldDenyAndRecordDenial() throws Exception {
        // Initial state: user is in zone-a
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Создаём разрешение на вход в zone-a (попытка будет с некорректной исходной зоной)
        jdbcTemplate.update(
            "INSERT INTO access_rules (user_id, to_zone_id) VALUES (?, ?)",
            userId, zoneAId
        );
        
        // Prepare event: transit from zone-b to zone-a (user claims to be in zone-b, but is actually in zone-a)
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-b";
        String toZone = "zone-a";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("state_mismatch");
        
        // Verify user state was NOT updated
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneAId); // Still in zone-a
        
        // Verify event was NOT recorded
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(0);
        
        // Verify denial was recorded (may be multiple due to transaction rollback safety)
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE event_id = ? AND reason = ?",
            Integer.class,
            eventId, "STATE_MISMATCH"
        );
        assertThat(denialCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void stateMismatch_entryFromOutWhenUserInZone_shouldDenyAndRecordDenial() throws Exception {
        // Initial state: user is in zone-a (already in system)
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Prepare event: entry from OUT to zone-b (but user is already in zone-a, not OUT)
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "OUT";
        String toZone = "zone-b";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("state_mismatch");
        
        // Verify user state was NOT updated
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneAId); // Still in zone-a
        
        // Verify event was NOT recorded
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(0);
        
        // Verify denial was recorded (may be multiple due to transaction rollback safety)
        Integer denialCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM denials WHERE event_id = ? AND reason = ?",
            Integer.class,
            eventId, "STATE_MISMATCH"
        );
        assertThat(denialCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void successfulExit_fromZoneToOut_shouldUpdateUserStateAndRecordEvent() throws Exception {
        // Initial state: user is in zone-a
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Prepare event: exit from zone-a to OUT
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "OUT";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("accepted");
        assertThat(response.getBody().get("eventId")).isEqualTo(eventId);
        
        // Verify user state was updated to OUT (NULL)
        Object currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            Object.class,
            userId
        );
        assertThat(currentZoneId).isNull(); // User is now OUT
        
        // Verify event was recorded atomically
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(1);
        
        // Verify event has NULL to_zone_id (OUT zone)
        Map<String, Object> event = jdbcTemplate.queryForMap(
            "SELECT from_zone_id, to_zone_id FROM events WHERE event_id = ?",
            eventId
        );
        assertThat(event.get("from_zone_id")).isEqualTo(zoneAId);
        assertThat(event.get("to_zone_id")).isNull();
    }

    @Test
    void deniedExit_fromZoneToOut_shouldNotUpdateUserStateOrRecordEvent() throws Exception {
        // Initial state: user is in zone-a
        jdbcTemplate.update(
            "INSERT INTO user_state (id, user_id, current_zone_id, version) VALUES (?, ?, ?, ?)",
            UUID.randomUUID(), userId, zoneAId, 0
        );
        
        // Remove configured exit to force denial
        jdbcTemplate.update(
            "DELETE FROM checkpoints WHERE from_zone_id = ? AND to_zone_id IS NULL",
            zoneAId
        );
        
        // Prepare event: exit from zone-a to OUT (should be denied)
        String eventId = UUID.randomUUID().toString();
        String timestamp = Instant.now().toString();
        String fromZone = "zone-a";
        String toZone = "OUT";
        
        String userToken = createJWTToken(issuerCode, userCode, Date.from(Instant.now().plusSeconds(3600)));
        String canonical = String.join("|", checkpointCode, timestamp, fromZone, toZone, userToken);
        byte[] canonicalBytes = canonical.getBytes(StandardCharsets.UTF_8);
        
        // Sign
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
        ResponseEntity<Map<String, Object>> response = post(json);
        
        // Verify response
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo("rejected");
        assertThat(response.getBody().get("reason")).isEqualTo("no_exit_path");
        
        // Verify user state was NOT updated
        UUID currentZoneId = jdbcTemplate.queryForObject(
            "SELECT current_zone_id FROM user_state WHERE user_id = ?",
            UUID.class,
            userId
        );
        assertThat(currentZoneId).isEqualTo(zoneAId); // Still in zone-a
        
        // Verify event was NOT recorded
        Integer eventCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM events WHERE event_id = ?",
            Integer.class,
            eventId
        );
        assertThat(eventCount).isEqualTo(0);
    }

    private ResponseEntity<Map<String, Object>> post(String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(json, headers);
        return restTemplate.exchange(
            url(),
            HttpMethod.POST,
            entity,
            new ParameterizedTypeReference<Map<String, Object>>() {}
        );
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

