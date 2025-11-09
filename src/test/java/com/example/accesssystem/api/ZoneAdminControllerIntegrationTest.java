package com.example.accesssystem.api;

import com.example.accesssystem.api.dto.CreateZoneRequest;
import com.example.accesssystem.api.dto.PageResponse;
import com.example.accesssystem.api.dto.UpdateZoneRequest;
import com.example.accesssystem.api.dto.ZoneResponse;
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
 * Integration tests for Zone CRUD operations.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
class ZoneAdminControllerIntegrationTest {

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

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port + "/admin/zones";
        // Clean up before each test
        jdbcTemplate.update("DELETE FROM access_rules");
        jdbcTemplate.update("DELETE FROM checkpoints");
        jdbcTemplate.update("DELETE FROM zones");
    }

    @Test
    void createZone_shouldReturnCreatedZone() {
        CreateZoneRequest request = new CreateZoneRequest("zone-1");

        ResponseEntity<ZoneResponse> response = restTemplate.postForEntity(
            baseUrl, request, ZoneResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("zone-1");
        assertThat(response.getBody().id()).isNotNull();
        assertThat(response.getBody().createdAt()).isNotNull();
    }

    @Test
    void createZone_duplicateCode_shouldReturnBadRequest() {
        CreateZoneRequest request = new CreateZoneRequest("zone-1");
        restTemplate.postForEntity(baseUrl, request, ZoneResponse.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
            baseUrl, request, String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getZone_shouldReturnZone() {
        CreateZoneRequest createRequest = new CreateZoneRequest("zone-1");
        ResponseEntity<ZoneResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, ZoneResponse.class);
        UUID zoneId = createResponse.getBody().id();

        ResponseEntity<ZoneResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + zoneId, ZoneResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("zone-1");
        assertThat(response.getBody().id()).isEqualTo(zoneId);
    }

    @Test
    void listZones_shouldReturnPaginatedResults() {
        restTemplate.postForEntity(baseUrl, new CreateZoneRequest("zone-1"), ZoneResponse.class);
        restTemplate.postForEntity(baseUrl, new CreateZoneRequest("zone-2"), ZoneResponse.class);

        ResponseEntity<PageResponse> response = restTemplate.getForEntity(
            baseUrl + "?offset=0&limit=10", PageResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().total()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void updateZone_shouldUpdateZone() {
        CreateZoneRequest createRequest = new CreateZoneRequest("zone-1");
        ResponseEntity<ZoneResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, ZoneResponse.class);
        UUID zoneId = createResponse.getBody().id();
        UpdateZoneRequest updateRequest = new UpdateZoneRequest("zone-1-updated");

        restTemplate.put(baseUrl + "/" + zoneId, updateRequest);
        ResponseEntity<ZoneResponse> response = restTemplate.getForEntity(
            baseUrl + "/" + zoneId, ZoneResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("zone-1-updated");
    }

    @Test
    void deleteZone_shouldDeleteZone() {
        CreateZoneRequest createRequest = new CreateZoneRequest("zone-1");
        ResponseEntity<ZoneResponse> createResponse = restTemplate.postForEntity(
            baseUrl, createRequest, ZoneResponse.class);
        UUID zoneId = createResponse.getBody().id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
            baseUrl + "/" + zoneId,
            org.springframework.http.HttpMethod.DELETE,
            null,
            Void.class);

        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ZoneResponse> getResponse = restTemplate.getForEntity(
            baseUrl + "/" + zoneId, ZoneResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

