package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CheckpointResponse;
import com.example.accesssystem.api.dto.CreateCheckpointRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateCheckpointRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Checkpoint CRUD operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class CheckpointAdminControllerIntegrationTest {

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

    private String baseUrl;
    private UUID zoneAId;
    private UUID zoneBId;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/admin/checkpoints";
        // Clean up before each test
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM checkpoints");
        jdbcTemplate.update("DELETE FROM zones");
        
        // Create test zones
        zoneAId = UUID.randomUUID();
        zoneBId = UUID.randomUUID();
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneAId, "zone-a");
        jdbcTemplate.update("INSERT INTO zones (id, code) VALUES (?, ?)", zoneBId, "zone-b");
    }

    @Test
    void createCheckpoint_shouldReturnCreatedCheckpoint() {
        CreateCheckpointRequest request = new CreateCheckpointRequest("cp-1", zoneAId, zoneBId);

        ResponseEntity<CheckpointResponse> response = restTemplate.postForEntity(
            baseUrl, request, CheckpointResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("cp-1");
        assertThat(response.getBody().fromZoneId()).isEqualTo(zoneAId);
        assertThat(response.getBody().toZoneId()).isEqualTo(zoneBId);
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void createCheckpoint_withNullFromZone_shouldReturnCreatedCheckpoint() {
        CreateCheckpointRequest request = new CreateCheckpointRequest("cp-1", null, zoneBId);

        ResponseEntity<CheckpointResponse> response = restTemplate.postForEntity(
            baseUrl, request, CheckpointResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().fromZoneId()).isNull();
    }

    @Test
    void getCheckpoint_shouldReturnCheckpoint() {
        CreateCheckpointRequest createRequest = new CreateCheckpointRequest("cp-1", zoneAId, zoneBId);
        ResponseEntity<CheckpointResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, CheckpointResponse.class);
        UUID checkpointId = createResponse.getBody().id();

        ResponseEntity<CheckpointResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + checkpointId, CheckpointResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("cp-1");
        assertThat(response.getBody().id()).isEqualTo(checkpointId);
    }

    @Test
    void listCheckpoints_shouldReturnPaginatedResults() {
        restTemplate.postForEntity(baseUrl, 
            new CreateCheckpointRequest("cp-1", zoneAId, zoneBId), CheckpointResponse.class);
        restTemplate.postForEntity(baseUrl, 
            new CreateCheckpointRequest("cp-2", zoneBId, zoneAId), CheckpointResponse.class);

        ResponseEntity<PageResponse> response = restTemplate.getForEntity(
            baseUrl + "?offset=0&limit=10", PageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void updateCheckpoint_shouldUpdateCheckpoint() {
        CreateCheckpointRequest createRequest = new CreateCheckpointRequest("cp-1", zoneAId, zoneBId);
        ResponseEntity<CheckpointResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, CheckpointResponse.class);
        UUID checkpointId = createResponse.getBody().id();
        UpdateCheckpointRequest updateRequest = new UpdateCheckpointRequest("cp-1-updated", zoneBId, zoneAId);

        restTemplate.put(baseUrl + "/" + checkpointId, updateRequest);
        ResponseEntity<CheckpointResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + checkpointId, CheckpointResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("cp-1-updated");
        assertThat(response.getBody().fromZoneId()).isEqualTo(zoneBId);
        assertThat(response.getBody().toZoneId()).isEqualTo(zoneAId);
    }

    @Test
    void deleteCheckpoint_shouldDeleteCheckpoint() {
        CreateCheckpointRequest createRequest = new CreateCheckpointRequest("cp-1", zoneAId, zoneBId);
        ResponseEntity<CheckpointResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, CheckpointResponse.class);
        UUID checkpointId = createResponse.getBody().id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            baseUrl + "/" + checkpointId,
            org.springframework.http.HttpMethod.DELETE,
            null,
            Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<CheckpointResponse> getResponse = restTemplate.getForEntity(
            baseUrl + "/" + checkpointId, CheckpointResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

